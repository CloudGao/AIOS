#include "engine.h"
#include <fstream>
#include <vector>
#include <iostream>
#include <chrono>
int main(int argc,char ** argv){
 if(argc<3)return 2;
 try{AiosWhisper model(argv[1]);std::ifstream in(argv[2],std::ios::binary);std::vector<float> pcm;float sample;while(in.read(reinterpret_cast<char*>(&sample),4))pcm.push_back(sample);
 for(int pass=0;pass<(argc>4?std::stoi(argv[4]):1);pass++){
 if(pass>0&&argc>5){std::ifstream next(argv[5],std::ios::binary);pcm.clear();while(next.read(reinterpret_cast<char*>(&sample),4))pcm.push_back(sample);}
 auto start=std::chrono::steady_clock::now();auto text=model.decode(pcm.data(),pcm.size(),pass>0&&argc>6?argv[6]:(argc>3?argv[3]:"auto"));
 auto ms=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-start).count();std::cout<<text<<"\n";std::cerr<<"AIOS_DECODE_MS="<<ms<<"\n";}return 0;
 }catch(const std::exception & x){std::cerr<<x.what()<<"\n";return 1;}
}
