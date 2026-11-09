#!/usr/bin/env python3
"""Convert the pinned upstream sparse face model to the exact approved dense input.

Requires ai-edge-litert, numpy and flatbuffers in an isolated Python environment.
No downloads or device access. Output hash must match the Android profile.
"""
import argparse
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import tempfile

SOURCE_SHA="2c3728e6da56f21e21a320433396fb06d40d9088f2247c05e5635a688d45dfe1"
OUTPUT_SHA="3e82b513e65d00b56c35b572f964a594fdbd6e56717361bc0b64cbaf9e140a4d"

def convert(source,output):
    if source.is_symlink() or not source.is_file() or source.stat().st_size!=676746:
        raise ValueError("Expected the pinned upstream sparse input")
    data=source.read_bytes()
    if hashlib.sha256(data).hexdigest()!=SOURCE_SHA:raise ValueError("Upstream model hash mismatch")
    if output.exists() or output.is_symlink():raise ValueError("Output must be new")
    import flatbuffers
    import numpy as np
    from ai_edge_litert import schema_py_generated as s
    from ai_edge_litert.interpreter import Interpreter,OpResolverType
    model=s.ModelT.InitFromObj(s.Model.GetRootAsModel(data,0));graph=model.subgraphs[0]
    original=Interpreter(model_content=data,experimental_preserve_all_tensors=True,experimental_op_resolver_type=OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES)
    original.allocate_tensors();shape=original.get_input_details()[0]['shape']
    sample=np.random.default_rng(42).uniform(-1,1,size=shape).astype(np.float32)
    original.set_tensor(original.get_input_details()[0]['index'],sample);original.invoke()
    reference=[original.get_tensor(o['index']) for o in original.get_output_details()]
    constant={n for n,t in enumerate(graph.tensors) if model.buffers[t.buffer].data is not None and len(model.buffers[t.buffer].data)>0}
    keep=[];removed=[]
    for op in graph.operators:
        code=model.operatorCodes[op.opcodeIndex].builtinCode
        if code in [s.BuiltinOperator.DENSIFY,s.BuiltinOperator.DEQUANTIZE] and all(int(n) in constant for n in op.inputs):
            for n in op.outputs:
                value=original.get_tensor(int(n));buffer=s.BufferT();buffer.data=np.frombuffer(value.tobytes(),dtype=np.uint8);model.buffers.append(buffer)
                tensor=graph.tensors[int(n)];tensor.buffer=len(model.buffers)-1;tensor.sparsity=None;tensor.quantization=None;constant.add(int(n))
            removed.append(code)
        else:keep.append(op)
    graph.operators=keep
    builder=flatbuffers.Builder(0);builder.Finish(model.Pack(builder),file_identifier=b'TFL3');result=bytes(builder.Output())
    if len(result)!=3628816 or hashlib.sha256(result).hexdigest()!=OUTPUT_SHA:
        raise ValueError("Converted graph differs from approved profile; do not relax the hash. Check converter/schema versions.")
    dense=Interpreter(model_content=result,experimental_op_resolver_type=OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES);dense.allocate_tensors()
    dense.set_tensor(dense.get_input_details()[0]['index'],sample);dense.invoke()
    errors=[float(np.max(np.abs(dense.get_tensor(o['index'])-ref))) for o,ref in zip(dense.get_output_details(),reference)]
    if not errors or not all(np.isfinite(x) and x<1e-5 for x in errors):raise ValueError("Reference output mismatch")
    output.parent.mkdir(parents=True,exist_ok=True)
    fd,temp=tempfile.mkstemp(prefix='.face-',dir=output.parent)
    try:
        with os.fdopen(fd,'wb') as stream:stream.write(result);stream.flush();os.fsync(stream.fileno())
        os.link(temp,output) # Atomic no-clobber publication.
    finally:Path(temp).unlink(missing_ok=True)
    return dict(source_sha256=SOURCE_SHA,sha256=OUTPUT_SHA,bytes=len(result),constant_ops_materialized=len(removed),remaining_ops=len(keep),max_abs=errors,
                packages={x:importlib.metadata.version(x) for x in ['ai-edge-litert','numpy','flatbuffers']},device_gpu_validated=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    print(json.dumps(convert(args.source,args.output),indent=2))

if __name__=='__main__':main()
