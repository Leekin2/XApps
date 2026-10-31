package io.github.leekin2.xapps;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Run as root by the XApps KernelSU/Magisk module through app_process:
 * enables XApps in LSPosed and ticks its scope.list, so nobody has to do it by hand.
 * Only adds rows; scope apps the user ticked themselves stay. Settings of the separate XGoogle,
 * XSecurity and XNotify modules are copied over (their groups do not collide), never replacing a
 * setting XApps already has, and so are the apps their users ticked themselves (optional features
 * such as Maps or Photos). LSPosed reads all of it on the next boot.
 *
 * Args: database apkPath userId installedPackagesFile [oldModulePackage...]
 */
public final class ScopeWriter {
    private static final String MODULE = "io.github.leekin2.xapps";
    /** Recommended for optional XGoogle features only; asked for when the feature is switched on. */
    private static final Set<String> OPTIONAL = Set.of("com.google.android.apps.photos", "com.google.android.apps.maps",
            "com.xiaomi.scanner");

    public static void main(String[] args) {
        try {
            List<String> old = new ArrayList<>();
            for (int i = 4; i < args.length; i++) old.add(args[i]);
            int[] counts = write(args[0], args[1], Integer.parseInt(args[2]), installed(args[3]), old);
            System.out.println("ok " + counts[0] + " " + counts[1]);
        } catch (Throwable t) {
            System.out.println("fail " + t);
            System.exit(1);
        }
    }

