package io.github.leekin2.xgoogle;

import io.github.leekin2.xgoogle.migration.MigrationEntry;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class XGoogleModule extends XposedModule {
    private final HookInstaller hooks = new HookInstaller(this);
    private String processName;

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
        MigrationEntry.attach(this);
        try {
            android.content.SharedPreferences settings = getRemotePreferences(FeatureConfig.GROUP);
            // Until the app has moved them, saved settings are still under the old group name.
            if (settings.getAll().isEmpty()) settings = getRemotePreferences(FeatureConfig.OLD_GROUP);
            FeatureConfig.attach(settings);
        }
        catch (Throwable failure) { hooks.error("Configuration unavailable; verified defaults retained",failure); }
        hooks.info("XGoogle " + BuildConfig.VERSION_NAME + " loaded in " + param.getProcessName() + "; framework=" + getFrameworkName()
                + " API=" + getApiVersion());
    }

    @Override public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        if (!SystemCompatibility.isSupported()) return;
        MigrationEntry.attach(this);
        MigrationEntry.hookSystem(param.getClassLoader());
        SignalModelHooks.install(hooks, param.getClassLoader());
        if (FeatureConfig.enabled(FeaturePolicy.PASSKEY)) PasskeyHooks.hookSystem(hooks, param.getClassLoader());
        if (FeatureConfig.enabled(FeaturePolicy.LOCATION)) LocationHooks.hookSystem(hooks, param.getClassLoader());
        hooks.info("SYSTEM native assistant and hotword initialization complete");
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!SystemCompatibility.isSupported()) return;
        // Play Integrity (DroidGuard) runs here; any hook in it can cost the integrity verdicts.
        if (TimelineHooks.INTEGRITY_PROCESS.equals(processName)) return;
        // Wallet renames its code with every update; the preview unlock is found by its shape.
        if ("com.google.android.apps.walletnfcrel".equals(packageName))
            WalletPreviewHooks.install(hooks, param.getClassLoader(), param.getApplicationInfo().sourceDir,
                    param.getApplicationInfo().dataDir);
        if (TimelineHooks.handles(packageName) && FeatureConfig.enabled(FeaturePolicy.TIMELINE))
            TimelineHooks.install(hooks, param.getClassLoader(), packageName);
        if (PasskeyHooks.handles(packageName) && FeatureConfig.enabled(FeaturePolicy.PASSKEY))
            PasskeyHooks.install(hooks, param.getClassLoader(), packageName, param.getApplicationInfo().sourceDir);
        if (PhotosUnlimitedHooks.PHOTOS.equals(packageName) && FeatureConfig.enabled(FeaturePolicy.PHOTOS_UNLIMITED))
            PhotosUnlimitedHooks.install(hooks, processName);
        if (PhotosUnlimitedHooks.PHOTOS.equals(packageName) && FeatureConfig.enabled(FeaturePolicy.PHOTOS_DCIM))
            PhotosFolderHooks.install(hooks, param.getClassLoader(), processName, param.getApplicationInfo().sourceDir,
                    param.getApplicationInfo().dataDir);
        if ("com.android.camera".equals(packageName) && FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS)) CameraLensHooks.install(hooks, param.getClassLoader(), param.getApplicationInfo().sourceDir, param.getApplicationInfo().dataDir);
        if ("com.android.systemui".equals(packageName) || "com.android.settings".equals(packageName)
                || "com.google.android.apps.walletnfcrel".equals(packageName)
                || "com.miui.voiceassist".equals(packageName)
                || "com.miui.aod".equals(packageName)
                || "com.miui.securitycore".equals(packageName)
                || "com.google.android.googlequicksearchbox".equals(packageName)) {
            MigrationEntry.attach(this);
            MigrationEntry.hookPackage(packageName, param.getClassLoader());
            if ("com.google.android.googlequicksearchbox".equals(packageName) && FeatureConfig.enabled(FeaturePolicy.LENS)) LensHooks.install(hooks, param.getClassLoader(),
                    param.getApplicationInfo().sourceDir, param.getApplicationInfo().dataDir);
            if ("com.google.android.googlequicksearchbox".equals(packageName) && FeatureConfig.enabled(FeaturePolicy.LIVE_TRANSLATE))
                LiveTranslateHooks.install(hooks, param.getClassLoader(), param.getApplicationInfo().sourceDir,
                        param.getApplicationInfo().dataDir);
        }
    }

}
