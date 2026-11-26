package io.github.leekin2.xsecurity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.DropBoxManager;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * "Crash logs" in the Security app's settings, as in kashi's Security Center mod: the crash
 * reports Android keeps in its DropBox, split into user apps, system apps and boot problems, each
 * readable and copyable. Security runs as the system user, which may read them. Written anew.
 */
final class CrashLogs {
    private static final String[] USER = {"data_app_crash", "data_app_native_crash", "data_app_anr"};
    private static final String[] SYSTEM = {"system_app_crash", "system_app_native_crash", "system_app_anr", "system_app_wtf"};
    private static final String[] BOOT = {"system_server_crash", "system_server_native_crash", "system_server_anr",
            "system_server_watchdog", "SYSTEM_RESTART", "SYSTEM_LAST_KMSG", "SYSTEM_RECOVERY_KMSG", "SYSTEM_TOMBSTONE"};
    private final Hooks hooks;
    private final ClassLoader loader;

    CrashLogs(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() throws Throwable {
        Class<?> fragment = Class.forName("com.miui.securityscan.ui.settings.SettingsFragment", false, loader);
        Class<?> preference = Class.forName("androidx.preference.Preference", false, loader);
        Class<?> group = Class.forName("androidx.preference.PreferenceGroup", false, loader);
        Class<?> text = Class.forName("miuix.preference.TextPreference", false, loader);
        Method setTitle = preference.getMethod("setTitle", CharSequence.class);
        Method setSummary = preference.getMethod("setSummary", CharSequence.class);
        Method setKey = preference.getMethod("setKey", String.class);
        Method add = group.getMethod("addPreference", preference);
        Method screen = fragment.getMethod("getPreferenceScreen");
        Method contextOf = fragment.getMethod("getContext");
        Method setOnClick = null;
        for (Method m : preference.getMethods()) {
            if (m.getName().equals("setOnPreferenceClickListener") && m.getParameterCount() == 1) setOnClick = m;
        }
        if (setOnClick == null || !setOnClick.getParameterTypes()[0].isInterface()) throw new NoSuchMethodException("Preference click listener");
        final Method onClick = setOnClick;
        hooks.install(fragment.getMethod("onCreatePreferences", android.os.Bundle.class, String.class), chain -> {
            Object result = chain.proceed();
            Object self = chain.getThisObject();
            Context ctx = (Context) contextOf.invoke(self);
            Object root = screen.invoke(self);
            if (ctx == null || root == null) return result;
            String[] w = words();
            Object row = text.getConstructor(Context.class).newInstance(ctx);
            setKey.invoke(row, "xsecurity_crash_logs");
            setTitle.invoke(row, w[0]);
            setSummary.invoke(row, w[1]);
            Object listener = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {onClick.getParameterTypes()[0]}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "xsecurity_crash_logs";
                    default: categories(ctx); return true;
                }
            });
            onClick.invoke(row, listener);
            add.invoke(root, row);
            return result;
        });
    }

    /** Title, summary, user, system, boot, empty, gone, copy. */
    private static String[] words() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return new String[] {"Çökme kayıtları", "Sistemin tuttuğu çökme raporları", "Kullanıcı uygulaması çökmeleri", "Sistem uygulaması çökmeleri", "Sistem ve açılış çökmeleri", "Çökme kaydı yok", "Bu kayıt artık yok.", "Kopyala"};
            case "ru": return new String[] {"Журналы сбоев", "Отчёты о сбоях, которые хранит система", "Сбои пользовательских приложений", "Сбои системных приложений", "Сбои системы и загрузки", "Нет журналов сбоев", "Этот журнал больше недоступен.", "Копировать"};
            case "es": return new String[] {"Registros de fallos", "Informes de fallos que guarda el sistema", "Fallos de apps de usuario", "Fallos de apps del sistema", "Fallos del sistema y del arranque", "No hay registros", "Este registro ya no existe.", "Copiar"};
            case "zh": return new String[] {"崩溃日志", "系统保存的崩溃报告", "用户应用崩溃", "系统应用崩溃", "系统与启动崩溃", "没有崩溃日志", "该日志已不存在。", "复制"};
            default: return new String[] {"Crash logs", "Crash reports kept by the system", "User app crashes", "System app crashes", "System and boot crashes", "No crash logs", "This log is no longer available.", "Copy"};
        }
    }

    private static final class Entry {
        final String tag;
        final long time;
        final String head;
        Entry(String tag, long time, String head) { this.tag = tag; this.time = time; this.head = head; }
    }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Runs on the main thread; a closed page or a failing dialog must not crash Security. */
    private static void onMain(Runnable r) {
        MAIN.post(() -> {
            try {
                if (r != null) r.run();
            } catch (Throwable ignored) {
            }
        });
    }

    private static void categories(Context ctx) {
        String[] w = words();
        // Reading the system's crash store can take a while: off the main thread.
        new Thread(() -> {
            String[][] tags = {USER, SYSTEM, BOOT};
            String[] items = new String[3];
            List<List<Entry>> lists = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                List<Entry> list;
                try { list = read(ctx, tags[i]); } catch (Throwable t) { list = new ArrayList<>(); }
                lists.add(list);
                items[i] = w[2 + i] + " (" + list.size() + ")";
            }
            onMain(() -> new AlertDialog.Builder(ctx)
                    .setTitle(w[0])
                    .setItems(items, (d, which) -> list(ctx, w[2 + which], lists.get(which)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
        }, "XSecurity crash logs").start();
    }

    private static List<Entry> read(Context ctx, String[] tags) {
        List<Entry> out = new ArrayList<>();
        DropBoxManager box = ctx.getSystemService(DropBoxManager.class);
        if (box == null) return out;
        for (String tag : tags) {
            long time = 0;
            DropBoxManager.Entry e;
            while ((e = box.getNextEntry(tag, time)) != null) {
                time = e.getTimeMillis();
                String head = e.getText(400);
                e.close();
                out.add(new Entry(tag, time, head == null ? "" : head));
            }
        }
        out.sort((a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    /** "Process: com.foo" or "Package: com.foo" from the report header, else the tag. */
    private static String who(Entry e) {
        for (String line : e.head.split("\n")) {
            if (line.startsWith("Process: ") || line.startsWith("Package: ")) return line.substring(9).split(" ")[0];
        }
        return e.tag;
    }

    private static void list(Context ctx, String title, List<Entry> entries) {
        String[] w = words();
        if (entries.isEmpty()) {
            Toast.makeText(ctx, w[5], Toast.LENGTH_SHORT).show();
            return;
        }
        DateFormat date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        String[] items = new String[entries.size()];
        for (int i = 0; i < items.length; i++) {
            Entry e = entries.get(i);
            items[i] = who(e) + "\n" + date.format(new Date(e.time)) + " · " + e.tag;
        }
        try {
            new AlertDialog.Builder(ctx)
                    .setTitle(title)
                    .setItems(items, (d, which) -> show(ctx, entries.get(which)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private static void show(Context ctx, Entry entry) {
        new Thread(() -> {
            String body = null;
            try {
                DropBoxManager box = ctx.getSystemService(DropBoxManager.class);
                DropBoxManager.Entry e = box == null ? null : box.getNextEntry(entry.tag, entry.time - 1);
                if (e != null && e.getTimeMillis() == entry.time) body = e.getText(256 * 1024);
                if (e != null) e.close();
            } catch (Throwable ignored) {
            }
            String found = body;
            onMain(() -> showBody(ctx, entry, found));
        }, "XSecurity crash log").start();
    }

    private static void showBody(Context ctx, Entry entry, String body) {
        String[] w = words();
        if (body == null) {
            Toast.makeText(ctx, w[6], Toast.LENGTH_SHORT).show();
            return;
        }
        String text = body;
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextIsSelectable(true);
        view.setTypeface(android.graphics.Typeface.MONOSPACE);
        view.setTextSize(11);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);
        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        scroll.addView(view);
        new AlertDialog.Builder(ctx)
                .setTitle(who(entry))
                .setView(scroll)
                .setPositiveButton(w[7], (d, x) -> {
                    try {
                        ctx.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(entry.tag, text));
                        Toast.makeText(ctx, "✓", Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
