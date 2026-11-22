package com.sketchware.ai.project;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import a.a.a.eC;
import a.a.a.hC;

/**
 * v2.1.4 (owner's 22 Nov review: "after choosing the project the description should come by itself, from a deep
 * analysis of it"): a Play Store description written from the saved project itself: its screens and how they are
 * reached (side menu, tabs, bottom navigation), what the user can tap (button and menu labels), what the app does
 * (components, notable widgets, libraries), the permissions it asks for, and the Android versions. Plain text with
 * bullets (the store page shows it as written), at most {@link #MAX} characters. Reads the saved project with its own
 * instances (never the editor's live copy); nothing is invented: a part the project doesn't have isn't mentioned.
 */
public final class StoreDescription {

    public static final int MAX = 1500;

    private StoreDescription() {
    }

    /** The description of saved project {@code scId}, or null when it can't be read. */
    public static String of(String scId) {
        JsonObject s;
        try {
            s = ProjectDiffAnalyzer.snapshot(scId);
        } catch (RuntimeException e) {
            return null;
        }
        // a project that couldn't be read (no screens) isn't described: the field stays for the user's words
        if (s.has("readError") || ProjectDiffAnalyzer.object(s, "screens").size() == 0) return null;
        return write(s, labels(scId));
    }

    /** Button / tab / menu words people tap, per screen in order (at most a few each), read from the saved layouts. */
    static List<String> labels(String scId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            hC files = new hC(scId);
            files.i();
            eC data = new eC(scId);
            data.g();
            data.e();
            ArrayList<ProjectFileBean> activities = files.b();
            if (activities == null || data.c == null) return new ArrayList<>(out);
            for (ProjectFileBean file : activities) {
                ArrayList<ViewBean> beans = data.c.get(file.getXmlName());
                if (beans == null) continue;
                for (ViewBean bean : beans) {
                    if (bean == null || bean.text == null) continue;
                    String type = ViewBean.getViewTypeName(bean.type);
                    if (type == null || !(type.endsWith("Button") || type.equals("CheckBox") || type.equals("Switch")
                            || type.equals("RadioButton"))) continue;
                    String label = clean(bean.text.text);
                    if (label != null) out.add(label);
                    if (out.size() >= 12) return new ArrayList<>(out);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // the labels are only a part of the description
        }
        return new ArrayList<>(out);
    }

    /** A label as people read it: one line, no placeholders ("Button", "TextView", "@string/…"), 2-28 characters. */
    static String clean(String raw) {
        if (raw == null) return null;
        String t = raw.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (t.length() < 2 || t.length() > 28) return null;
        if (t.startsWith("@") || t.startsWith("?")) return null;
        String lower = t.toLowerCase(Locale.ROOT);
        for (String placeholder : new String[]{"button", "textview", "checkbox", "switch", "radiobutton", "text",
                "new button", "click", "click me", "ok", "edittext"}) {
            if (lower.equals(placeholder) || lower.matches(placeholder + "[0-9]+")) return null;
        }
        return t;
    }

    /** The description from a snapshot ({@link ProjectDiffAnalyzer#snapshot}) and the tap labels. */
    public static String write(JsonObject s, List<String> labels) {
        String title = ProjectDiffAnalyzer.title(s);
        StringBuilder sb = new StringBuilder();
        Set<String> features = features(s);
        sb.append(title).append(" is an Android app");
        List<String> top = new ArrayList<>();
        for (String f : features) {
            if (top.size() == 2) break;
            top.add(lowerFirst(f));
        }
        if (!top.isEmpty()) sb.append(" that ").append(join(top, 2));
        sb.append(".\n\n");

        JsonObject screens = ProjectDiffAnalyzer.object(s, "screens");
        List<String> names = new ArrayList<>();
        Set<String> parts = new TreeSet<>();
        Set<String> nav = new TreeSet<>();
        for (Map.Entry<String, JsonElement> e : screens.entrySet()) {
            names.add(screenName(e.getKey()));
            if (!e.getValue().isJsonObject()) continue;
            JsonObject screen = e.getValue().getAsJsonObject();
            for (JsonElement p : ProjectDiffAnalyzer.array(screen, "parts")) {
                if (p.isJsonPrimitive()) parts.add(p.getAsString());
            }
            for (Map.Entry<String, JsonElement> v : ProjectDiffAnalyzer.object(screen, "views").entrySet()) {
                String type = v.getValue().isJsonPrimitive() ? v.getValue().getAsString() : "";
                if (type.equals("BottomNavigationView")) nav.add("bottom navigation");
                if (type.equals("TabLayout") || type.equals("ViewPager")) nav.add("tabs");
            }
        }
        if (parts.contains("navigation drawer")) nav.add("a side menu");
        if (!names.isEmpty()) {
            sb.append(names.size() == 1 ? "One screen" : names.size() + " screens").append(": ")
                    .append(join(names, 6));
            if (!nav.isEmpty()) sb.append(", reached with ").append(join(new ArrayList<>(nav), 3));
            sb.append(".\n");
        }
        if (labels != null && !labels.isEmpty()) {
            sb.append("Tap: ").append(join(labels, 8)).append(".\n");
        }

        if (!features.isEmpty()) {
            sb.append("\nWhat it does:\n");
            int n = 0;
            for (String f : features) {
                if (n++ >= 10) break;
                sb.append("• ").append(f).append('\n');
            }
        }

        List<String> permissions = permissionWords(s);
        if (!permissions.isEmpty()) {
            sb.append("\nAsks for: ").append(join(permissions, 8)).append(".\n");
        }

        String min = ProjectDiffAnalyzer.string(s, "minSdk");
        String target = ProjectDiffAnalyzer.string(s, "targetSdk");
        sb.append('\n').append("Made with Sketchware AI");
        if (s.has("material3") && s.get("material3").isJsonPrimitive() && s.get("material3").getAsBoolean()) {
            sb.append(" · Material 3 design");
        }
        if (target != null && !target.isEmpty()) {
            sb.append(" · Android ");
            if (min != null && !min.isEmpty()) sb.append(ProjectDiffAnalyzer.androidName(min)).append(" to ");
            sb.append(ProjectDiffAnalyzer.androidName(target));
        }
        sb.append('.');
        String text = sb.toString();
        return text.length() > MAX ? text.substring(0, MAX - 1) + "…" : text;
    }

    /** "MainActivity" → "Main", "SettingsActivity" → "Settings", "history_activity" → "History". */
    static String screenName(String javaName) {
        String n = javaName == null ? "" : javaName.replace(".java", "");
        if (n.endsWith("Activity") && n.length() > "Activity".length()) n = n.substring(0, n.length() - "Activity".length());
        n = n.replace('_', ' ').trim();
        // "ParkingHistory" → "Parking History"
        n = n.replaceAll("([a-z])([A-Z])", "$1 $2");
        return n.isEmpty() ? "Main" : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** What the app does, in plain words, from its components, widgets and libraries (most telling first). */
    static Set<String> features(JsonObject s) {
        Set<String> kinds = new TreeSet<>();
        JsonObject screens = ProjectDiffAnalyzer.object(s, "screens");
        Set<String> components = new TreeSet<>();
        for (Map.Entry<String, JsonElement> e : screens.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject screen = e.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> c : ProjectDiffAnalyzer.object(screen, "components").entrySet()) {
                if (c.getValue().isJsonPrimitive()) components.add(c.getValue().getAsString());
            }
            for (Map.Entry<String, JsonElement> v : ProjectDiffAnalyzer.object(screen, "views").entrySet()) {
                if (v.getValue().isJsonPrimitive()) kinds.add(v.getValue().getAsString());
            }
        }
        Set<String> libraries = new TreeSet<>();
        for (JsonElement l : ProjectDiffAnalyzer.array(s, "libraries")) if (l.isJsonPrimitive()) libraries.add(l.getAsString());

        Map<String, Boolean> out = new LinkedHashMap<>();
        if (kinds.contains("WebView")) out.put("Opens web pages inside the app", true);
        if (components.contains("Camera")) out.put("Takes photos with the camera", true);
        if (components.contains("File Picker")) out.put("Picks images and files from the phone", true);
        if (kinds.contains("MapView") || libraries.contains("Google Maps")) out.put("Shows maps", true);
        if (components.contains("Location")) out.put("Uses your location", true);
        if (components.contains("RequestNetwork") || components.contains("GitHub")) {
            out.put(components.contains("GitHub") ? "Works with GitHub online" : "Loads data from the internet", true);
        }
        if (components.contains("Firebase Auth") || components.contains("Firebase Phone Auth")
                || components.contains("Google Sign-In")) out.put("Lets you sign in to your account", true);
        if (components.contains("Firebase Database")) out.put("Keeps data in the cloud (Firebase)", true);
        if (components.contains("Firebase Storage")) out.put("Uploads and downloads files (Firebase Storage)", true);
        if (components.contains("Firebase Cloud Messaging") || components.contains("OneSignal")) {
            out.put("Receives push notifications", true);
        }
        if (components.contains("Notification")) out.put("Shows notifications", true);
        if (components.contains("SharedPreferences")) out.put("Remembers your data and settings on the phone", true);
        if (kinds.contains("VideoView")) out.put("Plays videos", true);
        if (components.contains("MediaPlayer") || components.contains("SoundPool")) out.put("Plays music and sounds", true);
        if (components.contains("TextToSpeech")) out.put("Reads text aloud", true);
        if (components.contains("SpeechToText")) out.put("Understands your voice", true);
        if (components.contains("Bluetooth")) out.put("Connects to Bluetooth devices", true);
        if (components.contains("Timer")) out.put("Runs timers", true);
        if (components.contains("DatePicker") || components.contains("TimePicker") || components.contains("Calendar")
                || kinds.contains("CalendarView") || kinds.contains("DatePicker") || kinds.contains("TimePicker")) {
            out.put("Works with dates and times", true);
        }
        if (kinds.contains("ListView") || kinds.contains("RecyclerView") || kinds.contains("GridView")) {
            out.put("Shows your items in scrolling lists", true);
        }
        if (kinds.contains("LottieAnimationView") || components.contains("ObjectAnimator")) out.put("Has smooth animations", true);
        if (components.contains("Vibrator")) out.put("Vibrates for feedback", true);
        if (components.contains("Gyroscope")) out.put("Reacts to how you hold the phone", true);
        if (components.contains("Interstitial Ad") || components.contains("Rewarded Ad") || kinds.contains("AdView")
                || components.contains("Facebook Banner Ad") || components.contains("Facebook Interstitial Ad")) {
            out.put("Shows ads", true);
        }
        if (kinds.contains("EditText") || kinds.contains("TextInputLayout")) out.put("Lets you type and save your own text", true);
        return new LinkedHashSet<>(out.keySet());
    }

    /** The permissions in words people know ("Camera", "Internet", …), each once. */
    static List<String> permissionWords(JsonObject s) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        JsonArray list = ProjectDiffAnalyzer.array(s, "permissions");
        for (JsonElement e : list) {
            if (!e.isJsonPrimitive()) continue;
            String p = e.getAsString().toUpperCase(Locale.ROOT);
            if (p.contains("CAMERA")) out.add("Camera");
            else if (p.contains("RECORD_AUDIO")) out.add("Microphone");
            else if (p.contains("LOCATION")) out.add("Location");
            else if (p.contains("BLUETOOTH")) out.add("Bluetooth");
            else if (p.contains("STORAGE") || p.contains("MEDIA_IMAGES") || p.contains("MEDIA_VIDEO")
                    || p.contains("MEDIA_AUDIO")) out.add("Photos and files");
            else if (p.contains("CONTACTS")) out.add("Contacts");
            else if (p.contains("CALL_PHONE") || p.contains("PHONE_STATE")) out.add("Phone");
            else if (p.contains("SMS")) out.add("SMS");
            else if (p.contains("CALENDAR")) out.add("Calendar");
            else if (p.contains("NOTIFICATIONS")) out.add("Notifications");
            else if (p.equals("INTERNET") || p.contains("NETWORK_STATE")) out.add("Internet");
            else if (p.contains("VIBRATE")) out.add("Vibration");
        }
        return new ArrayList<>(out);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String join(List<String> items, int most) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(items.size(), most);
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(i == shown - 1 && items.size() <= most ? " and " : ", ");
            sb.append(items.get(i));
        }
        if (items.size() > most) sb.append(" and ").append(items.size() - most).append(" more");
        return sb.toString();
    }
}
