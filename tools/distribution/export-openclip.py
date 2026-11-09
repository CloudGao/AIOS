#!/usr/bin/env python3
"""Export the pinned MIT OpenCLIP pair to FP16-weight LiteRT, with reference vectors."""
import argparse,hashlib,json,importlib.metadata
from pathlib import Path
import torch,open_clip,numpy as np,litert_torch
import torch.nn.functional as F
from ai_edge_quantizer import quantizer,recipe_manager,qtyping
from ai_edge_quantizer import algorithm_manager
p=argparse.ArgumentParser();p.add_argument('--checkpoint',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--text-only',action='store_true');a=p.parse_args()
assert hashlib.file_digest(a.checkpoint.open('rb'),'sha256').hexdigest()=='80905176486d952914c1deb3b753b8e653ab54aa4ffb14208bc5007cf7643a16'
a.output.mkdir(parents=True,exist_ok=True);torch.set_num_threads(4);torch.manual_seed(7)
model,_,preprocess=open_clip.create_model_and_transforms('ViT-B-16',pretrained=str(a.checkpoint));model.eval()
tokenizer=open_clip.get_tokenizer('ViT-B-16')
tokens={'encoder':tokenizer.encoder,'merges':[[x,y] for (x,y),rank in sorted(tokenizer.bpe_ranks.items(),key=lambda x:x[1])],'byte_encoder':{str(k):v for k,v in tokenizer.byte_encoder.items()},'context_length':77}
(a.output/'tokenizer.json').write_text(json.dumps(tokens,ensure_ascii=False))
class Tower(torch.nn.Module):
 def __init__(self,m,image):super().__init__();self.base=m;self.image=image
 def forward(self,x,eot_mask=None):
  if self.image:return F.normalize(self.base.encode_image(x),dim=-1)
  m=self.base;x0=x
  x=x.to(m.transformer.get_cast_dtype());x=x+m.positional_embedding.to(x.dtype)
  # Explicit per-head mask avoids the verified OpenGL broadcast discrepancy.
  for block in m.transformer.resblocks:
   normalized=block.ln_1(x);q,k,v=F.linear(normalized,block.attn.in_proj_weight,block.attn.in_proj_bias).chunk(3,dim=-1)
   heads=block.attn.num_heads;width=q.shape[-1]//heads
   q=q.reshape(1,77,heads,width).transpose(1,2);k=k.reshape(1,77,heads,width).transpose(1,2);v=v.reshape(1,77,heads,width).transpose(1,2)
   scores=(q@k.transpose(-2,-1))*(width**-0.5)+torch.nan_to_num(m.attn_mask,neginf=-10000.0).reshape(1,1,77,77).repeat(1,heads,1,1)
   attended=(scores.softmax(dim=-1)@v).transpose(1,2).reshape(1,77,heads*width)
   x=x+block.ls_1(F.linear(attended,block.attn.out_proj.weight,block.attn.out_proj.bias))
   x=x+block.ls_2(block.mlp(block.ln_2(x)))
  x=m.ln_final(x)
  x=(x*eot_mask.unsqueeze(-1)).sum(dim=1)@m.text_projection
  return F.normalize(x,dim=-1)
manifest={'model_id':'openclip-vit-b16-datacomp-xl','license':'MIT','checkpoint_revision':'d110532e8d4ff91c574ee60a342323f28468b287','checkpoint_sha256':hashlib.file_digest(a.checkpoint.open('rb'),'sha256').hexdigest(),'dimension':512,'metric':'l2','preprocessor':'rgb-bicubic-shortest224-centercrop-nchw-clip-normalized-v1','tokenizer_sha256':hashlib.sha256((a.output/'tokenizer.json').read_bytes()).hexdigest(),'graph_revision':'openclip-paired-fp16-v1','runtime':'LiteRT 2.3.0','compiler':importlib.metadata.version('litert-torch'),'input_size':224}
if a.text_only:manifest=json.loads((a.output/'manifest.json').read_text())
for role,is_image in ([('text',False)] if a.text_only else [('image',True),('text',False)]):
 example=torch.rand(1,3,224,224) if is_image else tokenizer(['a photo of a bicycle']).to(torch.int32)
 tower=Tower(model,is_image).eval()
 with torch.no_grad():
  reference=F.normalize(model.encode_image(example) if is_image else model.encode_text(example.long()),dim=-1)
  args=(example,) if is_image else (model.token_embedding(example.long()).to(torch.float16).to(torch.float32),(example==49407).to(torch.float32))
  assert F.cosine_similarity(reference,tower(*args)).min().item()>.9999
  path=a.output/(role+'.tflite');print('EXPORT',role,flush=True)
  edge=litert_torch.convert(tower,args)
  raw=a.output/(role+'.fp32.tflite');edge.export(str(raw))
  manager=recipe_manager.RecipeManager();manager.add_weight_only_config('.*',qtyping.TFLOperationName.ALL_SUPPORTED,16,granularity=qtyping.QuantGranularity.TENSORWISE,algorithm_key=algorithm_manager.AlgorithmName.FLOAT_CASTING)
  result=quantizer.Quantizer(str(raw),manager.get_quantization_recipe()).quantize();data=result.quantized_model
  path.write_bytes(data)
  np.save(a.output/(role+'-input.npy'),example.numpy());np.save(a.output/(role+'-reference.npy'),reference.numpy())
  example.numpy().astype('<f4' if is_image else '<i4').tofile(a.output/(role+'-input.bin'));reference.numpy().astype('<f4').tofile(a.output/(role+'-reference.bin'))
  manifest[role]={'file':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size}
  print('DONE',role,path.stat().st_size,flush=True)
table=a.output/'token-embeddings.fp16';table.write_bytes(model.token_embedding.weight.detach().numpy().astype('<f2').tobytes())
manifest['token_embeddings']={'file':table.name,'sha256':hashlib.sha256(table.read_bytes()).hexdigest(),'bytes':table.stat().st_size,'rows':49408,'columns':512}
manifest['graph_revision']='openclip-fp16-explicit-head-mask-v4' 
manifest['text']['pooling_input']='explicit-eot-mask-f32-77'
manifest.pop('space_id',None)
manifest['face']={'file':'face.tflite','sha256':'3e82b513e65d00b56c35b572f964a594fdbd6e56717361bc0b64cbaf9e140a4d','preprocessor':'letterbox192-nhwc-minus1to1-v1','decoder':'blazeface-full-range-2304-v1'}
manifest['space_id']=hashlib.sha256(json.dumps(manifest,sort_keys=True).encode()).hexdigest()
(a.output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
