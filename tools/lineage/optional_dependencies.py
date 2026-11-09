"""Explicit ROM payload selection. No network, implicit licensing grant, or device actions."""
import hashlib
import json
import re
from pathlib import Path

APPS = {'AIOSChromium', 'AIOSCollabora', 'AIOSPdfReader', 'AIOSTextEditor', 'AIOSKokoroTts'}
MODELS = {'lfm', 'speaker', 'face'}

class OptionalDependencies:
    def __init__(self, source, config=None):
        path=Path(config) if config else Path(source)/'android/host-rom/optional-dependencies.json'
        data=json.loads(path.read_text())
        if set(data)!={'schema','bundled_models','bundled_apps'} or data['schema']!=1:
            raise ValueError('Invalid optional dependency configuration')
        for key,allowed in [('bundled_models',MODELS),('bundled_apps',APPS)]:
            value=data[key]
            if not isinstance(value,list) or any(not isinstance(x,str) or x not in allowed for x in value) or len(value)!=len(set(value)):
                raise ValueError('Invalid optional dependency list: '+key)
        self.models=set(data['bundled_models']);self.apps=set(data['bundled_apps'])
        self.data=data
        self.fingerprint=hashlib.sha256(json.dumps(data,sort_keys=True,separators=(',',':')).encode()).hexdigest()

    def includes(self, module, relative):
        path=str(relative).replace('\\','/')
        if module=='speech' and path in {'assets/model.int8.onnx','assets/tokens.txt','assets/en.wav','assets/zh.wav'}:return False
        if module=='speech' and path.startswith('jni/'):return False
        if module=='text-models':return 'lfm' in self.models
        if module=='speech' and path=='assets/speaker.onnx':return 'speaker' in self.models
        if module=='search-models' and path.endswith(('.npy','.fp32.tflite','-input.bin','-reference.bin')):return False
        if module=='search-models' and path=='face.tflite':return 'face' in self.models
        if module=='tts':return 'AIOSKokoroTts' in self.apps
        if module=='prebuilts':
            if path in ('Android.bp','prebuilts.mk'):return True
            return path.split('/')[0] in self.apps
        return True

    def includes_path(self, name):
        for prefix in ('vendor/aios/','android/'):
            if name.startswith(prefix):
                parts=name[len(prefix):].split('/',1)
                return len(parts)!=2 or self.includes(*parts)
        return True

    def transform(self, module, relative, data):
        path=str(relative);text=data.decode()
        if module=='core-services' and path=='Android.bp' and 'lfm' not in self.models:
            for name in ('aios-local-text-model','aios-local-text-model-license'):
                text=re.sub(r'"'+name+r'"\s*,?\s*','',text)
        if module=='search-models' and path=='models.mk' and 'face' not in self.models:
            text=''.join(line for line in text.splitlines(keepends=True) if '/face.tflite:' not in line)
        if module=='prebuilts' and path=='prebuilts.mk':
            text='PRODUCT_PACKAGES += '+' '.join(sorted(self.apps-{'AIOSKokoroTts'}))+'\n'
        if module=='prebuilts' and path=='Android.bp':
            # These reviewed prebuilt declarations are top-level balanced Soong blocks.
            blocks=[];start=None;depth=0
            for line in text.splitlines(keepends=True):
                if start is None:
                    if not re.match(r'^\w+\s*\{',line):continue
                    start=[]
                start.append(line);depth+=line.count('{')-line.count('}')
                if depth==0:
                    block=''.join(start);match=re.search(r'\bname:\s*"([^"]+)"',block)
                    if not match:raise ValueError('Prebuilt declaration has no name')
                    if any(match[1]==app or match[1].startswith(app+'_') for app in self.apps):blocks.append(block)
                    start=None
            if depth or start is not None:raise ValueError('Unbalanced prebuilt declarations')
            text='// Selected optional applications; see optional-dependencies.json.\n'+''.join(blocks)
        return text.encode()

    def product_makefile(self, data):
        if 'AIOSKokoroTts' in self.apps:return data
        return data.replace(b'PRODUCT_PACKAGES += AIOSKokoroTts\n',b'')
