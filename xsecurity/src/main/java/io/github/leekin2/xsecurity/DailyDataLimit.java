package io.github.leekin2.xsecurity;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/**
 * Per-app daily mobile data limit and enhanced daily data usage (from kashi's Security Center V8,
 * written anew as clean hooks): lets the user pick a daily MB quota per app in App Info (Block,
 * Notify, or Block + Notify), checks today's mobile usage via NetworkStatsManager, notifies and/or
 * blocks mobile data until midnight, and restores automatically the next day.
 */
final class DailyDataLimit {
    private static final String PREFS = "xsecurity_data_limits";
    private static final String CHANNEL = "xsecurity_data_limit";
    private static final int MODE_BLOCK = 0, MODE_NOTIFY = 1, MODE_BOTH = 2;
    private static final long CHECK_INTERVAL_MS = 5 * 60_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean monitoring;

    private DailyDataLimit() { }

    static void hookEnhancedDailyData(Hooks hooks, ClassLoader loader) {
        try {
            Class<?> miSim = Class.forName("com.miui.networkassistant.utils.MiSimUtil", false, loader);
            for (Method m : miSim.getDeclaredMethods()) {
                if ("isMiSimEnable".equals(m.getName()) && m.getReturnType() == boolean.class) {
                    hooks.constant(m, false);
                }
            }
        } catch (Throwable ignored) { }
        try {
            Context app = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (app != null && hasAnyLimit(app)) startMonitor(app);
        } catch (Throwable ignored) { }
    }

    static String summary(Context context, String pkg, boolean tr) {
        SharedPreferences p = prefs(context);
        int mb = p.getInt("mb_" + pkg, 0);
        if (mb <= 0) return tr ? "Kapalı" : "Off";
        int mode = p.getInt("mode_" + pkg, MODE_BOTH);
        String m = modeLabel(mode, tr);
        return mb + " MB · " + m;
    }

