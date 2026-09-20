package com.d4viddf.hyperbridge.processing;

import android.os.Bundle;

interface INotificationProcessingService {
    boolean processPosted(in Bundle request);
    void processRemoved(in Bundle request);
    void reconcile(in Bundle request);
    void reload();
}
