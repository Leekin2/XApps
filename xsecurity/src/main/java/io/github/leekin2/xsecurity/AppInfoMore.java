package io.github.leekin2.xsecurity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.widget.Toast;
import java.io.File;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * More rows on the Security app's App info page, as in kashi's Security Center mod: install and
 * update dates, split APKs, signature, granted permissions, activities (tap to open) and an APK
 * backup to /sdcard/Security. Written anew.
 */
final class AppInfoMore {
    private AppInfoMore() { }

    static List<String[]> rows(Context context, PackageInfo pkg, boolean tr) {
        List<String[]> rows = new ArrayList<>();
        DateFormat date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        rows.add(new String[] {"xsecurity_installed", tr ? "Yüklenme" : "Installed", date.format(new Date(pkg.firstInstallTime))});
        rows.add(new String[] {"xsecurity_updated", tr ? "Son güncelleme" : "Last updated", date.format(new Date(pkg.lastUpdateTime))});
        String[] splits = pkg.applicationInfo.splitSourceDirs;
        rows.add(new String[] {"xsecurity_splits", tr ? "Bölünmüş APK'lar" : "Split APKs", String.valueOf(splits == null ? 0 : splits.length)});
        rows.add(new String[] {"xsecurity_cert", tr ? "İmza (SHA-256)" : "Signature (SHA-256)", signature(context, pkg.packageName)});
        PackageInfo full = full(context, pkg.packageName, PackageManager.GET_PERMISSIONS);
        int granted = 0;
        if (full != null && full.requestedPermissionsFlags != null) {
            for (int flag : full.requestedPermissionsFlags) if ((flag & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) granted++;
        }
        rows.add(new String[] {"xsecurity_perms", tr ? "Verilen izinler" : "Granted permissions", String.valueOf(granted)});
        PackageInfo acts = full(context, pkg.packageName, PackageManager.GET_ACTIVITIES);
        rows.add(new String[] {"xsecurity_activities", tr ? "Aktiviteler" : "Activities", String.valueOf(acts == null || acts.activities == null ? 0 : acts.activities.length)});
        rows.add(new String[] {"xsecurity_backup", tr ? "APK'yı yedekle" : "Back up APK", "/sdcard/Security"});
        if (Features.enabled(Features.DAILY_DATA_LIMIT)) {
            rows.add(new String[] {"xsecurity_data_limit", tr ? "Günlük mobil veri sınırı" : "Daily mobile data limit",
                    DailyDataLimit.summary(context, pkg.packageName, tr)});
        }
        return rows;
    }

    /** Handles a tap on one of our rows; false leaves it to the default (copy the value). */
    static boolean click(Context context, PackageInfo pkg, String key, boolean tr) {
        switch (key) {
            case "xsecurity_perms": permissions(context, pkg.packageName, tr); return true;
            case "xsecurity_activities": activities(context, pkg.packageName, tr); return true;
            case "xsecurity_backup": backup(context, pkg, tr); return true;
            case "xsecurity_data_limit": DailyDataLimit.showDialog(context, pkg, tr, null); return true;
            default: return false;
        }
    }

    private static PackageInfo full(Context context, String name, int flags) {
        try {
            return context.getPackageManager().getPackageInfo(name, flags);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String signature(Context context, String name) {
        PackageInfo info = full(context, name, PackageManager.GET_SIGNING_CERTIFICATES);
        if (info == null || info.signingInfo == null) return "-";
        Signature[] signers = info.signingInfo.hasMultipleSigners()
                ? info.signingInfo.getApkContentsSigners() : info.signingInfo.getSigningCertificateHistory();
        if (signers == null || signers.length == 0) return "-";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signers[signers.length - 1].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) hex.append(':');
                hex.append(String.format("%02X", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return "-";
        }
    }

    private static void permissions(Context context, String name, boolean tr) {
        PackageInfo info = full(context, name, PackageManager.GET_PERMISSIONS);
        List<String> granted = new ArrayList<>();
        if (info != null && info.requestedPermissions != null) {
            for (int i = 0; i < info.requestedPermissions.length; i++) {
                if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) granted.add(info.requestedPermissions[i]);
            }
        }
        if (granted.isEmpty()) granted.add(tr ? "Verilmiş izin yok" : "No granted permissions");
        String[] items = granted.toArray(new String[0]);
        new AlertDialog.Builder(context)
                .setTitle(tr ? "Verilen izinler" : "Granted permissions")
                .setItems(items, (d, which) -> copy(context, items[which], tr))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static void activities(Context context, String name, boolean tr) {
        PackageInfo info = full(context, name, PackageManager.GET_ACTIVITIES);
        ActivityInfo[] all = info == null || info.activities == null ? new ActivityInfo[0] : info.activities;
        String[] items = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            String short_ = all[i].name.startsWith(name) ? all[i].name.substring(name.length()) : all[i].name;
            items[i] = (all[i].exported ? "▶ " : "") + short_;
        }
        new AlertDialog.Builder(context)
                .setTitle(tr ? "Aktiviteler (▶ açılabilir)" : "Activities (▶ can open)")
                .setItems(items, (d, which) -> {
                    try {
                        // Security runs as the system user, so non-exported pages open too.
                        context.startActivity(new Intent().setClassName(name, all[which].name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable t) {
                        Toast.makeText(context, tr ? "Açılamadı" : "Could not open", Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static void backup(Context context, PackageInfo pkg, boolean tr) {
        Toast.makeText(context, tr ? "Yedekleniyor…" : "Backing up…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok;
            String where;
            try {
                CharSequence label = context.getPackageManager().getApplicationLabel(pkg.applicationInfo);
                String base = (label + "_" + pkg.versionName).replaceAll("[^\\p{L}\\p{N}._-]+", "_");
                File dir = new File("/sdcard/Security");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create " + dir);
                String[] splits = pkg.applicationInfo.splitSourceDirs;
                if (splits == null || splits.length == 0) {
                    File out = new File(dir, base + ".apk");
                    java.nio.file.Files.copy(new File(pkg.applicationInfo.sourceDir).toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    where = out.getPath();
                } else {
                    // Split apps go into one folder with every part, ready for a split installer.
                    File folder = new File(dir, base);
                    if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Cannot create " + folder);
                    java.nio.file.Files.copy(new File(pkg.applicationInfo.sourceDir).toPath(), new File(folder, "base.apk").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    for (String split : splits) {
                        File source = new File(split);
                        java.nio.file.Files.copy(source.toPath(), new File(folder, source.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    where = folder.getPath();
                }
                ok = true;
            } catch (Throwable t) {
                android.util.Log.w("XSecurity", "APK backup failed", t);
                ok = false;
                where = t.getMessage();
            }
            boolean done = ok;
            String path = where;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(context,
                    done ? (tr ? "Kaydedildi: " : "Saved: ") + path : (tr ? "Yedeklenemedi: " : "Backup failed: ") + path,
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    private static void copy(Context context, String text, boolean tr) {
        ClipboardManager clip = context.getSystemService(ClipboardManager.class);
        clip.setPrimaryClip(ClipData.newPlainText("text", text));
        Toast.makeText(context, tr ? "Kopyalandı" : "Copied", Toast.LENGTH_SHORT).show();
    }
}
