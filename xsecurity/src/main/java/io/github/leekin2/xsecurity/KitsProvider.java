package io.github.leekin2.xsecurity;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves the kits chosen in the lock screen editor. The editor (com.miui.aod) cannot write XApps'
 * settings, and HyperOS holds back broadcasts to an app that is not running, so the editor calls
 * this provider, which starts XApps when needed. Only the system (the editor runs as it) and XApps
 * itself may call it.
 */
public final class KitsProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.leekin2.xapps.kits";
    static final String SAVE = "save";
    static final String LOAD = "load";

    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) return null;
        boolean load = LOAD.equals(method);
        if (!load && (!SAVE.equals(method) || extras == null)) return null;
        String orderText = load ? null : extras.getString("order");
        int y = load ? -1 : extras.getInt("y", -1);
        List<String> order = new ArrayList<>();
        if (orderText != null) {
            for (String id : orderText.split(",")) if (LockKits.known(id) && !order.contains(id)) order.add(id);
        }
        long token = Binder.clearCallingIdentity();
        try {
            // The process may have just started for this call: LSPosed binds shortly after.
            SharedPreferences prefs = null;
            for (int i = 0; i < 30 && prefs == null; i++) {
                io.github.libxposed.service.XposedService service = XSecurityApp.current();
                if (service != null) {
                    try { prefs = service.getRemotePreferences(Features.GROUP); } catch (Throwable ignored) { }
                }
                if (prefs == null) try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
            Bundle out = new Bundle();
            if (prefs == null) { out.putBoolean("saved", false); return out; }
            if (load) {
                // What is stored now (the editor's own copy dates from when its process started).
                out.putBoolean("loaded", true);
                out.putString("order", String.join(",", LockKits.stored(prefs)));
                out.putInt("y", prefs.getInt(Features.AOD_KITS_Y, 0));
                out.putString("grid", prefs.getString(Features.AOD_KITS_GRID, LockKits.Grid.WIDE.name()));
                out.putBoolean("square", prefs.getBoolean(Features.AOD_KITS_SQUARE, false));
                return out;
            }
            LockKits.Grid layout = LockKits.Grid.read(extras.getString("grid", prefs.getString(Features.AOD_KITS_GRID, null)));
            if (!LockKits.fitsGrid(orderText != null ? order : LockKits.stored(prefs), layout)) {
                out.putBoolean("saved", false);
                return out;
            }
            SharedPreferences.Editor edit = prefs.edit();
            if (extras.containsKey("grid")) edit.putString(Features.AOD_KITS_GRID, layout.name());
            if (orderText != null) edit.putStringSet(Features.AOD_KIT_LIST, LockKits.encode(order));
            if (y >= 0) edit.putInt(Features.AOD_KITS_Y, y);
            if (!edit.commit()) { out.putBoolean("saved", false); return out; }
            out.putBoolean("saved", true);
            out.putBoolean("on_aod", prefs.getBoolean(Features.AOD_KITS_ON_AOD, false));
            out.putBoolean("hide_notif", prefs.getBoolean(Features.AOD_KITS_HIDE_NOTIF, false));
            return out;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
