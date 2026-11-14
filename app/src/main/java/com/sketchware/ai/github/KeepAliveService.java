package com.sketchware.ai.github;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * v1.9.9 (back in v2.0.2): an empty service a Custom Tab binds to while it is open (the Custom Tabs "keep alive" extra, as in
 * androidx.browser's KeepAliveService). Bound by the visible browser, the app isn't a cached background process while
 * the user approves the GitHub sign-in, so the sign-in's checks keep their network. A browser that ignores the extra
 * changes nothing: the check runs again as soon as the app is back in front. It does nothing else.
 */
public class KeepAliveService extends Service {
    private final Binder binder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
