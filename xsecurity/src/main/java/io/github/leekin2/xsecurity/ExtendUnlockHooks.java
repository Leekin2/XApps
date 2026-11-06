package io.github.leekin2.xsecurity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Brings back Google's Extend Unlock (Smart Lock) on HyperOS. HyperOS's lock screen implements the
 * system trust listener but never registers it, so it never learns that a trust agent unlocked the
 * phone. It is registered once when the lock screen starts; from then on the lock screen uses
 * Android's own trust state, unchanged. Idea from HyperTrust by StevenWin818 (GPL-3.0,
 * https://github.com/StevenWin818/HyperTrust); see NOTICE.
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
            Class<?> listener = Class.forName("android.app.trust.TrustManager$TrustListener");
            if (!listener.isAssignableFrom(monitor)) throw new IllegalStateException("Lock screen is not a trust listener");
            Method start = monitor.getDeclaredMethod("start");
            Field trustField = monitor.getDeclaredField("mTrustManager");
            trustField.setAccessible(true);
            Method register = trustField.getType().getMethod("registerTrustListener", listener);
            hooks.install(start, chain -> {
                Object result = chain.proceed();
                Object self = chain.getThisObject();
                try {
                    Object trust = trustField.get(self);
                    if (trust != null) register.invoke(trust, self);
                    hooks.info("Extend Unlock: lock screen follows the system trust state");
                } catch (Throwable failure) {
                    hooks.error("Extend Unlock could not register; stock behaviour kept", failure);
                }
                return result;
            });
            hooks.info("Extend Unlock ready");
        } catch (Throwable failure) {
            hooks.error("Extend Unlock unavailable; stock behaviour kept", failure);
        }
    }
}
