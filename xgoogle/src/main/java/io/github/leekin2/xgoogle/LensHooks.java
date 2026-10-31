package io.github.leekin2.xgoogle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

final class LensHooks {
    private static final String DEEPLINK = "com.google.android.apps.search.lens.deeplink.LensDeeplink";
    /** google://lens, which Xiaomi's camera uses; it closes the same way. */
    private static final String EXPORTED = "com.google.android.apps.search.lens.LensExportedActivity";
    private static final String LENS = "com.google.android.apps.search.lens.LensActivity";
    /** Lens's Talk screen, a separate screen that Lens starts. */
    private static final String LIVE = "com.google.android.apps.search.lens.live.activity.LiveActivity";
    /** Set by XGoogle's lock screen shortcut: Lens may show over the lock screen. */
    static final String KEYGUARD_EXTRA = "io.github.leekin2.xgoogle.KEYGUARD";

    /**
     * googleapp://lens is how the lock screen shortcut, the control center tile and other apps
     * open Lens, but on the China ROM Google's deep link screen closes without opening it. Open
     * Lens's own screen from inside the Google app instead.
     */
    static void installDeeplink(HookInstaller hooks) {
        try {
            // The deep link screen lives in a split loaded later; catch it as Android creates it.
            Method create = android.app.Instrumentation.class.getDeclaredMethod("callActivityOnCreate",
                    android.app.Activity.class, android.os.Bundle.class);
            hooks.untracked(create, chain -> {
                android.app.Activity activity = (android.app.Activity) chain.getArg(0);
                if (activity == null || !FeatureConfig.enabled(FeaturePolicy.LENS)) return chain.proceed();
                android.content.Intent opened = activity.getIntent();
                boolean fromLockScreen = opened != null && opened.getBooleanExtra(KEYGUARD_EXTRA, false);
                if (fromLockScreen && LENS.equals(activity.getComponentName().getClassName())) {
                    // Before Lens starts loading, so its recent photo is never read.
                    lockedLens = new java.lang.ref.WeakReference<>(activity);
                    activity.setShowWhenLocked(true);
                    closeOnScreenOff(activity);
                    Object created = chain.proceed();
                    guardGallery(activity);
                    return created;
                }
                // Talk opens its own screen; from Lens over the lock screen it must show there too,
                // or it runs (with the microphone) hidden behind the lock screen.
                if (LIVE.equals(activity.getComponentName().getClassName()) && lockedLens.get() != null) {
                    android.app.KeyguardManager keyguard = activity.getSystemService(android.app.KeyguardManager.class);
                    if (keyguard != null && keyguard.isDeviceLocked()) {
                        // Photos stay locked while Talk is open.
                        lockedLens = new java.lang.ref.WeakReference<>(activity);
                        activity.setShowWhenLocked(true);
                        closeOnScreenOff(activity);
                    }
                    return chain.proceed();
                }
                Object result = chain.proceed();
                // LensDeeplink is an activity-alias: match the component, not the class.
                String component = activity.getComponentName().getClassName();
                if (!DEEPLINK.equals(component) && !EXPORTED.equals(component)) return result;
                android.net.Uri data = activity.getIntent() == null ? null : activity.getIntent().getData();
                if (data == null || !"lens".equals(data.getHost())
                        || !("googleapp".equals(data.getScheme()) || "google".equals(data.getScheme()))) return result;
                try {
                    android.content.Intent lens = new android.content.Intent().setClassName(activity.getPackageName(), LENS)
                            .putExtra(KEYGUARD_EXTRA, fromLockScreen)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    // Over the lock screen Lens must close once it is left or the screen goes off.
                    if (fromLockScreen) lens.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
                            | android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    activity.startActivity(lens);
                    activity.finish();
                } catch (Throwable t) {
                    hooks.error("Lens screen not opened", t);
                }
                return result;
            });
            hooks.info("LENS deep link opens the Lens screen");
        } catch (Throwable error) {
            hooks.error("LENS deep link hook unavailable", error);
        }
    }

    private static volatile java.lang.ref.WeakReference<android.app.Activity> lockedLens = new java.lang.ref.WeakReference<>(null);

    /** True while Lens is open over the lock screen and the phone is still locked. */
    private static boolean galleryBlocked() {
        android.app.Activity lens = lockedLens.get();
        if (lens == null || lens.isFinishing() || lens.isDestroyed()) return false;
        android.app.KeyguardManager keyguard = lens.getSystemService(android.app.KeyguardManager.class);
        return keyguard != null && keyguard.isDeviceLocked();
    }

