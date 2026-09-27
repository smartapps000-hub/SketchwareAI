package com.sketchware.ai.storage;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Process;

import mod.agus.jcoderz.lib.FileUtil;

/**
 * One definition of "Sketchware AI may use shared storage", valid at targetSdkVersion 34.
 * <p>
 * Sketchware keeps projects in /sdcard/.sketchware and works on them with java.io.File.
 * On Android 11 and newer, an app targeting API 30+ can only do that with "All files access"
 * (MANAGE_EXTERNAL_STORAGE). The old READ/WRITE_EXTERNAL_STORAGE permissions no longer grant
 * it, and on Android 13+ they are never granted at all to apps targeting API 33+.
 * <p>
 * Much of Sketchware (including precompiled classes that cannot be edited) still asks
 * "is WRITE_EXTERNAL_STORAGE granted?". {@link #checkPermissionOverride} lets the base Activity
 * and the Application answer that question truthfully: granted exactly when All files access
 * is on.
 */
public final class StorageAccess {

    private StorageAccess() {
    }

    /** True when the device needs All files access instead of the legacy permissions. */
    public static boolean usesAllFilesAccess() {
        return Build.VERSION.SDK_INT >= 30;
    }

    /** Whether Sketchware AI can read and write its project folder right now. */
    public static boolean isGranted(Context context) {
        if (usesAllFilesAccess()) {
            return Environment.isExternalStorageManager();
        }
        int pid = Process.myPid();
        int uid = Process.myUid();
        return context.checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, pid, uid)
                == PackageManager.PERMISSION_GRANTED
                && context.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, pid, uid)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Answer for Context#checkPermission overrides.
     *
     * @return PERMISSION_GRANTED when a legacy storage permission is asked about for this
     * process and All files access is on; null to let the platform answer normally
     */
    public static Integer checkPermissionOverride(String permission, int pid, int uid) {
        if (usesAllFilesAccess()
                && isLegacyStoragePermission(permission)
                && pid == Process.myPid()
                && uid == Process.myUid()
                && Environment.isExternalStorageManager()) {
            return PackageManager.PERMISSION_GRANTED;
        }
        return null;
    }

    public static boolean isLegacyStoragePermission(String permission) {
        return Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)
                || Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission);
    }

    /** Opens the system "All files access" switch for Sketchware AI. */
    public static void openAllFilesAccessSettings(Activity activity) {
        FileUtil.requestAllFilesAccessPermission(activity);
    }

    /**
     * Context for the angads25 FilePickerDialog (restore, import Java/assets/resources/native libs,
     * tools). The picker only opens when checkCallingOrSelfPermission(READ_EXTERNAL_STORAGE) is
     * granted, which never happens on Android 13+; there it just requested the permission and
     * nothing appeared. This wrapper answers storage permission checks with All files access
     * (the permission Sketchware AI really uses) and passes everything else to the activity.
     */
    public static Context pickerContext(Context context) {
        return new android.content.ContextWrapper(context) {
            @Override
            public int checkCallingOrSelfPermission(String permission) {
                Integer override = checkPermissionOverride(permission, Process.myPid(), Process.myUid());
                return override != null ? override : super.checkCallingOrSelfPermission(permission);
            }

            @Override
            public int checkSelfPermission(String permission) {
                Integer override = checkPermissionOverride(permission, Process.myPid(), Process.myUid());
                return override != null ? override : super.checkSelfPermission(permission);
            }

            @Override
            public int checkPermission(String permission, int pid, int uid) {
                Integer override = checkPermissionOverride(permission, pid, uid);
                return override != null ? override : super.checkPermission(permission, pid, uid);
            }
        };
    }
}
