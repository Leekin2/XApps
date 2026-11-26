package io.github.leekin2.xnotify;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Xiaomi's island and focus notification code lives in the System UI plugin
 * (miui.systemui.plugin), whose class loader appears only when System UI loads it. Features
 * register here and receive each plugin class loader once.
 */
final class SystemUiPlugin {
    private static final List<Consumer<ClassLoader>> LISTENERS = new ArrayList<>();
    private static final Set<ClassLoader> SEEN = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean installed;

    private SystemUiPlugin() {
    }

    static synchronized void onLoaded(XposedInterface xposed, ClassLoader systemUi, Consumer<ClassLoader> listener) throws Exception {
        LISTENERS.add(listener);
        synchronized (SEEN) {
            for (ClassLoader seen : SEEN) listener.accept(seen);
        }
        if (installed) return;
        Class<?> factory = Class.forName("com.android.systemui.shared.plugins.PluginInstance$PluginFactory", false, systemUi);
        Method createLoader = factory.getDeclaredMethod("createClassLoader");
        xposed.hook(createLoader).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object result = chain.proceed();
            if (result instanceof ClassLoader) {
                ClassLoader loader = (ClassLoader) result;
                synchronized (SEEN) {
                    if (!SEEN.add(loader)) return result;
                }
                List<Consumer<ClassLoader>> listeners;
                synchronized (SystemUiPlugin.class) {
                    listeners = new ArrayList<>(LISTENERS);
                }
                for (Consumer<ClassLoader> each : listeners) {
                    try { each.accept(loader); } catch (Throwable ignored) { }
                }
            }
            return result;
        });
        installed = true;
    }
}
