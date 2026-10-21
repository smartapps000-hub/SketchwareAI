package h;

import java.lang.reflect.*;
import java.util.*;

/** Reflection helpers: the harness calls the shipped APK's own classes without compiling against them. */
public final class R {
    private R() {}

    public static Class<?> c(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean fits(Class<?> p, Object a) {
        if (a == null) return !p.isPrimitive();
        Class<?> w = p.isPrimitive() ? box(p) : p;
        return w.isInstance(a);
    }

    private static Class<?> box(Class<?> p) {
        if (p == int.class) return Integer.class;
        if (p == boolean.class) return Boolean.class;
        if (p == long.class) return Long.class;
        if (p == double.class) return Double.class;
        if (p == float.class) return Float.class;
        if (p == char.class) return Character.class;
        if (p == byte.class) return Byte.class;
        if (p == short.class) return Short.class;
        return p;
    }

    private static Method find(Class<?> k, String name, Object[] args) {
        for (Class<?> x = k; x != null; x = x.getSuperclass()) {
            for (Method m : x.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                Class<?>[] ps = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < ps.length && ok; i++) ok = fits(ps[i], args[i]);
                if (ok) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new RuntimeException("no method " + k.getName() + "." + name + "/" + args.length + " for " + Arrays.toString(args));
    }

    /** Instance call (target is an object) or static call (target is a Class). */
    public static Object call(Object target, String name, Object... args) throws Throwable {
        boolean st = target instanceof Class;
        Class<?> k = st ? (Class<?>) target : target.getClass();
        Method m = find(k, name, args);
        try {
            return m.invoke(st ? null : target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    public static Object neu(String cls, Object... args) throws Throwable {
        for (Constructor<?> c : c(cls).getDeclaredConstructors()) {
            if (c.getParameterCount() != args.length) continue;
            Class<?>[] ps = c.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < ps.length && ok; i++) ok = fits(ps[i], args[i]);
            if (!ok) continue;
            c.setAccessible(true);
            try {
                return c.newInstance(args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
        throw new RuntimeException("no constructor " + cls + "/" + args.length);
    }

    public static Field field(Class<?> k, String name) {
        for (Class<?> x = k; x != null; x = x.getSuperclass()) {
            try {
                Field f = x.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new RuntimeException("no field " + k.getName() + "." + name);
    }

    public static Object get(Object target, String name) throws Exception {
        boolean st = target instanceof Class;
        return field(st ? (Class<?>) target : target.getClass(), name).get(st ? null : target);
    }

    public static void set(Object target, String name, Object value) throws Exception {
        boolean st = target instanceof Class;
        Field f = field(st ? (Class<?>) target : target.getClass(), name);
        if (Modifier.isFinal(f.getModifiers())) {
            try {
                Field acc = Field.class.getDeclaredField("accessFlags");
                acc.setAccessible(true);
                acc.setInt(f, f.getModifiers() & ~Modifier.FINAL);
            } catch (NoSuchFieldException ignored) {
            }
        }
        f.set(st ? null : target, value);
    }

    /** A java.lang.reflect.Proxy for any interface; every call is logged and returns a default. */
    public static Object proxy(String iface, java.util.function.BiFunction<String, Object[], Object> handler) {
        Class<?> k = c(iface);
        return Proxy.newProxyInstance(k.getClassLoader(), new Class<?>[]{k}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) {
                switch (m.getName()) {
                    case "toString": return "proxy " + iface;
                    case "hashCode": return System.identityHashCode(p);
                    case "equals": return p == a[0];
                }
            }
            Object r = handler.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == null && m.getReturnType().isPrimitive()) {
                Class<?> t = m.getReturnType();
                if (t == boolean.class) return false;
                if (t == void.class) return null;
                if (t == long.class) return 0L;
                if (t == double.class) return 0.0;
                if (t == float.class) return 0f;
                return 0;
            }
            return r;
        });
    }
}
