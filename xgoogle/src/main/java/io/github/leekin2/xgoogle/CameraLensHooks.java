package io.github.leekin2.xgoogle;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import static io.github.leekin2.xgoogle.HookInstaller.target;

/**
 * Xiaomi's camera has a Google Lens button in its top bar, shown only on the global ROM: the gate
 * is "not a China build and Lens is installed". On the China ROM let the Google app count, so the
 * button appears; it opens google://lens, which LensHooks routes to Lens.
 */
final class CameraLensHooks {
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";

    static void install(HookInstaller hooks, ClassLoader loader) {
        try {
            Method gate = target(loader, "Xr.m").getDeclaredMethod("b");
            // Do not apply this binding to a camera version whose layout changed.
            if (gate.getReturnType() != boolean.class || !Modifier.isStatic(gate.getModifiers()))
                throw new NoSuchMethodException("Camera Lens gate signature changed");
            hooks.untracked(gate, chain -> {
                Object shown = chain.proceed();
                if (Boolean.TRUE.equals(shown) || !FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS)) return shown;
                return googleInstalled();
            });
            hooks.info("CAMERA Google Lens button enabled");
        } catch (Throwable error) {
            hooks.error("CAMERA Lens button unavailable for this camera version", error);
        }
        installLockScreen(hooks);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingDismiss;
    private static boolean dismissing;

    /**
     * Over the lock screen the camera asks for the lock before opening Lens. Lens shows over the
     * lock screen itself, as from XGoogle's lock screen shortcut (photos stay locked), so open it
     * there. The camera asks for the lock just before it starts Lens: hold each request until the
     * camera's click is done, and drop it when that click opened Lens.
     */
    private static void installLockScreen(HookInstaller hooks) {
        try {
            Method dismiss = KeyguardManager.class.getDeclaredMethod("requestDismissKeyguard",
                    Activity.class, KeyguardManager.KeyguardDismissCallback.class);
            hooks.untracked(dismiss, chain -> {
                KeyguardManager keyguard = (KeyguardManager) chain.getThisObject();
                if (dismissing || Looper.myLooper() != Looper.getMainLooper() || !lockScreenLens()
                        || !keyguard.isKeyguardLocked()) return chain.proceed();
                Activity activity = (Activity) chain.getArg(0);
                KeyguardManager.KeyguardDismissCallback callback = (KeyguardManager.KeyguardDismissCallback) chain.getArg(1);
                Runnable later = () -> {
                    pendingDismiss = null;
                    if (activity.isDestroyed()) return;
                    dismissing = true;
                    try { keyguard.requestDismissKeyguard(activity, callback); }
                    catch (Throwable error) { hooks.error("CAMERA lock request failed", error); }
                    finally { dismissing = false; }
                };
                if (pendingDismiss != null) MAIN.removeCallbacks(pendingDismiss);
                pendingDismiss = later;
                MAIN.post(later);
                return null;
            });
            Method start = Activity.class.getDeclaredMethod("startActivityForResult", Intent.class, int.class, Bundle.class);
            hooks.untracked(start, chain -> {
                Intent intent = (Intent) chain.getArg(0);
                Uri data = intent == null ? null : intent.getData();
                if (data != null && lockScreenLens() && "google".equals(data.getScheme()) && "lens".equals(data.getHost())
                        && GOOGLE.equals(intent.getPackage())) {
                    KeyguardManager keyguard = ((Activity) chain.getThisObject()).getSystemService(KeyguardManager.class);
                    if (keyguard != null && keyguard.isKeyguardLocked()) {
                        if (pendingDismiss != null) { MAIN.removeCallbacks(pendingDismiss); pendingDismiss = null; }
                        // Open it as the lock screen shortcut does: googleapp://lens in its own
                        // task (the camera's task closes when the lock screen comes back).
                        intent.setData(Uri.parse("googleapp://lens")).putExtra(LensHooks.KEYGUARD_EXTRA, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                }
                return chain.proceed();
            });
            hooks.info("CAMERA Lens opens over the lock screen");
        } catch (Throwable error) {
            hooks.error("CAMERA lock screen Lens unavailable", error);
        }
    }

    /** Both switches: Lens shows over the lock screen only through XGoogle's Google app hooks. */
    private static boolean lockScreenLens() {
        return FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS) && FeatureConfig.enabled(FeaturePolicy.LENS);
    }

    private static boolean googleInstalled() {
        try {
            android.content.Context context = (android.content.Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            context.getPackageManager().getPackageInfo(GOOGLE, 0);
            return true;
        } catch (Throwable missing) {
            return false;
        }
    }

    private CameraLensHooks() { }
}
