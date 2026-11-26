package io.github.leekin2.xsecurity;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * XAod: the always-on display part of XApps. HyperOS runs the AOD (com.miui.aod) inside System UI
 * as a plugin, so its code appears there with its own class loader when System UI loads it; the
 * AOD app's own process is hooked too, for builds that run it there. It has its own switch on the
 * XApps home screen and shares XSecurity's settings store and hook helpers.
 */
public final class XAodModule extends XposedModule {
    private static final String AOD = "com.miui.aod";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String MARKER = "com.miui.aod.Utils";
    /** HyperOS's lock screen editor runs in its own process of the AOD app. */
    private static final String EDITOR = "com.miui.aod:keyguardeditor";
    private final Hooks hooks = new Hooks(this);
    private final Set<ClassLoader> done = Collections.newSetFromMap(new WeakHashMap<>());
    private String process;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        process = param.getProcessName();
        if (!AOD.equals(process) && !SYSTEM_UI.equals(process) && !EDITOR.equals(process)) return;
        try { Features.attach(getRemotePreferences(Features.GROUP)); }
        catch (Throwable failure) { hooks.error("Settings unavailable; defaults used", failure); }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        String name = param.getPackageName();
        if (AOD.equals(name) && AOD.equals(process)) {
            install(param.getClassLoader());
        } else if (AOD.equals(name) && EDITOR.equals(process)) {
            new AodHooks(hooks, param.getClassLoader()).installEditor();
        } else if (SYSTEM_UI.equals(name) && SYSTEM_UI.equals(process)) {
            // Nothing to do in System UI while every XAod switch is off.
            if (!anyOn()) { hooks.info("XAod: every switch is off"); return; }
            watchPlugins(param.getClassLoader());
        }
    }

    private static boolean anyOn() {
        for (String key : new String[] {Features.AOD_GRAYSCALE, Features.AOD_LUNAR, Features.AOD_EDITOR_UNLOCK, Features.AOD_FULLSCREEN,
                Features.AOD_FLAGSHIP, Features.AOD_TIMEOUT, Features.AOD_BATTERY_CUTOFF, Features.AOD_BURNIN, Features.AOD_GLOW,
                Features.AOD_CHARGING, Features.AOD_NOW_PLAYING, Features.AOD_DARK_OFF, Features.AOD_KITS, Features.AOD_ALL,
                Features.AOD_SCREEN_STATE, Features.AOD_SMART_SENSOR, Features.AOD_STANDARD_SENSORS}) {
            if (Features.enabled(key)) return true;
        }
        return false;
    }

    /** Each System UI plugin gets its own class loader; the AOD's is the one that has its classes. */
    private void watchPlugins(ClassLoader systemUi) {
        try {
            Class<?> factory = Class.forName("com.android.systemui.shared.plugins.PluginInstance$PluginFactory", false, systemUi);
            Method create = factory.getDeclaredMethod("createClassLoader");
            hook(create).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof ClassLoader) {
                    ClassLoader loader = (ClassLoader) result;
                    try {
                        Class<?> marker = Class.forName(MARKER, false, loader);
                        // Only the AOD plugin's own copy, not one System UI shares with every plugin.
                        if (marker.getClassLoader() == loader) install(loader);
                    } catch (ClassNotFoundException notAod) {
                        // another plugin
                    }
                }
                return result;
            });
            hooks.info("AOD plugin watch ready");
            if (Features.enabled(Features.AOD_GLOW) || Features.enabled(Features.AOD_CHARGING) || Features.enabled(Features.AOD_NOW_PLAYING) || Features.enabled(Features.AOD_KITS)) {
                try {
                    AodOverlay.installKeyguard(hooks, systemUi);
                    hooks.info("full screen AOD extras ready");
                } catch (Throwable failure) {
                    hooks.error("full screen AOD extras unavailable", failure);
                }
            }
        } catch (Throwable failure) {
            hooks.error("AOD plugin watch unavailable", failure);
        }
    }

    private void install(ClassLoader loader) {
        synchronized (done) {
            if (!done.add(loader)) return;
        }
        new AodHooks(hooks, loader).install();
    }
}
