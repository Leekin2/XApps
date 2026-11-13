package io.github.leekin2.xnotify;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Xiaomi has distinct register and unregister methods with the same parameter type. */
final class ReceiveScreenBinding {
    static Method register(Class<?> model, Class<?> listener) throws NoSuchMethodException {
        Method method = model.getDeclaredMethod("o", listener);
        if (Modifier.isStatic(method.getModifiers()) || Modifier.isAbstract(method.getModifiers())
                || method.getReturnType() != void.class) throw new NoSuchMethodException("Invalid notification listener register method");
        method.setAccessible(true);
        return method;
    }

    private ReceiveScreenBinding() { }
}
