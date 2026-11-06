package com.sketchware.ai.project;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import a.a.a.Nx;
import a.a.a.eC;
import a.a.a.jq;
import mod.hey.studios.project.ProjectSettings;

/**
 * v1.9.4: runtime permissions as blocks. The blocks "request permission [group]", "permission [group] granted",
 * "permission status [group]" and "request permission name [text]" call the generated helper {@code SketchPermission}
 * ({@link #helperClass}); the activity events "When permission granted" / "When permission denied" are called from the
 * activity's {@code onRequestPermissionsResult} ({@link #resultCode}). Which Android permissions a group stands for
 * depends on the device's API level and the app's target SDK, decided at run time by the helper; the manifest gets
 * every permission the group can need on some Android version, each limited with {@code maxSdkVersion} where newer
 * versions don't use it ({@link #manifestEntries}). The permissions a project gets this way are listed in the build
 * log; nothing is requested unless a block asks for it.
 */
public final class PermissionCode {

    public static final String CLASS_NAME = "SketchPermission";
    public static final String FILE_NAME = CLASS_NAME + ".java";

    /** The groups of the block's drop-down, in its order. */
    public static final String[] GROUPS = {"STORAGE", "CAMERA", "MICROPHONE", "LOCATION", "BLUETOOTH", "NOTIFICATIONS",
            "PHONE", "SMS"};

    /** v1.9.5: the drawer's Permission category (custom palettes count from 9, so this id is never theirs). */
    public static final int PALETTE_ID = 100;
    public static final int PALETTE_COLOR = 0xff009688;

    /**
     * v1.9.5: Android permission names that stand for a group: picking one in the searchable list (or typing it into
     * "request permission name") asks for what that group means on the phone's Android version (READ_EXTERNAL_STORAGE
     * is the media permissions on Android 13+, BLUETOOTH is CONNECT + SCAN on 12+ ...).
     */
    private static final String[][] ALIASES = {
            // only names whose meaning moved between Android versions, or that Android asks for together: a name such
            // as SEND_SMS, READ_PHONE_STATE or ACCESS_COARSE_LOCATION stays that one permission (review: widening it
            // to the group would declare and ask for more than the user picked)
            {"STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"},
            {"CAMERA", "CAMERA"},
            {"MICROPHONE", "RECORD_AUDIO"},
            {"LOCATION", "ACCESS_FINE_LOCATION"},   // Android 12+ ignores FINE asked without COARSE
            {"BLUETOOTH", "BLUETOOTH", "BLUETOOTH_ADMIN", "BLUETOOTH_CONNECT", "BLUETOOTH_SCAN"},
            {"NOTIFICATIONS", "POST_NOTIFICATIONS"}};

    /** The group a block value stands for: a group itself, or an Android permission name of one; else null. */
    public static String groupOf(String value) {
        if (value == null) return null;
        if (isGroup(value)) return value;
        if (!value.startsWith(P)) return null;
        String name = value.substring(P.length());
        for (String[] alias : ALIASES) {
            for (int i = 1; i < alias.length; i++) if (alias[i].equals(name)) return alias[0];
        }
        return null;
    }

    public static final String OP_REQUEST = "requestAutoPermission";
    public static final String OP_REQUEST_NAME = "requestCustomPermission";
    public static final String OP_HAS = "hasAutoPermission";
    public static final String OP_STATUS = "getPermissionStatus";
    public static final String EVENT_GRANTED = "onPermissionGranted";
    public static final String EVENT_DENIED = "onPermissionDenied";

    /** A permission name typed into "request permission name": android.permission.READ_CONTACTS, com.example.X ... */
    private static final Pattern PERMISSION_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");

    private static final String P = "android.permission.";

    private PermissionCode() {
    }

