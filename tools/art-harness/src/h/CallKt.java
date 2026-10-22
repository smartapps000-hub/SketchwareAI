package h;

/** Calls KtHelper.greet from fixture 904's built APK on ART (Kotlin compiled by the IDE's kotlinc against kotlin-stdlib 1.8.22). */
public class CallKt {
    public static void main(String[] a) throws Exception {
        Class<?> k = Class.forName("com.fixture.kotlin.KtHelper");
        Object instance = k.getField("INSTANCE").get(null);
        Object r = k.getMethod("greet", java.util.List.class).invoke(instance, java.util.Arrays.asList("a", " ", "b"));
        System.out.println("KtHelper.greet([a, \" \", b]) = " + r);
    }
}