    /**
     * Lens opened over the lock screen must not show the phone's photos: while it is and the phone
     * is locked, the Google app gets no media (no gallery, no recent photo thumbnail).
     */
    static void installGalleryBlock(HookInstaller hooks) {
        try {
            java.util.List<Method> reads = new java.util.ArrayList<>();
            java.util.Collections.addAll(reads, android.content.ContentResolver.class.getDeclaredMethods());
            java.util.Collections.addAll(reads, android.content.ContentProviderClient.class.getDeclaredMethods());
            for (Method query : reads) {
                String name = query.getName();
                boolean read = name.equals("query") || name.equals("loadThumbnail") || name.equals("openFileDescriptor")
                        || name.equals("openAssetFileDescriptor") || name.equals("openTypedAssetFileDescriptor") || name.equals("openInputStream");
                if (!read || Modifier.isAbstract(query.getModifiers()) || query.getParameterCount() == 0
                        || query.getParameterTypes()[0] != android.net.Uri.class) continue;
                hooks.untracked(query, chain -> {
                    android.net.Uri uri = (android.net.Uri) chain.getArg(0);
                    if (uri == null || !"media".equals(uri.getAuthority()) || !galleryBlocked()) return chain.proceed();
                    if (query.getName().equals("query")) return null;
                    throw new java.io.FileNotFoundException("Locked: photos stay private");
                });
            }
            hooks.info("LENS keeps photos private over the lock screen");
        } catch (Throwable error) {
            hooks.error("LENS photo privacy hook unavailable", error);
        }
    }

    /**
     * Lens's gallery is Android's embedded photo picker, drawn by the media provider, so it cannot
     * be emptied from the Google app. While the phone is locked keep it hidden and ask for the lock
     * instead; once unlocked it shows as usual.
     */
    static void guardGallery(android.app.Activity activity) {
        // Only the picker's surface: on some Google app versions the container also holds the
        // camera buttons.
        int id = activity.getResources().getIdentifier("lens_gallery_surface", "id", activity.getPackageName());
        if (id == 0) return;
        android.view.View decor = activity.getWindow().getDecorView();
        boolean[] asked = {false};
        decor.getViewTreeObserver().addOnPreDrawListener(() -> {
            android.view.View gallery = decor.findViewById(id);
            if (gallery == null) return true;
            if (galleryBlocked()) {
                if (gallery.getVisibility() == android.view.View.VISIBLE) {
                    gallery.setVisibility(android.view.View.INVISIBLE);
                    if (!asked[0]) {
                        asked[0] = true;
                        android.app.KeyguardManager keyguard = activity.getSystemService(android.app.KeyguardManager.class);
                        if (keyguard != null) keyguard.requestDismissKeyguard(activity, new android.app.KeyguardManager.KeyguardDismissCallback() {
                            @Override public void onDismissCancelled() { asked[0] = false; }
                            @Override public void onDismissError() { asked[0] = false; }
                        });
                    }
                }
            } else if (gallery.getVisibility() == android.view.View.INVISIBLE) {
                gallery.setVisibility(android.view.View.VISIBLE);
            }
            return true;
        });
    }

    /** A screen shown over the lock screen must not still be there the next time the phone wakes. */
    static void closeOnScreenOff(android.app.Activity activity) {
        android.content.BroadcastReceiver off = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
                try { activity.unregisterReceiver(this); } catch (Throwable ignored) { }
                activity.finish();
            }
        };
        activity.registerReceiver(off, new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
                android.content.Context.RECEIVER_EXPORTED);
    }

    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        installDeeplink(hooks);
        installGalleryBlock(hooks);
        // Google renames its code with every update: find the Talk and Create eligibility checks
        // by their shape (DexKit), and cache them per Google app version.
        try (AppDexTargets targets = new AppDexTargets(loader, apkPath, dataDir)) {
            installEligibility(hooks, targets.resolve(FeaturePolicy.LENS));
        } catch (Throwable failure) {
            hooks.error("LENS lookup failed; original behavior retained", failure);
        }
    }

    static void installEligibility(HookInstaller hooks, List<Method> targets) {
        if (targets.size() < 2) { hooks.info("LENS skip: eligibility targets not uniquely found"); return; }
        int mark = hooks.mark();
        try {
            for (int i = 0; i < 2; i++) {
                hooks.install(targets.get(i), chain -> FeatureConfig.enabled(FeaturePolicy.LENS) ? true : chain.proceed());
            }
            for (int i = 2; i < targets.size(); i++) hooks.deoptimize(targets.get(i));
            hooks.info("LENS Talk + Create dynamic eligibility hooks installed; callers=" + (targets.size() - 2));
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("LENS incompatible targets; eligibility hooks removed", failure);
        }
    }
}