    /** One line about a group for the searchable list. */
    public static String describe(String group) {
        switch (group) {
            case "STORAGE":
                return "photos, videos, music (Android 13+), files before";
            case "CAMERA":
                return "camera";
            case "MICROPHONE":
                return "record audio";
            case "LOCATION":
                return "precise or approximate location";
            case "BLUETOOTH":
                return "connect / scan (Android 12+), location before";
            case "NOTIFICATIONS":
                return "show notifications (Android 13+)";
            case "PHONE":
                return "calls, phone state";
            case "SMS":
                return "send / receive / read SMS";
            default:
                return "";
        }
    }

    public static boolean isGroup(String group) {
        return Arrays.asList(GROUPS).contains(group);
    }

    public static boolean isPermissionName(String name) {
        return name != null && PERMISSION_NAME.matcher(name).matches();
    }

    /** Whether the activity's blocks or events use the permission system (its result code is generated then). */
    public static boolean usedBy(eC data, String javaName) {
        for (EventBean event : events(data, javaName)) {
            if (event.eventType == EventBean.EVENT_TYPE_ACTIVITY
                    && (EVENT_GRANTED.equals(event.eventName) || EVENT_DENIED.equals(event.eventName))) {
                return true;
            }
        }
        HashMap<String, ArrayList<BlockBean>> blocks = data.b(javaName);
        if (blocks == null) return false;
        for (ArrayList<BlockBean> list : blocks.values()) {
            if (list == null) continue;
            for (BlockBean block : list) {
                if (isOpCode(block.opCode)) return true;
            }
        }
        return false;
    }

    public static boolean isOpCode(String opCode) {
        return OP_REQUEST.equals(opCode) || OP_REQUEST_NAME.equals(opCode) || OP_HAS.equals(opCode) || OP_STATUS.equals(opCode);
    }

    private static boolean hasEvent(eC data, String javaName, String name) {
        for (EventBean event : events(data, javaName)) {
            if (event.eventType == EventBean.EVENT_TYPE_ACTIVITY && name.equals(event.eventName)) return true;
        }
        return false;
    }

    private static List<EventBean> events(eC data, String javaName) {
        ArrayList<EventBean> events = data.g(javaName);
        return events == null ? new ArrayList<EventBean>() : events;
    }

    /**
     * The statements {@code onRequestPermissionsResult} gets for the permission blocks: the helper takes its answer
     * (and asks for the next queued group), then the event the activity has is called with the group.
     */
    public static String resultCode(eC data, String javaName, String eol) {
        boolean granted = hasEvent(data, javaName, EVENT_GRANTED);
        boolean denied = hasEvent(data, javaName, EVENT_DENIED);
        StringBuilder sb = new StringBuilder();
        if (!granted && !denied) {
            return sb.append(CLASS_NAME).append(".onResult(this, requestCode, permissions);").append(eol).toString();
        }
        sb.append("String _permissionGroup = ").append(CLASS_NAME).append(".onResult(this, requestCode, permissions);").append(eol);
        if (granted && denied) {
            sb.append("if (_permissionGroup != null) {").append(eol);
            sb.append("if (").append(CLASS_NAME).append(".has(this, _permissionGroup)) {").append(eol);
            sb.append("_onPermissionGranted(_permissionGroup);").append(eol);
            sb.append("} else {").append(eol);
            sb.append("_onPermissionDenied(_permissionGroup, ").append(CLASS_NAME).append(".neverAskAgain(this, _permissionGroup));").append(eol);
            sb.append("}").append(eol);
            sb.append("}").append(eol);
        } else if (granted) {
            sb.append("if (_permissionGroup != null && ").append(CLASS_NAME).append(".has(this, _permissionGroup)) {").append(eol);
            sb.append("_onPermissionGranted(_permissionGroup);").append(eol);
            sb.append("}").append(eol);
        } else {
            sb.append("if (_permissionGroup != null && !").append(CLASS_NAME).append(".has(this, _permissionGroup)) {").append(eol);
            sb.append("_onPermissionDenied(_permissionGroup, ").append(CLASS_NAME).append(".neverAskAgain(this, _permissionGroup));").append(eol);
            sb.append("}").append(eol);
        }
        return sb.toString();
    }

