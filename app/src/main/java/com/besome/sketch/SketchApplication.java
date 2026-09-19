package com.besome.sketch;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import com.besome.sketch.tools.CollectErrorActivity;
import com.sketchware.ai.storage.StorageAccess;

import com.google.android.gms.analytics.Tracker;

public class SketchApplication extends Application {

    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    public synchronized Tracker a() {
        return new Tracker();
    }

    @Override
    public void onCreate() {
        mApplicationContext = getApplicationContext();
        // Sketchware opens project files in other apps by file:// URI. Replacing the VM policy
        // (instead of calling the hidden disableDeathOnFileUriExposure(), which is blocked for
        // apps targeting new API levels) keeps that from crashing with FileUriExposedException.
        android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());
        com.sketchware.ai.ui.ThemeMode.install(this);
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("SketchApplication", "Uncaught exception on thread " + thread.getName(), throwable);

            Intent intent = new Intent(getApplicationContext(), CollectErrorActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("error", Log.getStackTraceString(throwable));
            ((AlarmManager) getSystemService(Context.ALARM_SERVICE))
                    .set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            1000,
                            PendingIntent.getActivity(getApplicationContext(), 11111, intent,
                                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE));
            Process.killProcess(Process.myPid());
            System.exit(1);
            if (uncaughtExceptionHandler != null) {
                uncaughtExceptionHandler.uncaughtException(thread, throwable);
            }
        });
        super.onCreate();
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) {
        Integer override = StorageAccess.checkPermissionOverride(permission, pid, uid);
        return override != null ? override : super.checkPermission(permission, pid, uid);
    }

    @Override
    public int checkSelfPermission(String permission) {
        Integer override = StorageAccess.checkPermissionOverride(permission, Process.myPid(), Process.myUid());
        return override != null ? override : super.checkSelfPermission(permission);
    }
}
