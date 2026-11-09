#include <jni.h>
#include <linux/input.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <dirent.h>
#include <cerrno>
#include <time.h>
#include <cstring>
#include <memory>
#include <string>
#include <vector>
#include <algorithm>
#include "touch_state.h"

namespace {
constexpr int bits = sizeof(unsigned long) * 8;
template<size_t N> bool bit(const unsigned long (&a)[N], int n) {
    return n >= 0 && n / bits < int(N) && (a[n / bits] & (1UL << (n % bits)));
}
struct Device {
    int fd = -1; std::string path; ino_t inode = 0; dev_t node = 0;
    TouchState state;
    ~Device() { if (fd >= 0) close(fd); }
};
struct Monitor { std::vector<std::unique_ptr<Device>> devices; std::vector<std::string> inventory; };
std::vector<std::string> inventory() {
    std::vector<std::string> result;
    DIR* dir = opendir("/dev/input");
    if (!dir) return result;
    while (dirent* e = readdir(dir)) {
        const char* p = e->d_name;
        if (strncmp(p,"event",5) || !p[5]) continue;
        bool valid=true; for (const char* n=p+5; *n; ++n) if (*n<'0'||*n>'9') valid=false;
        if(valid) result.emplace_back(std::string("/dev/input/")+p);
    }
    closedir(dir); std::sort(result.begin(),result.end()); return result;
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_os_aios_apk_shell_PhysicalTouchMonitor_open(JNIEnv*, jclass) {
    auto m=std::make_unique<Monitor>(); m->inventory=inventory();
    if(m->inventory.empty() || m->inventory.size()>128) return 0;
    for(const auto& path:m->inventory) {
        auto d=std::make_unique<Device>(); d->path=path;
        d->fd=::open(path.c_str(),O_RDONLY|O_NONBLOCK|O_CLOEXEC|O_NOFOLLOW);
        // Inaccessible nodes cannot be proven not to be another touchscreen.
        if(d->fd<0) return 0;
        unsigned long props[(INPUT_PROP_MAX+bits)/bits]={};
        unsigned long abs[(ABS_MAX+bits)/bits]={}, keys[(KEY_MAX+bits)/bits]={};
        if(ioctl(d->fd,EVIOCGPROP(sizeof(props)),props)<0 ||
           ioctl(d->fd,EVIOCGBIT(EV_ABS,sizeof(abs)),abs)<0 ||
           ioctl(d->fd,EVIOCGBIT(EV_KEY,sizeof(keys)),keys)<0) return 0;
        bool xy=(bit(abs,ABS_MT_POSITION_X)&&bit(abs,ABS_MT_POSITION_Y)) ||
                (bit(abs,ABS_X)&&bit(abs,ABS_Y));
        if(!bit(props,INPUT_PROP_DIRECT)||!xy) continue;
        d->state.hasButton=bit(keys,BTN_TOUCH);
        if(!d->state.hasButton && !bit(abs,ABS_MT_TRACKING_ID)) return 0;
        struct stat st{};
        if(fstat(d->fd,&st)<0 || !S_ISCHR(st.st_mode)) return 0;
        d->inode=st.st_ino; d->node=st.st_rdev;
        // CLOCK_MONOTONIC makes stale/backlogged reports detectable without logging them.
        int clock=CLOCK_MONOTONIC;
        if(ioctl(d->fd,EVIOCSCLOCKID,&clock)<0) return 0;
        if(d->state.hasButton) {
            unsigned long pressed[(KEY_MAX+bits)/bits]={};
            if(ioctl(d->fd,EVIOCGKEY(sizeof(pressed)),pressed)<0) return 0;
            d->state.button=bit(pressed,BTN_TOUCH);
        } else {
            input_absinfo slots{};
            if(ioctl(d->fd,EVIOCGABS(ABS_MT_SLOT),&slots)<0 || slots.minimum!=0 || slots.maximum>=64) return 0;
            int values[65]; values[0]=ABS_MT_TRACKING_ID;
            int count=slots.maximum+1;
            if(ioctl(d->fd,EVIOCGMTSLOTS((count+1)*sizeof(int)),values)<0) return 0;
            d->state.slot=slots.value;
            if(slots.value<0||slots.value>=64) return 0;
            for(int i=0;i<count;i++) d->state.contacts[i]=values[i+1]>=0;
        }
        // Do not activate while a finger is already held down.
        if(d->state.down()) return 0;
        m->devices.push_back(std::move(d));
        if(m->devices.size()>8) return 0;
    }
    if(m->devices.empty()) return 0;
    return reinterpret_cast<jlong>(m.release());
}
extern "C" JNIEXPORT jint JNICALL
Java_os_aios_apk_shell_PhysicalTouchMonitor_poll(JNIEnv*, jclass, jlong handle) {
    auto* m=reinterpret_cast<Monitor*>(handle);
    if(!m || inventory()!=m->inventory) return -1;
    std::vector<pollfd> fds;
    for(auto& d:m->devices) {
        struct stat st{};
        if(lstat(d->path.c_str(),&st)<0||st.st_ino!=d->inode||st.st_rdev!=d->node) return -1;
        fds.push_back({d->fd,POLLIN,0});
    }
    int ready=::poll(fds.data(),fds.size(),250);
    if(ready<0) return errno==EINTR?0:-1;
    int pressed=0;
    for(size_t i=0;i<fds.size();i++) {
        if(fds[i].revents&(POLLERR|POLLHUP|POLLNVAL)) return -1;
        if(!(fds[i].revents&POLLIN)) continue;
        input_event events[64];
        ssize_t n=read(fds[i].fd,events,sizeof(events));
        if(n<0 && (errno==EAGAIN||errno==EINTR)) continue;
        if(n<=0||n%sizeof(input_event)) return -1;
        timespec now{}; if(clock_gettime(CLOCK_MONOTONIC,&now)<0) return -1;
        for(size_t j=0;j<size_t(n)/sizeof(input_event);j++) {
            auto& e=events[j];
            long long age=(now.tv_sec-e.time.tv_sec)*1000000LL+now.tv_nsec/1000-e.time.tv_usec;
            if(age>500000 || age < -100000) return -1;
            int result=m->devices[i]->state.accept(e.type,e.code,e.value);
            if(result<0) return -1;
            pressed+=result;
        }
    }
    return pressed;
}
extern "C" JNIEXPORT void JNICALL
Java_os_aios_apk_shell_PhysicalTouchMonitor_close(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Monitor*>(handle);
}
