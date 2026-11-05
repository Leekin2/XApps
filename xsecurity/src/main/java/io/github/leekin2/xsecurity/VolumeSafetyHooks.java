package io.github.leekin2.xsecurity;

import java.lang.reflect.Method;

/**
 * Removes the high volume warning and its limit. Runs in system_server: both places that give the
 * safe volume index answer a value no volume reaches, so the warning never starts. Idea from
 * HyperCeiler's VolumeDisableSafe (https://github.com/ReChronoRain/HyperCeiler), written anew.
 */
final class VolumeSafetyHooks {
    private static final int NEVER_REACHED = Integer.MAX_VALUE - 1;
    private final Hooks hooks;
    private final ClassLoader loader;

    VolumeSafetyHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        hook("com.android.server.audio.SoundDoseHelper", "safeMediaVolumeIndex");
        hook("com.android.server.audio.SoundDoseHelperStubImpl", "updateSafeMediaVolumeIndex");
    }

    private void hook(String className, String methodName) {
        try {
            Method method = Class.forName(className, false, loader).getDeclaredMethod(methodName, int.class);
            hooks.constant(method, NEVER_REACHED);
            hooks.info("Volume safety: " + methodName + " answered");
        } catch (Throwable failure) {
            hooks.error("Volume safety: " + methodName + " unavailable", failure);
        }
    }
}
