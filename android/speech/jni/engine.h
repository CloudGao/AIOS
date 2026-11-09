#pragma once
#include "whisper.h"
#include <atomic>
#include <stdexcept>
#include <string>
#include <cmath>
struct AiosWhisper {
 whisper_context * context;
 std::atomic<bool> cancelled{false};
 explicit AiosWhisper(const char * path) {
  auto options=whisper_context_default_params();options.use_gpu=false;
  context=whisper_init_from_file_with_params(path,options);
  if(!context)throw std::runtime_error("whisper_model_load_failed");
 }
 ~AiosWhisper(){whisper_free(context);}
 std::string decode(const float * samples,int count,const char * language){
  if(count<1||count>16000*30)throw std::runtime_error("whisper_audio_limit");
  double energy=0;for(int i=0;i<count;i++){if(!std::isfinite(samples[i]))throw std::runtime_error("whisper_audio_nonfinite");energy+=samples[i]*samples[i];}
  if(energy/count<1e-6)return {}; // Silence never becomes a hallucinated transcript.
  auto p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  p.n_threads=4;p.translate=false;p.language=language;p.detect_language=false;
  p.no_context=true;p.no_timestamps=true;p.print_realtime=false;p.print_progress=false;p.print_timestamps=false;p.print_special=false;
  p.temperature=0;p.temperature_inc=0;p.greedy.best_of=1;
  p.abort_callback=[](void * data){return static_cast<AiosWhisper*>(data)->cancelled.load();};p.abort_callback_user_data=this;
  if(cancelled.load())throw std::runtime_error("whisper_cancelled");
  if(whisper_full(context,p,samples,count)!=0)throw std::runtime_error(cancelled.load()?"whisper_cancelled":"whisper_decode_failed");
  std::string text;
  for(int n=0;n<whisper_full_n_segments(context);n++)text+=whisper_full_get_segment_text(context,n);
  return text;
 }
};
