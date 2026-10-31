package io.github.leekin2.xgoogle;

import android.app.Activity;
import android.app.KeyguardManager;

/** Google Wallet's tap-to-pay screen over the lock screen. */
final class WalletPreviewHooks {
    /** Waiting for the lock after "Open Wallet"; the tap-to-pay screen stays open meanwhile. */
    private static volatile boolean holding;
    private static boolean unlocking;
    /** Sending Wallet's own deferred lock request. */
    private static boolean requesting;

    private static boolean isPreview(Activity activity) {
        return WalletPowerPolicy.isQuickDrawPreview(activity.getClass().getName(),
                activity.getIntent() == null ? null : activity.getIntent().getAction());
    }

    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        if (!FeatureConfig.enabled(FeaturePolicy.WALLET)) return;
        int mark = hooks.mark();
        try {
            // Wallet asks for the lock (to verify the person) as this screen starts, before it is
            // on screen; HyperOS then cancels the request and Wallet closes the screen. Never answer
            // it falsely (skipping it made Wallet treat the phone as verified and offer "Hold to
            // reader" without any check): send the real request once the screen has focus, so the
            // fingerprint/PIN prompt shows over it.
            hooks.install(KeyguardManager.class.getDeclaredMethod("requestDismissKeyguard",
                    Activity.class, KeyguardManager.KeyguardDismissCallback.class), chain -> {
                Activity activity = (Activity) chain.getArg(0);
                if (requesting || !FeatureConfig.enabled(FeaturePolicy.WALLET) || activity == null
                        || !isPreview(activity) || activity.hasWindowFocus()) return chain.proceed();
                KeyguardManager keyguard = (KeyguardManager) chain.getThisObject();
                KeyguardManager.KeyguardDismissCallback callback = (KeyguardManager.KeyguardDismissCallback) chain.getArg(1);
                android.view.ViewTreeObserver observer = activity.getWindow().getDecorView().getViewTreeObserver();
                observer.addOnWindowFocusChangeListener(new android.view.ViewTreeObserver.OnWindowFocusChangeListener() {
                    @Override public void onWindowFocusChanged(boolean focused) {
                        if (!focused) return;
                        activity.getWindow().getDecorView().getViewTreeObserver().removeOnWindowFocusChangeListener(this);
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        requesting = true;
                        try { keyguard.requestDismissKeyguard(activity, callback); }
                        catch (Throwable error) { hooks.error("WALLET lock request failed", error); }
                        finally { requesting = false; }
                    }
                });
                return null;
            });
            // "Open Wallet" starts the full app and closes this screen straight away; the app then
            // waits behind the lock screen. Ask for the lock first and keep this screen open until
            // it is answered: once unlocked the app opens and the screen closes, as Wallet meant.
            hooks.install(Activity.class.getDeclaredMethod("startActivity", android.content.Intent.class, android.os.Bundle.class), chain -> {
                Activity from = (Activity) chain.getThisObject();
                android.content.Intent intent = (android.content.Intent) chain.getArg(0);
                if (unlocking || !FeatureConfig.enabled(FeaturePolicy.WALLET) || intent == null || intent.getComponent() == null
                        || !WalletPowerPolicy.WALLET_PACKAGE.equals(intent.getComponent().getPackageName())
                        || !isPreview(from)) return chain.proceed();
                KeyguardManager keyguard = from.getSystemService(KeyguardManager.class);
                if (keyguard == null || !keyguard.isDeviceLocked()) return chain.proceed();
                android.os.Bundle options = (android.os.Bundle) chain.getArg(1);
                holding = true;
                keyguard.requestDismissKeyguard(from, new KeyguardManager.KeyguardDismissCallback() {
                    @Override public void onDismissSucceeded() {
                        holding = false;
                        unlocking = true;
                        try { from.startActivity(intent, options); } finally { unlocking = false; }
                        from.finishAndRemoveTask();
                    }
                    @Override public void onDismissCancelled() { holding = false; }
                    @Override public void onDismissError() { holding = false; }
                });
                return null;
            });
            // Wallet closes the screen with finishAndRemoveTask(); hold both ways of closing it.
            for (String close : new String[]{"finish", "finishAndRemoveTask"}) {
                hooks.install(Activity.class.getDeclaredMethod(close), chain ->
                        holding && isPreview((Activity) chain.getThisObject()) ? null : chain.proceed());
            }
            hooks.info("WALLET Open Wallet asks for the lock on the tap-to-pay screen");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("WALLET Open Wallet hook unavailable; original behavior retained", failure);
        }
    }
    private WalletPreviewHooks() {}
}
