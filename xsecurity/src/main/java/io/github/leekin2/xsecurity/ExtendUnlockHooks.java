package io.github.leekin2.xsecurity;

import android.app.KeyguardManager;
import android.content.Context;
import android.util.SparseBooleanArray;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Brings back Google's Extend Unlock (Smart Lock) on HyperOS, where the lock screen never takes the
 * trusted state from the system. The lock screen now follows the state the system itself holds, so
 * nothing is unlocked that a trust agent did not unlock. Adapted from HyperTrust by StevenWin818
 * (GPL-3.0, https://github.com/StevenWin818/HyperTrust); see NOTICE.
 */
final class ExtendUnlockHooks {
    private final Hooks hooks;
    private final ClassLoader loader;

    ExtendUnlockHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() {
        if (!Features.enabled(Features.EXTEND_UNLOCK)) return;
        try {
            Class<?> monitor = loader.loadClass("com.android.keyguard.KeyguardUpdateMonitor");
            Method hasTrust = monitor.getDeclaredMethod("getUserHasTrust", int.class);
            Method simSecure = monitor.getDeclaredMethod("isSimPinSecure");
            Method biometricAllowed = monitor.getDeclaredMethod("isUnlockingWithBiometricAllowed", boolean.class);
            Field cache = monitor.getDeclaredField("mUserHasTrust");
            Field contextField = monitor.getDeclaredField("mContext");
            simSecure.setAccessible(true);
            biometricAllowed.setAccessible(true);
            cache.setAccessible(true);
            contextField.setAccessible(true);
            Method deviceSecure = KeyguardManager.class.getMethod("isDeviceSecure", int.class);
            Method deviceLocked = KeyguardManager.class.getMethod("isDeviceLocked", int.class);
            hooks.install(hasTrust, chain -> {
                Object self = chain.getThisObject();
                int user = (int) chain.getArg(0);
                KeyguardManager keyguard = ((Context) contextField.get(self)).getSystemService(KeyguardManager.class);
                boolean trusted = (boolean) deviceSecure.invoke(keyguard, user)
                        && !(boolean) deviceLocked.invoke(keyguard, user);
                SparseBooleanArray stored = (SparseBooleanArray) cache.get(self);
                if (stored != null) stored.put(user, trusted);
                return trusted && !(boolean) simSecure.invoke(self) && (boolean) biometricAllowed.invoke(self, true);
            });
            hooks.info("Extend Unlock ready");
        } catch (Throwable failure) {
            hooks.error("Extend Unlock unavailable; stock behaviour kept", failure);
        }
    }
}