    private static int[] write(String path, String apk, int user, Set<String> installed, List<String> old) throws Exception {
        List<String> scope = new ArrayList<>();
        for (String app : scopeList())
            if (!OPTIONAL.contains(app) && (app.equals("system") || installed.contains(app))) scope.add(app);
        SQLiteDatabase db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE);
        int added = 0;
        db.beginTransaction();
        try {
            if (columns(db, "modules").contains("mid")) {
                // LSPosed 1.9: scope rows point at the module's numeric id.
                db.execSQL("INSERT OR IGNORE INTO modules (module_pkg_name, apk_path, enabled) VALUES (?, ?, 1)", new Object[]{MODULE, apk});
                db.execSQL("UPDATE modules SET apk_path = ?, enabled = 1 WHERE module_pkg_name = ?", new Object[]{apk, MODULE});
                long mid;
                try (Cursor c = db.rawQuery("SELECT mid FROM modules WHERE module_pkg_name = ?", new String[]{MODULE})) {
                    c.moveToFirst();
                    mid = c.getLong(0);
                }
                for (String app : scope) {
                    if (db.compileStatement("INSERT OR IGNORE INTO scope (mid, app_pkg_name, user_id) VALUES (" + mid + ", '"
                            + app + "', " + user + ")").executeInsert() != -1) added++;
                }
            } else {
                // Newer LSPosed forks: per-user state table, scope keyed by package name.
                db.execSQL("INSERT OR IGNORE INTO modules (module_pkg_name, apk_path) VALUES (?, ?)", new Object[]{MODULE, apk});
                db.execSQL("UPDATE modules SET apk_path = ? WHERE module_pkg_name = ?", new Object[]{apk, MODULE});
                db.execSQL("INSERT OR IGNORE INTO modules_state (module_pkg_name, user_id, enabled) VALUES (?, ?, 1)", new Object[]{MODULE, user});
                db.execSQL("UPDATE modules_state SET enabled = 1 WHERE module_pkg_name = ? AND user_id = ?", new Object[]{MODULE, user});
                for (String app : scope) {
                    if (db.compileStatement("INSERT OR IGNORE INTO scope (module_pkg_name, app_pkg_name, user_id) VALUES ('"
                            + MODULE + "', '" + app + "', " + user + ")").executeInsert() != -1) added++;
                }
            }
            added += copyScopes(db, old, user);
            int copied = copyConfigs(db, old);
            db.setTransactionSuccessful();
            return new int[]{added, copied};
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /** Apps that never go into XApps' scope, even when an old module had them ticked. */
    private static final String SKIPPED = "'com.miui.home', 'com.android.launcher', 'com.oplus.wallpapers'";

    /** Copies the old modules' ticked apps (for this user) to XApps. */
    private static int copyScopes(SQLiteDatabase db, List<String> old, int user) {
        boolean byId = columns(db, "modules").contains("mid");
        int copied = 0;
        for (String module : old) {
            if (!module.matches("[A-Za-z0-9._]+")) continue;
            String sql = byId
                    ? "INSERT OR IGNORE INTO scope (mid, app_pkg_name, user_id) SELECT (SELECT mid FROM modules WHERE module_pkg_name = '"
                        + MODULE + "'), s.app_pkg_name, s.user_id FROM scope s JOIN modules m ON s.mid = m.mid WHERE m.module_pkg_name = '"
                        + module + "' AND s.user_id = " + user + " AND s.app_pkg_name NOT IN (" + SKIPPED + ")"
                    : "INSERT OR IGNORE INTO scope (module_pkg_name, app_pkg_name, user_id) SELECT '" + MODULE
                        + "', app_pkg_name, user_id FROM scope WHERE module_pkg_name = '" + module + "' AND user_id = " + user
                        + " AND app_pkg_name NOT IN (" + SKIPPED + ")";
            copied += db.compileStatement(sql).executeUpdateDelete();
        }
        return copied;
    }

    /** Copies the old modules' remote preferences to XApps; keys XApps already has win. */
    private static int copyConfigs(SQLiteDatabase db, List<String> old) {
        String table = null, group = null, key = null;
        Set<String> config = columns(db, "module_configs");
        if (config.contains("group_name")) { table = "module_configs"; group = "group_name"; key = "key_name"; }
        else if (columns(db, "configs").contains("group")) { table = "configs"; group = "`group`"; key = "`key`"; }
        if (table == null) return 0;
        int copied = 0;
        for (String module : old) {
            if (!module.matches("[A-Za-z0-9._]+")) continue;
            android.database.sqlite.SQLiteStatement copy = db.compileStatement("INSERT OR IGNORE INTO " + table
                    + " (module_pkg_name, user_id, " + group + ", " + key + ", data) SELECT '" + MODULE + "', user_id, "
                    + group + ", " + key + ", data FROM " + table + " WHERE module_pkg_name = '" + module + "'");
            copied += copy.executeUpdateDelete();
        }
        // XGoogle's settings group was called "coogle" before XApps; XGoogle reads "xgoogle" now.
        db.execSQL("INSERT OR IGNORE INTO " + table + " (module_pkg_name, user_id, " + group + ", " + key + ", data) SELECT"
                + " module_pkg_name, user_id, 'xgoogle', " + key + ", data FROM " + table
                + " WHERE module_pkg_name = '" + MODULE + "' AND " + group + " = 'coogle'");
        db.execSQL("DELETE FROM " + table + " WHERE module_pkg_name = '" + MODULE + "' AND " + group + " = 'coogle'");
        return copied;
    }

    private static Set<String> columns(SQLiteDatabase db, String table) {
        Set<String> names = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c.moveToNext()) names.add(c.getString(1));
        }
        return names;
    }

    /** The scope list LSPosed shows as recommended, read from this APK. */
    private static List<String> scopeList() throws Exception {
        List<String> apps = new ArrayList<>();
        try (InputStream in = ScopeWriter.class.getResourceAsStream("/META-INF/xposed/scope.list");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            for (String line; (line = reader.readLine()) != null; ) {
                line = line.trim();
                if (line.matches("[A-Za-z0-9._]+")) apps.add(line);
            }
        }
        return apps;
    }

    /** Lines of `pm list packages` output. */
    private static Set<String> installed(String file) throws Exception {
        Set<String> names = new HashSet<>();
        for (String line : Files.readAllLines(new File(file).toPath())) {
            line = line.trim();
            if (line.startsWith("package:")) names.add(line.substring(8));
        }
        return names;
    }

    private ScopeWriter() { }
}
