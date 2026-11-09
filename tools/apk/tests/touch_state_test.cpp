#include "../../../android/host-apk/native/touch_state.h"
#include <cassert>
#include <iostream>
int main(){
 TouchState s;s.hasButton=true;
 assert(s.accept(EV_KEY,BTN_TOUCH,1)==0);assert(s.accept(EV_SYN,SYN_REPORT,0)==1);
 assert(s.accept(EV_ABS,ABS_MT_POSITION_X,200)==0);assert(s.accept(EV_SYN,SYN_REPORT,0)==0);
 s.accept(EV_KEY,BTN_TOUCH,0);assert(s.accept(EV_SYN,SYN_REPORT,0)==0);
 s.accept(EV_KEY,BTN_TOUCH,1);assert(s.accept(EV_SYN,SYN_REPORT,0)==1);
 assert(s.accept(EV_SYN,SYN_DROPPED,0)==-1);
 TouchState mt;mt.accept(EV_ABS,ABS_MT_TRACKING_ID,8);assert(mt.accept(EV_SYN,SYN_REPORT,0)==1);
 mt.accept(EV_ABS,ABS_MT_SLOT,1);mt.accept(EV_ABS,ABS_MT_TRACKING_ID,9);assert(mt.accept(EV_SYN,SYN_REPORT,0)==0);
 mt.accept(EV_ABS,ABS_MT_SLOT,0);mt.accept(EV_ABS,ABS_MT_TRACKING_ID,-1);assert(mt.accept(EV_SYN,SYN_REPORT,0)==0);
 mt.accept(EV_ABS,ABS_MT_SLOT,1);mt.accept(EV_ABS,ABS_MT_TRACKING_ID,-1);assert(mt.accept(EV_SYN,SYN_REPORT,0)==0);
 mt.accept(EV_ABS,ABS_MT_TRACKING_ID,10);assert(mt.accept(EV_SYN,SYN_REPORT,0)==1);
 assert(mt.accept(EV_ABS,ABS_MT_SLOT,64)==-1);
 TouchState ignored;ignored.hasButton=true;ignored.accept(EV_KEY,KEY_A,1);assert(ignored.accept(EV_SYN,SYN_REPORT,0)==0);
 std::cout<<"touch transitions, multi-touch, repeats, release, dropped events and bounds passed\n";
}
