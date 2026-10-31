package io.github.leekin2.xnotify;

import android.app.Application;
import android.util.Log;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.List;

/**
 * Pop-ups and the reply box need the System UI scope, which LSPosed does not add when an existing
 * install is updated. When LSPosed connects, ask for it once; LSPosed shows a one-tap approval notice.
 */
public final class ScopeApp {
    private static final String SYSTEMUI = "com.android.systemui";
    /** LSPosed's connection, for the settings screen; null until LSPosed binds. */
    static volatile XposedService service;

    /** Called once from XApps' Application. */
    public static void start(Application app) {
        io.github.leekin2.xapps.common.ServiceHub.register(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService bound) {
                service = bound;
                requestSystemUi(app, bound);
            }
            @Override public void onServiceDied(XposedService died) { service = null; }
        });
    }

    private static void requestSystemUi(Application app, XposedService service) {
        try {
            List<String> missing = new java.util.ArrayList<>(List.of(SYSTEMUI));
            missing.removeAll(service.getScope());
            if (missing.isEmpty()) return;
            // Ask once per version, so a declined request does not come back at every boot.
            android.content.SharedPreferences prefs = app.getSharedPreferences("xnotify_scope", android.content.Context.MODE_PRIVATE);
            long version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).getLongVersionCode();
            if (prefs.getLong("asked", 0) == version) return;
            prefs.edit().putLong("asked", version).apply();
            service.requestScope(missing, new XposedService.OnScopeEventListener() {
                @Override public void onScopeRequestApproved(List<String> approved) { Log.i("XNotify", "Scope approved: " + approved); }
                @Override public void onScopeRequestFailed(String message) { Log.i("XNotify", "Scope not added: " + message); }
            });
        } catch (Throwable t) {
            Log.w("XNotify", "Scope request failed", t);
        }
    }
}
