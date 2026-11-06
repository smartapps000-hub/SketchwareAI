package h;

import java.lang.reflect.Method;

/**
 * v1.9.4: SketchPermission.permissions(group, sdk, target) from fixture 932's built APK on ART: the Android permissions
 * each group asks for on Android 5 to 17 for targets 30 / 33 / 35. Prints one line per case and PERMCALL n/n ok.
 */
public class CallPerm {
    static int checks, fails;

    static void expect(Method m, String group, int sdk, int target, String want) throws Exception {
        String[] got = (String[]) m.invoke(null, group, sdk, target);
        StringBuilder sb = new StringBuilder();
        for (String p : got) sb.append(sb.length() == 0 ? "" : ",").append(p.replace("android.permission.", ""));
        checks++;
        boolean ok = sb.toString().equals(want);
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + group + " api " + sdk + " target " + target + ": [" + sb + "]"
                + (ok ? "" : " want [" + want + "]"));
    }

    public static void main(String[] a) throws Exception {
        Method m = Class.forName(a.length > 0 ? a[0] : "com.fixture.perm.SketchPermission")
                .getMethod("permissions", String.class, int.class, int.class);
        expect(m, "STORAGE", 37, 35, "READ_MEDIA_IMAGES,READ_MEDIA_VIDEO,READ_MEDIA_AUDIO,READ_MEDIA_VISUAL_USER_SELECTED");
        expect(m, "STORAGE", 33, 35, "READ_MEDIA_IMAGES,READ_MEDIA_VIDEO,READ_MEDIA_AUDIO");
        expect(m, "STORAGE", 34, 33, "READ_MEDIA_IMAGES,READ_MEDIA_VIDEO,READ_MEDIA_AUDIO");
        expect(m, "STORAGE", 35, 30, "READ_EXTERNAL_STORAGE");
        expect(m, "STORAGE", 30, 35, "READ_EXTERNAL_STORAGE");
        expect(m, "STORAGE", 29, 35, "READ_EXTERNAL_STORAGE,WRITE_EXTERNAL_STORAGE");
        expect(m, "STORAGE", 31, 29, "READ_EXTERNAL_STORAGE,WRITE_EXTERNAL_STORAGE");
        expect(m, "STORAGE", 23, 35, "READ_EXTERNAL_STORAGE,WRITE_EXTERNAL_STORAGE");
        expect(m, "BLUETOOTH", 31, 35, "BLUETOOTH_CONNECT,BLUETOOTH_SCAN");
        expect(m, "BLUETOOTH", 30, 35, "ACCESS_FINE_LOCATION");
        expect(m, "BLUETOOTH", 33, 30, "ACCESS_FINE_LOCATION");
        expect(m, "NOTIFICATIONS", 33, 35, "POST_NOTIFICATIONS");
        expect(m, "NOTIFICATIONS", 32, 35, "");
        expect(m, "NOTIFICATIONS", 35, 32, "");
        expect(m, "CAMERA", 23, 35, "CAMERA");
        expect(m, "MICROPHONE", 35, 35, "RECORD_AUDIO");
        expect(m, "LOCATION", 35, 35, "ACCESS_FINE_LOCATION,ACCESS_COARSE_LOCATION");
        expect(m, "PHONE", 35, 35, "CALL_PHONE,READ_PHONE_STATE");
        expect(m, "SMS", 35, 35, "SEND_SMS,RECEIVE_SMS,READ_SMS");
        expect(m, "android.permission.READ_CONTACTS", 35, 35, "READ_CONTACTS");
        expect(m, "", 35, 35, "");
        expect(m, "not a group", 35, 35, "");
        // v1.9.5: Android permission names picked in the searchable list stand for their group
        expect(m, "android.permission.READ_EXTERNAL_STORAGE", 34, 35, "READ_MEDIA_IMAGES,READ_MEDIA_VIDEO,READ_MEDIA_AUDIO,READ_MEDIA_VISUAL_USER_SELECTED");
        expect(m, "android.permission.READ_EXTERNAL_STORAGE", 32, 35, "READ_EXTERNAL_STORAGE");
        expect(m, "android.permission.BLUETOOTH", 31, 35, "BLUETOOTH_CONNECT,BLUETOOTH_SCAN");
        expect(m, "android.permission.BLUETOOTH_CONNECT", 30, 35, "ACCESS_FINE_LOCATION");
        expect(m, "android.permission.POST_NOTIFICATIONS", 32, 35, "");
        expect(m, "android.permission.RECORD_AUDIO", 35, 35, "RECORD_AUDIO");
        expect(m, "android.permission.SEND_SMS", 35, 35, "SEND_SMS");
        expect(m, "android.permission.READ_MEDIA_IMAGES", 32, 35, "READ_EXTERNAL_STORAGE");
        expect(m, "android.permission.READ_MEDIA_IMAGES", 35, 35, "READ_MEDIA_IMAGES");
        expect(m, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED", 33, 35, "READ_MEDIA_IMAGES,READ_MEDIA_VIDEO");
        Method group = m.getDeclaringClass().getMethod("group", String.class);
        // only names whose meaning moved between versions are groups; SMS / phone / coarse location names stay names
        String[][] groups = {{"android.permission.CAMERA", "CAMERA"}, {"android.permission.ACCESS_FINE_LOCATION", "LOCATION"},
                {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"},
                {"android.permission.SEND_SMS", "android.permission.SEND_SMS"}, {"RECORD_AUDIO", "RECORD_AUDIO"},
                {"android.permission.READ_CONTACTS", "android.permission.READ_CONTACTS"}, {"SMS", "SMS"}};
        for (String[] g : groups) {
            Object got = group.invoke(null, g[0]);
            checks++;
            boolean ok = g[1].equals(got);
            if (!ok) fails++;
            System.out.println((ok ? "ok   " : "FAIL ") + "group(" + g[0] + ") = " + got);
        }
        System.out.println("PERMCALL " + (checks - fails) + "/" + checks + " ok");
    }
}
