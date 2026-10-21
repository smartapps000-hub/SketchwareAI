package android.content;
import java.util.*;
/** In-memory SharedPreferences for the harness. */
public class MapPrefs implements SharedPreferences {
    final Map<String, Object> m = new HashMap<>();
    @SuppressWarnings("unchecked") <T> T g(String k, T d) { Object v = m.get(k); return v == null ? d : (T) v; }
    public Map<String, ?> getAll() { return new HashMap<>(m); }
    public String getString(String k, String d) { return g(k, d); }
    public Set<String> getStringSet(String k, Set<String> d) { return g(k, d); }
    public int getInt(String k, int d) { return g(k, d); }
    public long getLong(String k, long d) { return g(k, d); }
    public float getFloat(String k, float d) { return g(k, d); }
    public boolean getBoolean(String k, boolean d) { return g(k, d); }
    public boolean contains(String k) { return m.containsKey(k); }
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    public Editor edit() {
        return new Editor() {
            final Map<String, Object> p = new HashMap<>(); boolean clear;
            public Editor putString(String k, String v) { p.put(k, v); return this; }
            public Editor putStringSet(String k, Set<String> v) { p.put(k, v); return this; }
            public Editor putInt(String k, int v) { p.put(k, v); return this; }
            public Editor putLong(String k, long v) { p.put(k, v); return this; }
            public Editor putFloat(String k, float v) { p.put(k, v); return this; }
            public Editor putBoolean(String k, boolean v) { p.put(k, v); return this; }
            public Editor remove(String k) { p.put(k, null); return this; }
            public Editor clear() { clear = true; return this; }
            public boolean commit() { if (clear) m.clear(); for (Map.Entry<String, Object> e : p.entrySet()) { if (e.getValue() == null) m.remove(e.getKey()); else m.put(e.getKey(), e.getValue()); } return true; }
            public void apply() { commit(); }
        };
    }
}
