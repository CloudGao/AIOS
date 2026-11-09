package os.aios.runtime.models;

/** Fixed, tensor-compatible inputs. A different URL does not imply model compatibility. */
public final class OptionalModelSpec {
    public final String id, sha256, url, source, license; public final long bytes; public final boolean licensePending;
    private OptionalModelSpec(String id,long bytes,String hash,String url,String source,String license,boolean pending){this.id=id;this.bytes=bytes;sha256=hash;this.url=url;this.source=source;this.license=license;licensePending=pending;}
    public static OptionalModelSpec get(String id){
        switch(id){
        case "lfm":return new OptionalModelSpec(id,229312224L,"7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4",
            "https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/resolve/657e078c94084481950a2d555a941481f715536b/LFM2.5-350M-Q4_K_M.gguf",
            "https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF", "https://huggingface.co/LiquidAI/LFM2.5-350M-GGUF/blob/657e078c94084481950a2d555a941481f715536b/LICENSE",false);
        case "speaker":return new OptionalModelSpec(id,28281138L,"f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx",
            "https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models", "https://github.com/modelscope/3D-Speaker",true);
        case "face":return new OptionalModelSpec(id,3628816L,"3e82b513e65d00b56c35b572f964a594fdbd6e56717361bc0b64cbaf9e140a4d",
            "",
            "https://storage.googleapis.com/mediapipe-assets/face_detection_full_range_sparse.tflite", "https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE",true);
        default:throw new IllegalArgumentException("unknown_optional_model");
        }
    }
}
