package com.sketchware.ai.project;

import mod.hey.studios.project.ProjectSettings;

/**
 * Bluetooth on Android 12+ (API 31+): apps targeting 31 or higher need BLUETOOTH_CONNECT
 * (paired devices, connect, names, enable request) and BLUETOOTH_SCAN (discovery,
 * cancelDiscovery) declared in the manifest and granted at runtime, or those calls throw
 * SecurityException. The legacy BLUETOOTH / BLUETOOTH_ADMIN permissions then only apply up to
 * Android 11 (maxSdkVersion 30). Projects targeting 30 or lower keep the legacy behaviour.
 */
public final class BluetoothPermissions {

    public static final String CONNECT = "android.permission.BLUETOOTH_CONNECT";
    public static final String SCAN = "android.permission.BLUETOOTH_SCAN";

    private BluetoothPermissions() {
    }

    public static boolean targetsAndroid12(ProjectSettings settings) {
        try {
            return Integer.parseInt(settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, "28")) >= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean targetsAndroid12(String sc_id) {
        return targetsAndroid12(new ProjectSettings(sc_id));
    }
}
