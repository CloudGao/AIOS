#pragma once
#include <linux/input.h>
#include <array>

// Only contact state is retained. Position, pressure and key values are discarded.
struct TouchState {
    bool hasButton = false, button = false, reported = false;
    int slot = 0;
    std::array<bool, 64> contacts{};
    bool down() const {
        if (hasButton) return button;
        for (bool active : contacts) if (active) return true;
        return false;
    }
    // -1 means uncertain: fail closed. 1 is a new physical contact.
    int accept(unsigned type, unsigned code, int value) {
        if (type == EV_SYN && code == SYN_DROPPED) return -1;
        if (type == EV_KEY && code == BTN_TOUCH) button = value != 0;
        if (type == EV_ABS && code == ABS_MT_SLOT) {
            if (value < 0 || value >= int(contacts.size())) return -1;
            slot = value;
        }
        if (type == EV_ABS && code == ABS_MT_TRACKING_ID) contacts[slot] = value >= 0;
        if (type == EV_SYN && code == SYN_REPORT) {
            bool next = down(), pressed = next && !reported;
            reported = next;
            return pressed ? 1 : 0;
        }
        return 0;
    }
};