    /** The activity events' code (EventsHandler): a method the result code above calls. */
    public static String eventCode(String eventName, String logic) {
        if (EVENT_GRANTED.equals(eventName)) {
            return "public void _onPermissionGranted(final String _permissionGroup) {\r\n" + logic + "\r\n}";
        }
        return "public void _onPermissionDenied(final String _permissionGroup, final boolean _neverAskAgain) {\r\n" + logic + "\r\n}";
    }

    // ---------------------------------------------------------------------------------------------- manifest

    /**
     * {permission → {maxSdkVersion or null, usesPermissionFlags or null}} for the groups and permission names a project
     * uses. Merged so that a permission two groups need keeps the wider range (no maxSdkVersion wins).
     */
    public static LinkedHashMap<String, String[]> manifestEntries(Set<String> groups, Set<String> names, int target) {
        return manifestEntries(groups, names, target, false);
    }

    /**
     * @param downloadsBelow23 the web block "allow downloads" in a project whose min SDK is below 23: Android 4.4 - 5.1's
     *                         download manager needs WRITE_EXTERNAL_STORAGE even for the app's own folder
     */
    public static LinkedHashMap<String, String[]> manifestEntries(Set<String> groups, Set<String> names, int target,
                                                                  boolean downloadsBelow23) {
        LinkedHashMap<String, String[]> out = new LinkedHashMap<>();
        for (String group : groups) {
            switch (group) {
                case "STORAGE":
                    put(out, P + "READ_EXTERNAL_STORAGE", target >= 33 ? "32" : null, null);
                    put(out, P + "WRITE_EXTERNAL_STORAGE", target >= 30 ? "29" : null, null);
                    if (target >= 33) {
                        put(out, P + "READ_MEDIA_IMAGES", null, null);
                        put(out, P + "READ_MEDIA_VIDEO", null, null);
                        put(out, P + "READ_MEDIA_AUDIO", null, null);
                    }
                    if (target >= 34) put(out, P + "READ_MEDIA_VISUAL_USER_SELECTED", null, null);
                    break;
                case "CAMERA":
                    put(out, P + "CAMERA", null, null);
                    break;
                case "MICROPHONE":
                    put(out, P + "RECORD_AUDIO", null, null);
                    break;
                case "LOCATION":
                    put(out, P + "ACCESS_FINE_LOCATION", null, null);
                    put(out, P + "ACCESS_COARSE_LOCATION", null, null);
                    break;
                case "BLUETOOTH":
                    if (target >= 31) {
                        put(out, P + "BLUETOOTH", "30", null);
                        put(out, P + "BLUETOOTH_ADMIN", "30", null);
                        put(out, P + "ACCESS_FINE_LOCATION", "30", null);
                        put(out, P + "BLUETOOTH_CONNECT", null, null);
                        put(out, P + "BLUETOOTH_SCAN", null, "neverForLocation");
                    } else {
                        put(out, P + "BLUETOOTH", null, null);
                        put(out, P + "BLUETOOTH_ADMIN", null, null);
                        put(out, P + "ACCESS_FINE_LOCATION", null, null);
                    }
                    break;
                case "NOTIFICATIONS":
                    put(out, P + "POST_NOTIFICATIONS", null, null);
                    break;
                case "PHONE":
                    put(out, P + "CALL_PHONE", null, null);
                    put(out, P + "READ_PHONE_STATE", null, null);
                    break;
                case "SMS":
                    put(out, P + "SEND_SMS", null, null);
                    put(out, P + "RECEIVE_SMS", null, null);
                    put(out, P + "READ_SMS", null, null);
                    break;
                default:
            }
        }
        for (String name : names) {
            put(out, name, null, null);
            // v1.9.5: a media permission picked by name means storage below Android 13 (SketchPermission.permissions)
            if (name.startsWith(P + "READ_MEDIA_")) {
                put(out, P + "READ_EXTERNAL_STORAGE", target >= 33 ? "32" : null, null);
                if (name.equals(P + "READ_MEDIA_VISUAL_USER_SELECTED") && target >= 33) {
                    put(out, P + "READ_MEDIA_IMAGES", null, null);
                    put(out, P + "READ_MEDIA_VIDEO", null, null);
                }
            }
        }
        if (downloadsBelow23) put(out, P + "WRITE_EXTERNAL_STORAGE", "22", null);
        return out;
    }

