package io.github.leekin2.xsecurity;

import android.os.VibrationEffect;
import java.lang.reflect.Method;

/**
 * One short buzz for notifications that use the system's default vibration. Runs in system_server.
 * Insistent notifications (calls, alarms) and apps with their own pattern are left as they are.
 */
final class NotificationVibrationHooks {
    private static final long BUZZ_MS = 70;
    private final Hooks hooks;
    private final ClassLoader loader;

    NotificationVibrationHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        try {
            Class<?> helper = Class.forName("com.android.server.notification.VibratorHelper", false, loader);
            for (String name : new String[]{"createDefaultVibration", "createFallbackVibration"}) {
                Method method = helper.getDeclaredMethod(name, boolean.class);
                hooks.install(method, chain -> Boolean.TRUE.equals(chain.getArg(0)) ? chain.proceed()
                        : VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE));
            }
            hooks.info("Notification vibration: short buzz set");
        } catch (Throwable failure) {
            hooks.error("Notification vibration unavailable", failure);
        }
    }
}