    static void showDialog(Context context, PackageInfo pkg, boolean tr, Runnable onChanged) {
        SharedPreferences p = prefs(context);
        String name = pkg.packageName;
        int curMb = p.getInt("mb_" + name, 0);
        int curMode = p.getInt("mode_" + name, MODE_BOTH);

        float dp = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * dp);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Math.round(12 * dp), pad, Math.round(4 * dp));

        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(tr ? "Günlük sınır (MB)" : "Limit in MB");
        if (curMb > 0) input.setText(String.valueOf(curMb));
        box.addView(input);

        RadioGroup group = new RadioGroup(context);
        String[] labels = tr
                ? new String[] {"Mobil veriyi kapat", "Yalnızca bildir", "Kapat ve bildir"}
                : new String[] {"Turn off mobile data", "Notify only", "Turn off data and notify"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setId(i);
            rb.setText(labels[i]);
            group.addView(rb);
        }
        group.check(Math.max(0, Math.min(2, curMode)));
        box.addView(group);

        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setTitle(tr ? "Günlük mobil veri sınırı" : "Daily mobile data limit")
                .setMessage(tr
                        ? "Uygulama bir günde bu kadar mobil veri kullandığında ne olacağını seçin. Wi-Fi etkilenmez."
                        : "Pick what happens when this app uses this much mobile data in a day. Wi-Fi is not affected.")
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int mb = 0;
                    try { mb = Integer.parseInt(input.getText().toString().trim()); } catch (Throwable ignored) { }
                    if (mb <= 0) {
                        clearLimit(context, name, pkg.applicationInfo != null ? pkg.applicationInfo.uid : -1);
                    } else {
                        int mode = Math.max(0, group.getCheckedRadioButtonId());
                        p.edit().putInt("mb_" + name, mb).putInt("mode_" + name, mode)
                                .remove("hit_" + name).apply();
                        startMonitor(context.getApplicationContext());
                    }
                    if (onChanged != null) onChanged.run();
                });
        if (curMb > 0) {
            b.setNeutralButton(tr ? "Sınırı kaldır" : "Remove limit", (d, w) -> {
                clearLimit(context, name, pkg.applicationInfo != null ? pkg.applicationInfo.uid : -1);
                Toast.makeText(context, tr ? "Veri sınırı kaldırıldı" : "Data limit removed", Toast.LENGTH_SHORT).show();
                if (onChanged != null) onChanged.run();
            });
        }
        b.show();
    }

    private static void clearLimit(Context context, String pkg, int uid) {
        SharedPreferences p = prefs(context);
        boolean wasBlocked = p.getBoolean("blocked_" + pkg, false);
        p.edit().remove("mb_" + pkg).remove("mode_" + pkg)
                .remove("hit_" + pkg).remove("blocked_" + pkg).apply();
        if (wasBlocked && uid > 0) setMobileRestricted(context, uid, false);
    }

    private static String modeLabel(int mode, boolean tr) {
        if (mode == MODE_BLOCK) return tr ? "Engelle" : "Block";
        if (mode == MODE_NOTIFY) return tr ? "Bildir" : "Notify";
        return tr ? "Engelle + bildir" : "Block + notify";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean hasAnyLimit(Context context) {
        try {
            Map<String, ?> all = prefs(context).getAll();
            if (all == null || all.isEmpty()) return false;
            for (Map.Entry<String, ?> e : all.entrySet()) {
                if (e.getKey().startsWith("mb_") && e.getValue() instanceof Integer && (Integer) e.getValue() > 0) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static synchronized void startMonitor(Context context) {
        if (monitoring) return;
        Context app = context.getApplicationContext();
        if (!hasAnyLimit(app)) return;
        monitoring = true;
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (!hasAnyLimit(app)) {
                    synchronized (DailyDataLimit.class) { monitoring = false; }
                    return;
                }
                new Thread(() -> checkAll(app), "xsecurity-data-limit").start();
                MAIN.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };
        MAIN.postDelayed(tick, 15_000L);
    }

    private static void checkAll(Context context) {
        try {
            SharedPreferences p = prefs(context);
            Map<String, ?> all = p.getAll();
            if (all == null || all.isEmpty()) return;
            long midnight = todayMidnightMs();
            long now = System.currentTimeMillis();
            int dayKey = todayKey();
            NetworkStatsManager nsm = context.getSystemService(NetworkStatsManager.class);
            if (nsm == null) return;
            for (Map.Entry<String, ?> e : all.entrySet()) {
                if (!e.getKey().startsWith("mb_") || !(e.getValue() instanceof Integer)) continue;
                int limitMb = (Integer) e.getValue();
                if (limitMb <= 0) continue;
                String pkg = e.getKey().substring(3);
                int uid;
                try { uid = context.getPackageManager().getApplicationInfo(pkg, 0).uid; }
                catch (Throwable t) { continue; }
                int hitDay = p.getInt("hit_" + pkg, 0);
                // New day: automatically lift yesterday's mobile block.
                if (hitDay != 0 && hitDay != dayKey && p.getBoolean("blocked_" + pkg, false)) {
                    setMobileRestricted(context, uid, false);
                    p.edit().remove("blocked_" + pkg).remove("hit_" + pkg).apply();
                    hitDay = 0;
                }
                if (hitDay == dayKey) continue;
                long usedBytes = mobileBytesToday(nsm, uid, midnight, now);
                if (usedBytes < limitMb * 1024L * 1024L) continue;
                int mode = p.getInt("mode_" + pkg, MODE_BOTH);
                SharedPreferences.Editor ed = p.edit().putInt("hit_" + pkg, dayKey);
                if (mode == MODE_BLOCK || mode == MODE_BOTH) {
                    setMobileRestricted(context, uid, true);
                    ed.putBoolean("blocked_" + pkg, true);
                }
                ed.apply();
                if (mode == MODE_NOTIFY || mode == MODE_BOTH) {
                    notifyLimit(context, pkg, limitMb, mode == MODE_BOTH);
                }
            }
        } catch (Throwable ignored) { }
    }

    @SuppressWarnings("deprecation")
    private static long mobileBytesToday(NetworkStatsManager nsm, int uid, long startMs, long endMs) {
        long total = 0;
        try (NetworkStats stats = nsm.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, null, startMs, endMs, uid)) {
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket);
                total += bucket.getRxBytes() + bucket.getTxBytes();
            }
        } catch (Throwable ignored) { }
        return total;
    }

    private static void setMobileRestricted(Context context, int uid, boolean block) {
        try {
            Object npm = context.getSystemService("netpolicy");
            if (npm == null) return;
            Method get = npm.getClass().getMethod("getUidPolicy", int.class);
            Method set = npm.getClass().getMethod("setUidPolicy", int.class, int.class);
            int cur = (Integer) get.invoke(npm, uid);
            // POLICY_REJECT_METERED_BACKGROUND = 1
            int next = block ? (cur | 1) : (cur & ~1);
            set.invoke(npm, uid, next);
        } catch (Throwable ignored) { }
    }

    private static void notifyLimit(Context context, String pkg, int mb, boolean blocked) {
        try {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, tr ? "Uygulama veri sınırları" : "App data limits", NotificationManager.IMPORTANCE_DEFAULT));
            CharSequence label = pkg;
            try { label = context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg, 0)); }
            catch (Throwable ignored) { }
            String title = tr ? "Günlük veri sınırına ulaşıldı" : "Daily data limit reached";
            String body = tr
                    ? (blocked ? label + " günlük " + mb + " MB mobil veri sınırına ulaştı. Gece yarısına kadar kısıtlandı."
                            : label + " bugün " + mb + " MB günlük mobil veri sınırına ulaştı.")
                    : (blocked ? label + " used its " + mb + " MB daily mobile data limit. Restricted until midnight."
                            : label + " used its " + mb + " MB daily mobile data limit today.");
            Notification n = new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .build();
            nm.notify(0x58440000 ^ pkg.hashCode(), n);
        } catch (Throwable ignored) { }
    }

    private static long todayMidnightMs() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static int todayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }
}