    private static void put(Map<String, String[]> out, String name, String maxSdk, String flags) {
        String[] old = out.get(name);
        if (old == null) {
            out.put(name, new String[]{maxSdk, flags});
            return;
        }
        String max = old[0] == null || maxSdk == null ? null
                : String.valueOf(Math.max(Integer.parseInt(old[0]), Integer.parseInt(maxSdk)));
        // a flag (neverForLocation) only while every use of the permission has it
        String flag = old[1] != null && old[1].equals(flags) ? flags : null;
        out.put(name, new String[]{max, flag});
    }

    /**
     * Writes the uses-permission tags the blocks need into the manifest, after the ones written already (those keep
     * their own attributes: a second tag for the same name is a duplicate Gradle's manifest merger refuses).
     *
     * @return whether the STORAGE group is used (Ix adds requestLegacyExternalStorage for it as for the File blocks)
     */
    public static boolean writeManifest(Nx manifest, jq features, ProjectSettings settings, Set<String> written) {
        Set<String> groups = features.permissionGroups;
        Set<String> names = features.permissionNames;
        boolean downloads = features.webDownloadsUsed && minSdk(settings) < 23;
        if (groups.isEmpty() && names.isEmpty() && !downloads) return false;
        int target = target(settings);
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : manifestEntries(groups, names, target, downloads).entrySet()) {
            String name = entry.getKey();
            if (written.contains(name)) continue;
            Nx tag = new Nx("uses-permission");
            tag.a("android", "name", name);
            if (entry.getValue()[0] != null) tag.a("android", "maxSdkVersion", entry.getValue()[0]);
            if (entry.getValue()[1] != null) tag.a("android", "usesPermissionFlags", entry.getValue()[1]);
            manifest.a(tag);
            written.add(name);
            added.add(name.startsWith(P) ? name.substring(P.length()) : name);
        }
        // listed in the build log's notes (BuildWarnings.begin runs after the files are generated)
        List<String> by = new ArrayList<>(groups);
        by.addAll(names);
        if (downloads) by.add("web downloads below Android 6");
        features.permissionNote = added.isEmpty() ? null : "Permission / web blocks (" + String.join(", ", by)
                + ") declare in the manifest: " + String.join(", ", added) + ".";
        return groups.contains("STORAGE");
    }

    public static int minSdk(ProjectSettings settings) {
        try {
            return Integer.parseInt(settings.getValue(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, "21"));
        } catch (NumberFormatException e) {
            return 21;
        }
    }

    public static int target(ProjectSettings settings) {
        try {
            return Integer.parseInt(settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, "28"));
        } catch (NumberFormatException e) {
            return 28;
        }
    }

    // ---------------------------------------------------------------------------------------------- helper class

    /** Whether a generated or custom Java file calls the helper. */
    public static boolean uses(String code) {
        return code != null && code.contains(CLASS_NAME + ".");
    }

    /**
     * Source of {@code <package>.SketchPermission}. Java 7, min SDK 19, ASCII. With AppCompat a block in a Fragment of
     * the project asks through the fragment's activity.
     */
    public static String helperClass(String packageName, boolean appCompat) {
        return "package " + packageName + ";\n"
                + "\n"
                + "import android.app.Activity;\n"
                + "import android.content.Context;\n"
                + "import android.content.pm.PackageManager;\n"
                + "import android.os.Build;\n"
                + "import android.os.Handler;\n"
                + "import android.os.Looper;\n"
                + "\n"
                + "import java.lang.ref.WeakReference;\n"
                + "import java.util.ArrayList;\n"
                + "\n"
                + "/**\n"
                + " * Runtime permissions for the permission blocks (Sketchware AI). A group stands for the Android\n"
                + " * permissions it needs on the device's Android version for this app's target SDK. Results reach the\n"
                + " * activity's onRequestPermissionsResult, which calls the activity's permission events.\n"
                + " */\n"
                + "public class SketchPermission {\n"
                + "\n"
                + "    public static final String GRANTED = \"GRANTED\";\n"
                + "    public static final String DENIED = \"DENIED\";\n"
                + "    public static final String NEVER_ASK_AGAIN = \"NEVER_ASK_AGAIN\";\n"
                + "\n"
                + "    private static final String[] GROUPS = {\"STORAGE\", \"CAMERA\", \"MICROPHONE\", \"LOCATION\", \"BLUETOOTH\",\n"
                + "            \"NOTIFICATIONS\", \"PHONE\", \"SMS\"};\n"
                + "    private static final int FIRST_CODE = 7401;\n"
                + "    private static final int NAME_CODE = 7500;\n"
                + "    private static final String PREFS = \"sketch_permissions\";\n"
                + "    private static final String P = \"android.permission.\";\n"
                + "    /** {group, the Android permissions that stand for it ...} */\n"
                + "    private static final String[][] ALIASES = {\n"
                + "            {\"STORAGE\", \"READ_EXTERNAL_STORAGE\", \"WRITE_EXTERNAL_STORAGE\"},\n"
                + "            {\"CAMERA\", \"CAMERA\"},\n"
                + "            {\"MICROPHONE\", \"RECORD_AUDIO\"},\n"
                + "            {\"LOCATION\", \"ACCESS_FINE_LOCATION\"},\n"
                + "            {\"BLUETOOTH\", \"BLUETOOTH\", \"BLUETOOTH_ADMIN\", \"BLUETOOTH_CONNECT\", \"BLUETOOTH_SCAN\"},\n"
                + "            {\"NOTIFICATIONS\", \"POST_NOTIFICATIONS\"}};\n"
                + "    /** A request still waiting for its answer; later requests wait in the queue (one dialog at a time). */\n"
                + "    private static WeakReference<Activity> asking;\n"
                + "    private static String askingGroup;\n"
                + "    private static long askedAt;\n"
                + "    /** The group of the last answer that came with no permissions (an interrupted dialog). */\n"
                + "    private static String emptyAnswer;\n"
                + "    private static final ArrayList<Object[]> queue = new ArrayList<Object[]>();\n"
                + "\n"
                + "    private SketchPermission() {\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * The group a value stands for: a group, or an Android permission of one (android.permission.CAMERA is\n"
                + "     * CAMERA, READ_EXTERNAL_STORAGE is STORAGE ...); any other permission name stays itself.\n"
                + "     */\n"
                + "    public static String group(String value) {\n"
                + "        if (value == null) return null;\n"
                + "        String name = value.startsWith(P) ? value.substring(P.length()) : value;\n"
                + "        for (int i = 0; i < ALIASES.length; i++) {\n"
                + "            for (int j = 0; j < ALIASES[i].length; j++) {\n"
                + "                if (ALIASES[i][j].equals(name) && (j > 0 ? value.startsWith(P) : !value.startsWith(P))) return ALIASES[i][0];\n"
                + "            }\n"
                + "        }\n"
                + "        return value;\n"
                + "    }\n"
                + "\n"
                + "    /** The permissions a group needs on a device with API level sdk, for an app with target SDK target. */\n"
                + "    public static String[] permissions(String group, int sdk, int target) {\n"
                + "        group = group(group);\n"
                + "        if (\"STORAGE\".equals(group)) {\n"
                + "            if (sdk >= 34 && target >= 34) {\n"
                + "                return new String[]{P + \"READ_MEDIA_IMAGES\", P + \"READ_MEDIA_VIDEO\", P + \"READ_MEDIA_AUDIO\",\n"
                + "                        P + \"READ_MEDIA_VISUAL_USER_SELECTED\"};\n"
                + "            }\n"
                + "            if (sdk >= 33 && target >= 33) {\n"
                + "                return new String[]{P + \"READ_MEDIA_IMAGES\", P + \"READ_MEDIA_VIDEO\", P + \"READ_MEDIA_AUDIO\"};\n"
                + "            }\n"
                + "            if (sdk <= 29 || target < 30) {\n"
                + "                return new String[]{P + \"READ_EXTERNAL_STORAGE\", P + \"WRITE_EXTERNAL_STORAGE\"};\n"
                + "            }\n"
                + "            return new String[]{P + \"READ_EXTERNAL_STORAGE\"};\n"
                + "        }\n"
                + "        if (\"CAMERA\".equals(group)) return new String[]{P + \"CAMERA\"};\n"
                + "        if (\"MICROPHONE\".equals(group)) return new String[]{P + \"RECORD_AUDIO\"};\n"
                + "        if (\"LOCATION\".equals(group)) return new String[]{P + \"ACCESS_FINE_LOCATION\", P + \"ACCESS_COARSE_LOCATION\"};\n"
                + "        if (\"BLUETOOTH\".equals(group)) {\n"
                + "            if (sdk >= 31 && target >= 31) return new String[]{P + \"BLUETOOTH_CONNECT\", P + \"BLUETOOTH_SCAN\"};\n"
                + "            return new String[]{P + \"ACCESS_FINE_LOCATION\"};\n"
                + "        }\n"
                + "        if (\"NOTIFICATIONS\".equals(group)) {\n"
                + "            if (sdk >= 33 && target >= 33) return new String[]{P + \"POST_NOTIFICATIONS\"};\n"
                + "            return new String[0];\n"
                + "        }\n"
                + "        if (\"PHONE\".equals(group)) return new String[]{P + \"CALL_PHONE\", P + \"READ_PHONE_STATE\"};\n"
                + "        if (\"SMS\".equals(group)) return new String[]{P + \"SEND_SMS\", P + \"RECEIVE_SMS\", P + \"READ_SMS\"};\n"
                + "        if (group != null && group.startsWith(P + \"READ_MEDIA_\")) {\n"
                + "            // a media permission means storage below Android 13; partial access exists from 14\n"
                + "            if (sdk < 33 || target < 33) return new String[]{P + \"READ_EXTERNAL_STORAGE\"};\n"
                + "            if (group.equals(P + \"READ_MEDIA_VISUAL_USER_SELECTED\") && (sdk < 34 || target < 34)) {\n"
                + "                return new String[]{P + \"READ_MEDIA_IMAGES\", P + \"READ_MEDIA_VIDEO\"};\n"
                + "            }\n"
                + "        }\n"
                + "        if (isName(group)) return new String[]{group};\n"
                + "        return new String[0];\n"
                + "    }\n"
                + "\n"
                + "    /** Whether the group (or permission name) is granted now. */\n"
                + "    public static boolean has(Object host, String group) {\n"
                + "        group = group(group);\n"
                + "        Context context = context(host);\n"
                + "        if (context == null || !known(group)) return false;\n"
                + "        int sdk = Build.VERSION.SDK_INT;\n"
                + "        int target = target(context);\n"
                + "        if (\"LOCATION\".equals(group)) {\n"
                + "            // approximate location is enough: users may grant only that (Android 12+)\n"
                + "            return granted(context, P + \"ACCESS_FINE_LOCATION\") || granted(context, P + \"ACCESS_COARSE_LOCATION\");\n"
                + "        }\n"
                + "        if (\"STORAGE\".equals(group) && sdk >= 34 && target >= 34) {\n"
                + "            // \"Select photos and videos\" grants the selected ones only (Android 14+)\n"
                + "            boolean pictures = granted(context, P + \"READ_MEDIA_IMAGES\") && granted(context, P + \"READ_MEDIA_VIDEO\")\n"
                + "                    || granted(context, P + \"READ_MEDIA_VISUAL_USER_SELECTED\");\n"
                + "            return pictures && granted(context, P + \"READ_MEDIA_AUDIO\");\n"
                + "        }\n"
                + "        String[] all = permissions(group, sdk, target);\n"
                + "        for (int i = 0; i < all.length; i++) {\n"
                + "            if (!granted(context, all[i])) return false;\n"
                + "        }\n"
                + "        return true;\n"
                + "    }\n"
                + "\n"
                + "    /** GRANTED, DENIED or NEVER_ASK_AGAIN (denied with \"Don't ask again\", or blocked by the system). */\n"
                + "    public static String status(Object host, String group) {\n"
                + "        group = group(group);\n"
                + "        if (has(host, group)) return GRANTED;\n"
                + "        Context context = context(host);\n"
                + "        if (context != null && Build.VERSION.SDK_INT >= 23 && known(group)\n"
                + "                && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(group, false)\n"
                + "                && neverAskAgain(host, group)) {\n"
                + "            return NEVER_ASK_AGAIN;\n"
                + "        }\n"
                + "        return DENIED;\n"
                + "    }\n"
                + "\n"
                + "    /** After a denial: whether Android won't show the dialog again for the group. */\n"
                + "    public static boolean neverAskAgain(Object host, String group) {\n"
                + "        group = group(group);\n"
                + "        Activity activity = activity(host);\n"
                + "        if (activity == null || Build.VERSION.SDK_INT < 23) return false;\n"
                + "        // Android answers with nothing when the dialog was interrupted: not a refusal\n"
                + "        if (group != null && group.equals(emptyAnswer)) return false;\n"
                + "        String[] all = permissions(group, Build.VERSION.SDK_INT, target(activity));\n"
                + "        for (int i = 0; i < all.length; i++) {\n"
                + "            if (!granted(activity, all[i]) && !activity.shouldShowRequestPermissionRationale(all[i])) return true;\n"
                + "        }\n"
                + "        return false;\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Asks for the group. The answer comes to the activity's onRequestPermissionsResult; when nothing needs\n"
                + "     * asking (granted already, Android 5, or a permission the Android version doesn't have) right after this\n"
                + "     * event. While a dialog is open, a second request waits for it.\n"
                + "     */\n"
                + "    public static void request(Object host, String group) {\n"
                + "        group = group(group);\n"
                + "        Activity activity = activity(host);\n"
                + "        if (activity == null) return;\n"
                + "        Activity busy = asking == null ? null : asking.get();\n"
                + "        if (busy != null && !busy.isFinishing() && System.currentTimeMillis() - askedAt < 60000) {\n"
                + "            queue.add(new Object[]{new WeakReference<Activity>(activity), group});\n"
                + "            return;\n"
                + "        }\n"
                + "        ask(activity, group);\n"
                + "    }\n"
                + "\n"
                + "    private static void ask(final Activity activity, String group) {\n"
                + "        final int code = code(group);\n"
                + "        final String[] all = permissions(group, Build.VERSION.SDK_INT, target(activity));\n"
                + "        asking = new WeakReference<Activity>(activity);\n"
                + "        askingGroup = group;\n"
                + "        askedAt = System.currentTimeMillis();\n"
                + "        if (known(group)) {\n"
                + "            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(group, true).apply();\n"
                + "        }\n"
                + "        if (Build.VERSION.SDK_INT >= 23 && known(group) && all.length > 0 && !has(activity, group)) {\n"
                + "            activity.requestPermissions(all, code);\n"
                + "            return;\n"
                + "        }\n"
                + "        new Handler(Looper.getMainLooper()).post(new Runnable() {\n"
                + "            @Override\n"
                + "            public void run() {\n"
                + "                answer(activity, code, all);\n"
                + "            }\n"
                + "        });\n"
                + "    }\n"
                + "\n"
                + "    private static void answer(Activity activity, int code, String[] all) {\n"
                + "        int[] results = new int[all.length];\n"
                + "        for (int i = 0; i < all.length; i++) {\n"
                + "            results[i] = granted(activity, all[i]) ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;\n"
                + "        }\n"
                + "        if (Build.VERSION.SDK_INT >= 23) {\n"
                + "            activity.onRequestPermissionsResult(code, all, results);\n"
                + "            return;\n"
                + "        }\n"
                + "        try {\n"
                + "            // Android 5: Activity has no onRequestPermissionsResult, the generated activity has\n"
                + "            activity.getClass().getMethod(\"onRequestPermissionsResult\", int.class, String[].class, int[].class)\n"
                + "                    .invoke(activity, code, all, results);\n"
                + "        } catch (Exception e) {\n"
                + "            onResult(activity, code, all);\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Called by onRequestPermissionsResult: the group the answer is for, or null when the request code\n"
                + "     * isn't one of these blocks. Starts the next waiting request.\n"
                + "     */\n"
                + "    public static String onResult(Object host, int requestCode, String[] permissions) {\n"
                + "        String group = null;\n"
                + "        if (requestCode == NAME_CODE) {\n"
                + "            group = permissions != null && permissions.length > 0 ? permissions[0] : askingGroup;\n"
                + "        } else if (requestCode >= FIRST_CODE && requestCode < FIRST_CODE + GROUPS.length) {\n"
                + "            group = GROUPS[requestCode - FIRST_CODE];\n"
                + "        }\n"
                + "        if (group == null) return null;\n"
                + "        emptyAnswer = permissions == null || permissions.length == 0 ? group : null;\n"
                + "        asking = null;\n"
                + "        askingGroup = null;\n"
                + "        while (!queue.isEmpty()) {\n"
                + "            Object[] next = queue.remove(0);\n"
                + "            @SuppressWarnings(\"unchecked\")\n"
                + "            final Activity activity = ((WeakReference<Activity>) next[0]).get();\n"
                + "            final String nextGroup = (String) next[1];\n"
                + "            if (activity == null || activity.isFinishing()) continue;\n"
                + "            asking = new WeakReference<Activity>(activity);\n"
                + "            askedAt = System.currentTimeMillis();\n"
                + "            new Handler(Looper.getMainLooper()).post(new Runnable() {\n"
                + "                @Override\n"
                + "                public void run() {\n"
                + "                    ask(activity, nextGroup);\n"
                + "                }\n"
                + "            });\n"
                + "            break;\n"
                + "        }\n"
                + "        return group;\n"
                + "    }\n"
                + "\n"
                + "    private static int code(String group) {\n"
                + "        for (int i = 0; i < GROUPS.length; i++) {\n"
                + "            if (GROUPS[i].equals(group)) return FIRST_CODE + i;\n"
                + "        }\n"
                + "        return NAME_CODE;\n"
                + "    }\n"
                + "\n"
                + "    private static boolean known(String group) {\n"
                + "        if (group == null) return false;\n"
                + "        for (int i = 0; i < GROUPS.length; i++) {\n"
                + "            if (GROUPS[i].equals(group)) return true;\n"
                + "        }\n"
                + "        return isName(group);\n"
                + "    }\n"
                + "\n"
                + "    private static boolean isName(String group) {\n"
                + "        return group != null && group.indexOf('.') > 0 && group.indexOf(' ') < 0;\n"
                + "    }\n"
                + "\n"
                + "    private static boolean granted(Context context, String permission) {\n"
                + "        return context.checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid())\n"
                + "                == PackageManager.PERMISSION_GRANTED;\n"
                + "    }\n"
                + "\n"
                + "    private static int target(Context context) {\n"
                + "        return context.getApplicationInfo().targetSdkVersion;\n"
                + "    }\n"
                + "\n"
                + "    @SuppressWarnings(\"deprecation\")\n"
                + "    private static Activity activity(Object host) {\n"
                + "        if (host instanceof Activity) return (Activity) host;\n"
                + (appCompat ? "        if (host instanceof androidx.fragment.app.Fragment) return ((androidx.fragment.app.Fragment) host).getActivity();\n" : "")
                + "        if (host instanceof android.app.Fragment) return ((android.app.Fragment) host).getActivity();\n"
                + "        return null;\n"
                + "    }\n"
                + "\n"
                + "    private static Context context(Object host) {\n"
                + "        if (host instanceof Context) return (Context) host;\n"
                + "        return activity(host);\n"
                + "    }\n"
                + "}\n";
    }
}
