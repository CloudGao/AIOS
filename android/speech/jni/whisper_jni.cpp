#include <jni.h>
#include <vector>
#include "engine.h"
static void fail(JNIEnv * e,const char * text){e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),text);}
extern "C" JNIEXPORT jlong JNICALL Java_os_aios_speech_WhisperRecognizer_load(JNIEnv * e,jclass,jstring path){
 const char * value=e->GetStringUTFChars(path,nullptr);if(!value)return 0;
 AiosWhisper * model=nullptr;try{model=new AiosWhisper(value);}catch(const std::exception & x){fail(e,x.what());}
 e->ReleaseStringUTFChars(path,value);return reinterpret_cast<jlong>(model);
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_os_aios_speech_WhisperRecognizer_transcribe(JNIEnv * e,jclass,jlong handle,jfloatArray input,jstring language){
 auto * model=reinterpret_cast<AiosWhisper*>(handle);if(!model){fail(e,"whisper_closed");return nullptr;}
 const jsize count=e->GetArrayLength(input);if(count<1||count>480000){fail(e,"whisper_audio_limit");return nullptr;}
 std::vector<float> samples(count);e->GetFloatArrayRegion(input,0,count,samples.data());if(e->ExceptionCheck())return nullptr;
 const char * lang=e->GetStringUTFChars(language,nullptr);if(!lang)return nullptr;
 std::string result;try{result=model->decode(samples.data(),count,lang);}catch(const std::exception & x){fail(e,x.what());}
 e->ReleaseStringUTFChars(language,lang);if(e->ExceptionCheck())return nullptr;
 jbyteArray out=e->NewByteArray(result.size());if(out)e->SetByteArrayRegion(out,0,result.size(),reinterpret_cast<const jbyte*>(result.data()));return out;
}
extern "C" JNIEXPORT void JNICALL Java_os_aios_speech_WhisperRecognizer_setCancelled(JNIEnv *,jclass,jlong handle,jboolean value){if(handle)reinterpret_cast<AiosWhisper*>(handle)->cancelled.store(value);}
extern "C" JNIEXPORT void JNICALL Java_os_aios_speech_WhisperRecognizer_release(JNIEnv *,jclass,jlong handle){delete reinterpret_cast<AiosWhisper*>(handle);}
