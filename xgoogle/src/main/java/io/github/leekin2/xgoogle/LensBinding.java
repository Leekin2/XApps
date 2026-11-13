package io.github.leekin2.xgoogle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;

/** Names may change; retain the inspected eligibility signatures and controller layouts. */
final class LensBinding {
    static boolean isPair(Method create, Method talk) {
        for (Constructor<?> constructor : create.getDeclaringClass().getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length != 4) continue;
            Method[] pair = select(java.util.List.of(create, talk), p[1]);
            if (pair != null && pair[0].equals(create) && pair[1].equals(talk)) return true;
        }
        return false;
    }
    static boolean is1775Pair(Method create, Method talk) {
        if (!isPredicate(create) || !isPredicate(talk) || !create.getName().equals("c")
                || !talk.getName().equals("c") || !create.getDeclaringClass().getName().equals("cvtu")
                || !talk.getDeclaringClass().getName().equals("chmv")) return false;
        for (Constructor<?> constructor : create.getDeclaringClass().getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 4 && p[0].getName().equals("com.google.apps.tiktok.account.AccountId")
                    && p[1] == talk.getDeclaringClass() && p[2] == boolean.class && p[3] == long.class) return true;
        }
        return false;
    }

    static Method[] select(Collection<Method> candidates, Class<?> gate) {
        Method create = null, talk = null;
        for (Method method : candidates) {
            if (!isPredicate(method)) continue;
            Class<?> owner = method.getDeclaringClass();
            for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length == 4 && p[0].getName().equals("com.google.apps.tiktok.account.AccountId")
                        && p[1] == gate && p[2] == boolean.class && p[3] == long.class) {
                    if (create != null && !create.equals(method)) return null;
                    create = method;
                }
                if (isTalkConstructor(p) && hasModeGetter(owner)) {
                    if (talk != null && !talk.equals(method)) return null;
                    talk = method;
                }
            }
        }
        return create == null || talk == null || create.equals(talk) ? null : new Method[]{create, talk};
    }

    static boolean isPredicate(Method method) {
        int flags = method.getModifiers();
        return method.getParameterCount() == 0 && method.getReturnType() == boolean.class
                && !Modifier.isStatic(flags) && !Modifier.isAbstract(flags) && !Modifier.isNative(flags)
                && !method.isSynthetic();
    }

    /** Three references, the flags, one reference: Google 17.67 has two flags, 17.68 six. */
    private static boolean isTalkConstructor(Class<?>[] p) {
        // Talk controller layouts seen so far: three leading references with two, six or ten flags
        // (Google 17.67, 17.68, 17.70); four leading references with ten or eleven flags (17.72, 17.73).
        if ((p.length != 6 && p.length != 10 && p.length != 14 && p.length != 15 && p.length != 16)
                || p[p.length - 1].isPrimitive()) return false;
        int flagsStart = p.length >= 15 ? 4 : 3;
        for (int i = 0; i < flagsStart; i++) if (p[i].isPrimitive()) return false;
        for (int i = flagsStart; i < p.length - 1; i++) if (p[i] != boolean.class) return false;
        return true;
    }

    private static boolean hasModeGetter(Class<?> owner) {
        int count = 0;
        for (Method method : owner.getDeclaredMethods())
            if (method.getReturnType() == long.class && method.getParameterCount() == 0
                    && !Modifier.isStatic(method.getModifiers())) count++;
        return count == 1;
    }
    private LensBinding() {}
}
