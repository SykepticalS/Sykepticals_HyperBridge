package com.sykeptical.hyperpop.processing;

import android.app.Notification;

/** SystemUI-owned return path; never waits for a broadcast on the main thread. */
interface IIslandDispatcher {
    boolean post(String tag, int id, in Notification notification, long generation);
}
