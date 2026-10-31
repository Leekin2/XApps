package io.github.leekin2.xgoogle;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** One listener in the existing SystemUI gesture controller, with no touch monitor. */
public final class NativeCircleGestureListener {
    private final Object controller, listener;
    private final Method add, remove;
    private volatile boolean registered;

    public NativeCircleGestureListener(Object controller, Class<?> listenerType, Runnable action)
            throws ReflectiveOperationException {
        this.controller = controller;
        add = controller.getClass().getMethod("addHomeHandlerLongClickListener", listenerType);
        remove = controller.getClass().getMethod("removeHomeHandlerLongClickListener", listenerType);
        Method longPress = listenerType.getMethod("onLongPressed");
        if (!listenerType.isInterface() || longPress.getReturnType() != void.class)
            throw new NoSuchMethodException("Unsupported native gesture listener");
        listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "XGoogle native circle listener";
                            default -> null;
                        };
                    }
                    if (registered && method.equals(longPress)) action.run();
                    return null;
                });
    }

    public synchronized void update(boolean enabled) throws ReflectiveOperationException {
        if (registered == enabled) return;
        (enabled ? add : remove).invoke(controller, listener);
        registered = enabled;
    }

    public boolean isRegistered() { return registered; }
}
