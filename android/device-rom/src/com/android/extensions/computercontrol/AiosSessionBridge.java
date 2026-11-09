package com.android.extensions.computercontrol;

import androidx.annotation.NonNull;

/** ROM-only package bridge for presenting an already-owned platform session in MirrorView. */
public final class AiosSessionBridge {
    private AiosSessionBridge() {}

    @NonNull
    public static ComputerControlSession wrap(
            @NonNull android.companion.virtual.computercontrol.ComputerControlSession session) {
        return new ComputerControlSession(session);
    }
}
