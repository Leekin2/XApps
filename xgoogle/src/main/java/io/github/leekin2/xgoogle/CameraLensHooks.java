package io.github.leekin2.xgoogle;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.UsingFieldData;
import org.luckypray.dexkit.wrap.DexMethod;
import static io.github.leekin2.xgoogle.HookInstaller.target;

/**
 * Xiaomi's camera has a Google Lens button in its top bar, shown only on the global ROM: the gate
 * is "not a China build and Lens is installed". On the China ROM let the Google app count, so the
 * button appears; it opens google://lens, which LensHooks routes to Lens.
 */
final class CameraLensHooks {
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";

    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        try {
            Method gate = cachedGate(hooks, loader, apkPath, dataDir);
            if (gate == null) throw new NoSuchMethodException("Camera Lens gate not found");
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

    /** One scan per camera build; every update renames the gate's class. */
    private static Method cachedGate(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) throws Exception {
        File apk = new File(apkPath);
        String key = apkPath + ":" + apk.lastModified() + ":" + apk.length();
        File cache = dataDir == null ? null : new File(dataDir, "xgoogle_camera_lens.cache");
        if (cache != null && cache.isFile()) {
            try {
                String[] stored = new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8).split("\n", -1);
                if (stored.length == 2 && stored[0].equals(key) && !stored[1].isEmpty()) {
                    Method method = new DexMethod(stored[1]).getMethodInstance(loader);
                    if (fits(method)) return method;
                }
            } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError stale) {
                // Scan again.
            }
        }
        Method found = findGate(hooks, loader, apkPath);
        if (found != null && cache != null) {
            try {
                File temporary = File.createTempFile("xgoogle_camera_lens", ".tmp", new File(dataDir));
                Files.write(temporary.toPath(), (key + "\n" + new DexMethod(found).serialize()).getBytes(StandardCharsets.UTF_8));
                if (!temporary.renameTo(cache)) temporary.delete();
            } catch (IOException | RuntimeException unwritable) {
                hooks.info("CAMERA Lens cache not written: " + unwritable);
            }
        }
        return found;
    }

    /**
     * The check that google://lens resolves is one lazy value, kept alone in its own class, and both
     * methods naming that URI read it. The gate is the only static no-argument boolean reading it.
     */
    private static Method findGate(HookInstaller hooks, ClassLoader loader, String apkPath) throws ReflectiveOperationException {
        AppDexTargets.loadNative();
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            Set<String> shared = null;
            for (MethodData named : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .usingStrings(List.of("google://lens"), StringMatchType.Equals, false)))) {
                Set<String> read = new HashSet<>();
                for (UsingFieldData using : named.getUsingFields()) {
                    FieldData field = using.getField();
                    if (Modifier.isStatic(field.getModifiers()) && field.getDeclaredClass().getFields().size() == 1)
                        read.add(field.getDescriptor());
                }
                if (shared == null) shared = read; else shared.retainAll(read);
            }
            List<Method> gates = new ArrayList<>();
            if (shared != null) for (String field : shared) {
                for (MethodData reader : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                        .paramCount(0).returnType("boolean").addUsingField(field)))) {
                    Method method = reader.getMethodInstance(loader);
                    if (fits(method)) gates.add(method);
                }
            }
            if (gates.size() == 1) return gates.get(0);
            hooks.info("CAMERA Lens gate candidates: " + gates.size());
        }
        // Camera builds from before this scan was tested.
        Method old = target(loader, "Xr.m").getDeclaredMethod("b");
        return fits(old) ? old : null;
    }

    private static boolean fits(Method method) {
        return method.getReturnType() == boolean.class && Modifier.isStatic(method.getModifiers())
                && method.getParameterCount() == 0;
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
