package io.github.leekin2.xsecurity;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Battery protection page, as in kashi's Security Center mod: live current, voltage, power and
 * time to full (updated every second while the page is open), and a health history that keeps
 * the battery's actual capacity and cycle count once a day. Written anew.
 */
final class BatteryLive {
    private static final String PAGE = "com.miui.powercenter.nightcharge.ChargeProtectFragment";
    private static final String HISTORY = "xsecurity_battery_history";
    private final Hooks hooks;
    private final ClassLoader loader;

    BatteryLive(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() throws Throwable {
        Class<?> fragment = Class.forName(PAGE, false, loader);
        Class<?> preference = Class.forName("androidx.preference.Preference", false, loader);
        Class<?> group = Class.forName("androidx.preference.PreferenceGroup", false, loader);
        Class<?> category = Class.forName("miuix.preference.PreferenceCategory", false, loader);
        Class<?> text = Class.forName("miuix.preference.TextPreference", false, loader);
        Constructor<?> newCategory = category.getConstructor(Context.class, android.util.AttributeSet.class);
        Constructor<?> newText = text.getConstructor(Context.class);
        Method setTitle = preference.getMethod("setTitle", CharSequence.class);
        Method setKey = preference.getMethod("setKey", String.class);
        Method setPersistent = preference.getMethod("setPersistent", boolean.class);
        Method setText = text.getMethod("setText", String.class);
        Method add = group.getMethod("addPreference", preference);
        Method screen = fragment.getMethod("getPreferenceScreen");
        Method contextOf = fragment.getMethod("getContext");
        Method added = fragment.getMethod("isAdded");
        Method resumed = fragment.getMethod("isResumed");
        Method setOnClick = null;
        for (Method m : preference.getMethods()) {
            if (m.getName().equals("setOnPreferenceClickListener") && m.getParameterCount() == 1) setOnClick = m;
        }
        final Method onClick = setOnClick;

        java.util.WeakHashMap<Object, Runnable> resumeTick = new java.util.WeakHashMap<>();
        try {
            hooks.install(fragment.getMethod("onResume"), chain -> {
                Object result = chain.proceed();
                Runnable r = resumeTick.get(chain.getThisObject());
                if (r != null) r.run();
                return result;
            });
        } catch (Throwable ignored) { }

        hooks.install(fragment.getMethod("onCreatePreferences", android.os.Bundle.class, String.class), chain -> {
            Object result = chain.proceed();
            Object self = chain.getThisObject();
            Context ctx = (Context) contextOf.invoke(self);
            Object root = screen.invoke(self);
            if (ctx == null || root == null) return result;
            String[] w = words();
            recordToday(ctx);

            Object section = newCategory.newInstance(ctx, null);
            setKey.invoke(section, "xsecurity_battery_live");
            setTitle.invoke(section, w[0]);
            add.invoke(root, section);
            Object[] rows = new Object[4];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = newText.newInstance(ctx);
                setKey.invoke(rows[i], "xsecurity_battery_live_" + i);
                setPersistent.invoke(rows[i], false);
                setTitle.invoke(rows[i], w[1 + i]);
                add.invoke(section, rows[i]);
            }
            Object history = newText.newInstance(ctx);
            setKey.invoke(history, "xsecurity_battery_history");
            setPersistent.invoke(history, false);
            setTitle.invoke(history, w[7]);
            setText.invoke(history, String.valueOf(entries(ctx).size()));
            if (onClick != null && onClick.getParameterTypes()[0].isInterface()) {
                Object listener = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {onClick.getParameterTypes()[0]}, (proxy, m, args) -> {
                    switch (m.getName()) {
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "xsecurity_battery_history";
                        default: showHistory(ctx); return true;
                    }
                });
                onClick.invoke(history, listener);
            }
            add.invoke(section, history);

            Handler main = new Handler(Looper.getMainLooper());
            Runnable tick = new Runnable() {
                @Override public void run() {
                    try {
                        if (!(Boolean) added.invoke(self) || !(Boolean) resumed.invoke(self)) return;
                        String[] values = live(ctx, w);
                        for (int i = 0; i < rows.length; i++) setText.invoke(rows[i], values[i]);
                        main.postDelayed(this, 1000);
                    } catch (Throwable t) {
                        hooks.error("battery live values", t);
                    }
                }
            };
            resumeTick.put(self, () -> {
                main.removeCallbacks(tick);
                main.post(tick);
            });
            main.post(tick);
            return result;
        });
    }

    /** Section, current, voltage, power, time to full, charging, discharging, history. */
    private static String[] words() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return new String[] {"Canlı pil bilgisi", "Akım", "Voltaj", "Güç", "Dolmasına kalan", "şarj oluyor", "boşalıyor", "Sağlık geçmişi"};
            case "ru": return new String[] {"Батарея сейчас", "Ток", "Напряжение", "Мощность", "До полной зарядки", "заряжается", "разряжается", "История здоровья"};
            case "es": return new String[] {"Batería en vivo", "Corriente", "Voltaje", "Potencia", "Tiempo hasta carga completa", "cargando", "descargando", "Historial de salud"};
            case "zh": return new String[] {"实时电池信息", "电流", "电压", "功率", "充满还需", "充电中", "放电中", "健康历史"};
            default: return new String[] {"Battery now", "Current", "Voltage", "Power", "Time to full", "charging", "discharging", "Health history"};
        }
    }

    private static String[] live(Context ctx, String[] w) {
        BatteryManager battery = ctx.getSystemService(BatteryManager.class);
        Intent sticky = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        long microAmps = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        int milliVolts = sticky == null ? 0 : sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int status = sticky == null ? 0 : sticky.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        // Some kernels report milliamps; anything this small is taken as mA.
        double milliAmps = Math.abs(microAmps) > 20_000 ? microAmps / 1000.0 : microAmps;
        double watts = Math.abs(milliAmps) * milliVolts / 1_000_000.0;
        String current = String.format(java.util.Locale.getDefault(), "%.0f mA (%s)", Math.abs(milliAmps), charging ? w[5] : w[6]);
        String voltage = String.format(java.util.Locale.getDefault(), "%.3f V", milliVolts / 1000.0);
        String power = String.format(java.util.Locale.getDefault(), "%.2f W", watts);
        String full = "-";
        if (charging) {
            long left = battery.computeChargeTimeRemaining();
            if (left > 0) {
                long minutes = left / 60_000;
                full = minutes >= 60 ? (minutes / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", minutes % 60) : minutes + "'";
            }
        }
        return new String[] {current, voltage, power, full};
    }

    private static long readLong(String name) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/sys/class/power_supply/battery/" + name))) {
            String line = reader.readLine();
            return line == null ? -1 : Long.parseLong(line.trim());
        } catch (Throwable unavailable) {
            return -1;
        }
    }

    /** One entry per day: "day|capacity mAh|design mAh|cycles". */
    private static void recordToday(Context ctx) {
        long full = readLong("charge_full");
        if (full <= 0) return;
        long day = System.currentTimeMillis() / 86_400_000L;
        SharedPreferences prefs = ctx.getSharedPreferences(HISTORY, Context.MODE_PRIVATE);
        if (prefs.getLong("last_day", -1) == day) return;
        String entry = day + "|" + full / 1000 + "|" + readLong("charge_full_design") / 1000 + "|" + readLong("cycle_count");
        String all = prefs.getString("entries", "");
        String[] lines = all.isEmpty() ? new String[0] : all.split("\n");
        StringBuilder kept = new StringBuilder();
        // Keep about a year.
        for (int i = Math.max(0, lines.length - 364); i < lines.length; i++) kept.append(lines[i]).append('\n');
        kept.append(entry);
        prefs.edit().putLong("last_day", day).putString("entries", kept.toString()).apply();
    }

    private static List<String[]> entries(Context ctx) {
        String all = ctx.getSharedPreferences(HISTORY, Context.MODE_PRIVATE).getString("entries", "");
        List<String[]> out = new ArrayList<>();
        if (all.isEmpty()) return out;
        for (String line : all.split("\n")) {
            String[] parts = line.split("\\|");
            if (parts.length == 4) out.add(parts);
        }
        return out;
    }

    private static void showHistory(Context ctx) {
        List<String[]> list = entries(ctx);
        DateFormat date = DateFormat.getDateInstance(DateFormat.MEDIUM);
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String[] e = list.get(list.size() - 1 - i);
            long design = Long.parseLong(e[2]);
            long cap = Long.parseLong(e[1]);
            String health = design > 0 ? " · %" + Math.round(cap * 100.0 / design) : "";
            items[i] = date.format(new Date(Long.parseLong(e[0]) * 86_400_000L)) + "\n" + cap + " mAh" + health + " · " + e[3] + " ⟳";
        }
        String[] w = words();
        new android.app.AlertDialog.Builder(ctx)
                .setTitle(w[7])
                .setItems(items, null)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
