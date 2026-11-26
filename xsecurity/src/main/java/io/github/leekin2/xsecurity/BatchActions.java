package io.github.leekin2.xsecurity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

/**
 * "Batch actions" in the Security app's App manager menu, as in kashi's Security Center mod:
 * pick several apps, then force stop, clear cache, disable or enable them together. Security
 * runs as the system user, so the hidden system calls work. Written anew.
 */
final class BatchActions {
    private static final int MENU_ID = 0x58A7C4;
    private final Hooks hooks;
    private final ClassLoader loader;

    BatchActions(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() throws Throwable {
        Class<?> main = Class.forName("com.miui.appmanager.AppManagerMainActivity", false, loader);
        hooks.install(main.getDeclaredMethod("onCreateOptionsMenu", Menu.class), chain -> {
            Object result = chain.proceed();
            Menu menu = (Menu) chain.getArg(0);
            if (menu != null && menu.findItem(MENU_ID) == null) menu.add(Menu.NONE, MENU_ID, 50, words()[0]);
            return result;
        });
        hooks.install(main.getDeclaredMethod("onOptionsItemSelected", MenuItem.class), chain -> {
            MenuItem item = (MenuItem) chain.getArg(0);
            if (item == null || item.getItemId() != MENU_ID) return chain.proceed();
            pick((Activity) chain.getThisObject());
            return true;
        });
    }

    /** Title, Next, selected count, the four actions, confirm, done. */
    private static String[] words() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return new String[] {"Toplu işlemler", "İleri", "%d uygulama seçildi", "Durmaya zorla", "Önbelleği temizle", "Devre dışı bırak", "Etkinleştir", "%d uygulamaya uygulansın mı?", "Tamam: %d / %d uygulama", "devre dışı"};
            case "ru": return new String[] {"Пакетные действия", "Далее", "Выбрано: %d", "Остановить", "Очистить кэш", "Отключить", "Включить", "Применить к %d приложениям?", "Готово: %d из %d", "отключено"};
            case "es": return new String[] {"Acciones en lote", "Siguiente", "%d apps seleccionadas", "Forzar detención", "Borrar caché", "Desactivar", "Activar", "¿Aplicar a %d apps?", "Hecho: %d de %d apps", "desactivada"};
            case "zh": return new String[] {"批量操作", "下一步", "已选择 %d 个应用", "强行停止", "清除缓存", "停用", "启用", "应用到 %d 个应用？", "完成：%d / %d 个应用", "已停用"};
            default: return new String[] {"Batch actions", "Next", "%d apps selected", "Force stop", "Clear cache", "Disable", "Enable", "Apply to %d apps?", "Done: %d of %d apps", "disabled"};
        }
    }

    private void pick(Activity activity) {
        String[] w = words();
        PackageManager pm = activity.getPackageManager();
        List<ApplicationInfo> apps = new ArrayList<>();
        // Only apps the user installed: no system app (updated or not), and never the current
        // home screen or keyboard, which would leave the phone without a launcher or a keyboard.
        java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(
                "android", "com.android.systemui", "com.google.android.gms", "com.android.vending", "com.android.phone"));
        try {
            android.content.pm.ResolveInfo home = pm.resolveActivity(new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            if (home != null && home.activityInfo != null) keep.add(home.activityInfo.packageName);
            String ime = android.provider.Settings.Secure.getString(activity.getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.contains("/")) keep.add(ime.substring(0, ime.indexOf('/')));
        } catch (Throwable ignored) { }
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (keep.contains(app.packageName)) continue;
            if (app.packageName.equals(activity.getPackageName())) continue;
            apps.add(app);
        }
        Collator collator = Collator.getInstance();
        List<String> labels = new ArrayList<>();
        for (ApplicationInfo app : apps) labels.add(String.valueOf(pm.getApplicationLabel(app)));
        Integer[] order = new Integer[apps.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> collator.compare(labels.get(a), labels.get(b)));
        String[] items = new String[order.length];
        for (int i = 0; i < order.length; i++) {
            ApplicationInfo app = apps.get(order[i]);
            items[i] = labels.get(order[i]) + (app.enabled ? "" : " (" + w[9] + ")");
        }
        boolean[] checked = new boolean[items.length];
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(w[0])
                .setMultiChoiceItems(items, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton(w[1], (d, which) -> {
                    List<String> chosen = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(apps.get(order[i]).packageName);
                    if (!chosen.isEmpty()) action(activity, chosen);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    private void action(Activity activity, List<String> chosen) {
        String[] w = words();
        String[] actions = {w[3], w[4], w[5], w[6]};
        new AlertDialog.Builder(activity)
                .setTitle(String.format(w[2], chosen.size()))
                .setItems(actions, (d, which) -> new AlertDialog.Builder(activity)
                        .setMessage(actions[which] + "\n" + String.format(w[7], chosen.size()))
                        .setPositiveButton(android.R.string.ok, (d2, x) -> run(activity, chosen, which))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void run(Activity activity, List<String> chosen, int which) {
        Context context = activity.getApplicationContext();
        new Thread(() -> {
            int ok = 0;
            for (String pkg : chosen) {
                try {
                    switch (which) {
                        case 0: forceStop(context, pkg); break;
                        case 1: clearCache(context, pkg); break;
                        case 2: context.getPackageManager().setApplicationEnabledSetting(pkg, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0); break;
                        default: context.getPackageManager().setApplicationEnabledSetting(pkg, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0); break;
                    }
                    ok++;
                } catch (Throwable t) {
                    hooks.error("batch action on " + pkg, t);
                }
            }
            int done = ok;
            activity.runOnUiThread(() -> Toast.makeText(activity, String.format(words()[8], done, chosen.size()), Toast.LENGTH_LONG).show());
        }).start();
    }

    private static void forceStop(Context context, String pkg) throws Exception {
        Object am = context.getSystemService(Context.ACTIVITY_SERVICE);
        Method stop = am.getClass().getMethod("forceStopPackage", String.class);
        stop.invoke(am, pkg);
    }

    private static void clearCache(Context context, String pkg) throws Exception {
        PackageManager pm = context.getPackageManager();
        Class<?> observer = Class.forName("android.content.pm.IPackageDataObserver");
        Method clear = pm.getClass().getMethod("deleteApplicationCacheFiles", String.class, observer);
        clear.invoke(pm, pkg, null);
    }
}
