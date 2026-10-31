package io.github.leekin2.xapps;

import android.content.SharedPreferences;
import android.util.Log;
import io.github.leekin2.xgoogle.XGoogleModule;
import io.github.leekin2.xnotify.XNotifyModule;
import io.github.leekin2.xsecurity.XSecurityModule;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The single LSPosed entry. Each part (XGoogle, XSecurity, XNotify) stays its own module class and
 * runs on XApps' framework connection; a part switched off in XApps is never created, so it puts
 * no hook anywhere. A part that throws is logged and the others carry on.
 */
public final class XAppsModule extends XposedModule {
    private static final String TAG = "XApps";
    private final List<XposedModule> parts = new ArrayList<>();

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        SharedPreferences prefs = null;
        try { prefs = getRemotePreferences(Parts.GROUP); }
        catch (Throwable unavailable) { Log.w(TAG, "Settings unavailable; every part kept on", unavailable); }
        add(prefs, Parts.GOOGLE, XGoogleModule::new);
        add(prefs, Parts.SECURITY, XSecurityModule::new);
        add(prefs, Parts.NOTIFY, XNotifyModule::new);
        for (XposedModule part : parts) {
            try { part.onModuleLoaded(param); }
            catch (Throwable failure) { Log.e(TAG, part.getClass().getSimpleName() + " failed to load", failure); }
        }
    }

    private void add(SharedPreferences prefs, String key, Supplier<XposedModule> create) {
        if (prefs != null && !prefs.getBoolean(key, true)) return;
        XposedModule part = create.get();
        part.attachFramework(this, () -> { });
        parts.add(part);
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        for (XposedModule part : parts) {
            try { part.onSystemServerStarting(param); }
            catch (Throwable failure) { Log.e(TAG, part.getClass().getSimpleName() + " failed in system_server", failure); }
        }
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        for (XposedModule part : parts) {
            try { part.onPackageLoaded(param); }
            catch (Throwable failure) { Log.e(TAG, part.getClass().getSimpleName() + " failed in " + param.getPackageName(), failure); }
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        for (XposedModule part : parts) {
            try { part.onPackageReady(param); }
            catch (Throwable failure) { Log.e(TAG, part.getClass().getSimpleName() + " failed in " + param.getPackageName(), failure); }
        }
    }
}
