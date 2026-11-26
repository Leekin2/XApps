package io.github.leekin2.xnotify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;

/**
 * HyperOS Settings › Notifications and Control centre gets two rows under its notification
 * section: the Super Island app picker and XNotify's other notification switches. Both open
 * XApps' own screens, where the choices are stored.
 */
final class SettingsEntry {
    private static final String TAG = "XNotify";
    private static final String PAGE = "com.android.settings.NotificationControlCenterSettings";
    private static final String[] AOD_PAGES = {
            "com.android.settings.AodAndLockScreenSettings",
            "com.android.settings.security.LockscreenDashboardFragment"
    };
    private static final String VALUE_PREF = "com.android.settingslib.miuisettings.preference.ValuePreference";
    private static final String XAPPS = "io.github.leekin2.xapps";
    private static final String KEY_ISLAND = "xapps_island_apps";
    private static final String KEY_MORE = "xapps_notification_extras";
    private static final String KEY_AOD = "xapps_aod_extras";
    private static final String KEY_KITS = "xapps_lock_kits";
    private static final String IME_UTILS = "com.android.settings.inputmethod.InputMethodFunctionSelectUtils";
    private static final String KEYBOARD_PAGE = "com.android.settings.language.MiuiLanguageAndInputSettings";

    private SettingsEntry() {
    }

    static void install(XposedInterface xposed, ClassLoader loader) throws Exception {
        Class<?> page = Class.forName(PAGE, false, loader);
        Method onCreate = method(page, "onCreate", Bundle.class);
        xposed.hook(onCreate).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object result = chain.proceed();
            // onCreate may be a shared superclass method, so act on this page only.
            Object self = chain.getThisObject();
            if (page.isInstance(self)) {
                try { addRows(self); } catch (Throwable t) { Log.w(TAG, "Settings rows not added", t); }
            }
            return result;
        });
        // The XAod row only while the XAod part runs; the keyboard rules only with XSecurity's
        // "Enhanced keyboard toolbar" switch on (both read from XApps' own settings).
        boolean aodPart = remoteBool(xposed, "xapps", "aod", true);
        boolean keyboard = remoteBool(xposed, "xapps", "security", true) && remoteBool(xposed, "xsecurity", "enhanced_keyboard", false);
        if (aodPart) for (String aodClass : AOD_PAGES) {
            try {
                Class<?> aodPage = Class.forName(aodClass, false, loader);
                Method aodCreate = method(aodPage, "onCreate", Bundle.class);
                xposed.hook(aodCreate).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    Object self = chain.getThisObject();
                    if (aodPage.isInstance(self)) {
                        try { addAodRow(self, loader); } catch (Throwable t) { Log.w(TAG, "AOD Settings row not added", t); }
                    }
                    return result;
                });
            } catch (Throwable t) {
                Log.w(TAG, "AOD Settings page unavailable: " + aodClass, t);
            }
        }
        if (keyboard) hookNativeKeyboardSettings(xposed, loader);
    }

    /**
     * Unlocks Xiaomi's OWN native "Full-screen keyboard optimization" (miui_bottom_manager),
     * keyboard theme sync, and privacy keyboard options inside Settings › Languages & input
     * for Gboard and every third-party keyboard — instead of redirecting out to XApps.
     */
    private static void hookNativeKeyboardSettings(XposedInterface xposed, ClassLoader loader) {
        try {
            Class<?> utils = Class.forName(IME_UTILS, false, loader);
            int hooked = 0;
            for (Method m : utils.getDeclaredMethods()) {
                if (m.getReturnType() != boolean.class) continue;
                String name = m.getName();
                if ("isMiuiImeBottomSupport".equals(name) || "isMiuiInputMethod".equals(name)) {
                    xposed.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> true);
                    hooked++;
                }
            }
            // Add current/third-party IMEs into sCustomIme so MiuiLanguageAndInputSettings enables all native rows.
            try {
                java.lang.reflect.Field f = utils.getDeclaredField("sCustomIme");
                f.setAccessible(true);
                Object list = f.get(null);
                if (list instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> custom = (java.util.List<String>) list;
                    if (!custom.contains("com.google.android.inputmethod.latin")) {
                        custom.add("com.google.android.inputmethod.latin");
                    }
                }
            } catch (Throwable ignored) { }
            try {
                Class<?> kbdPage = Class.forName(KEYBOARD_PAGE, false, loader);
                Method update = method(kbdPage, "updateInputMethodPreferenceViews");
                xposed.hook(update).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    try {
                        Object self = chain.getThisObject();
                        Context ctx = (Context) self.getClass().getMethod("getContext").invoke(self);
                        if (ctx != null) {
                            String cur = (String) utils.getMethod("getCurrentInputMethod", Context.class).invoke(null, ctx);
                            if (cur != null && !cur.isEmpty()) {
                                java.lang.reflect.Field f = utils.getDeclaredField("sCustomIme");
                                f.setAccessible(true);
                                Object list = f.get(null);
                                if (list instanceof java.util.List) {
                                    @SuppressWarnings("unchecked")
                                    java.util.List<String> custom = (java.util.List<String>) list;
                                    if (!custom.contains(cur)) custom.add(cur);
                                }
                            }
                        }
                    } catch (Throwable ignored) { }
                    return chain.proceed();
                });
            } catch (Throwable ignored) { }
            if (hooked > 0) Log.i(TAG, "HyperOS Settings: native enhanced keyboard options unlocked (" + hooked + ")");
        } catch (Throwable t) {
            Log.w(TAG, "HyperOS Settings: native keyboard unlock unavailable", t);
        }
    }

    private static boolean remoteBool(XposedInterface xposed, String group, String key, boolean byDefault) {
        try { return xposed.getRemotePreferences(group).getBoolean(key, byDefault); } catch (Throwable t) { return byDefault; }
    }

    private static void addAodRow(Object fragment, ClassLoader loader) throws Exception {
        Context context = (Context) fragment.getClass().getMethod("getContext").invoke(fragment);
        if (context == null) return;
        Method find = method(fragment.getClass(), "findPreference", CharSequence.class);
        if (find.invoke(fragment, KEY_AOD) != null) return;
        Object section = find.invoke(fragment, "lock_screen_display_category");
        if (section == null) section = find.invoke(fragment, "others_category");
        if (section == null) section = method(fragment.getClass(), "getPreferenceScreen").invoke(fragment);
        if (section == null) return;
        Class<?> rowType = Class.forName(VALUE_PREF, false, loader);
        String[] t = aodTexts(context);
        add(section, row(rowType, context, KEY_KITS, t[2], t[3], "io.github.leekin2.xsecurity.KitsActivity"));
        add(section, row(rowType, context, KEY_AOD, t[0], t[1], "io.github.leekin2.xsecurity.AodActivity"));
    }

    private static void addRows(Object fragment) throws Exception {
        Context context = (Context) fragment.getClass().getMethod("getContext").invoke(fragment);
        if (context == null) return;
        Method find = method(fragment.getClass(), "findPreference", CharSequence.class);
        Object section = find.invoke(fragment, "notification_settings");
        if (section == null) return;
        if (find.invoke(fragment, KEY_MORE) != null) return;
        // Same row type as the page's own "Notification animation" row, so it looks native.
        Object sample = find.invoke(fragment, "notification_light_effect");
        if (sample == null) sample = find.invoke(fragment, "notification_managing");
        if (sample == null) return;
        Class<?> rowType = sample.getClass();
        String[] t = texts(context);
        add(section, row(rowType, context, KEY_MORE, t[0], t[1], "io.github.leekin2.xnotify.SettingsActivity"));
    }

    private static Object row(Class<?> type, Context context, String key, String title, String summary, String activity) throws Exception {
        Object row = type.getConstructor(Context.class).newInstance(context);
        method(type, "setKey", String.class).invoke(row, key);
        method(type, "setTitle", CharSequence.class).invoke(row, title);
        method(type, "setSummary", CharSequence.class).invoke(row, summary);
        Intent intent = new Intent().setComponent(new ComponentName(XAPPS, activity))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        method(type, "setIntent", Intent.class).invoke(row, intent);
        return row;
    }

    private static void add(Object group, Object row) throws Exception {
        for (Method m : group.getClass().getMethods()) {
            if (m.getName().equals("addPreference") && m.getParameterCount() == 1) {
                m.invoke(group, row);
                return;
            }
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... params) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name);
    }

    /** Extras title and summary in the device language. */
    private static String[] texts(Context context) {
        switch (context.getResources().getConfiguration().getLocales().get(0).getLanguage()) {
            case "tr":
                return new String[] {"Bildirim eklentileri", "Parçacıklı temizleme, canlı şarj ve müzik adası (XApps)"};
            case "ru":
                return new String[] {"Дополнения уведомлений", "Очистка частицами, мощность зарядки и музыка на острове (XApps)"};
            case "es":
                return new String[] {"Extras de notificaciones", "Borrado con partículas, carga y música en la isla (XApps)"};
            case "zh":
                return new String[] {"通知扩展", "粒子清除动画、实时充电功率与音乐岛（XApps）"};
            default:
                return new String[] {"Notification extras", "Particle clear-all, live charging and music island (XApps)"};
        }
    }

    private static String[] aodTexts(Context context) {
        switch (context.getResources().getConfiguration().getLocales().get(0).getLanguage()) {
            case "tr":
                return new String[] {"Her zaman açık ekran eklentileri (XAod)", "Zamanlayıcı, pil sınırı, kenar ışığı, şarj bilgisi, çalan şarkı ve kitler (XApps)",
                        "Kilit ekranı kitleri", "Saatin altına tarih, hava, alarm, pil ve daha fazlasını ekleyin (XApps)"};
            case "ru":
                return new String[] {"Дополнения AOD (XAod)", "Таймер, порог заряда, подсветка краёв, зарядка, музыка и виджеты (XApps)",
                        "Виджеты экрана блокировки", "Дата, погода, будильник, батарея и другое под часами (XApps)"};
            case "es":
                return new String[] {"Extras de pantalla siempre activa (XAod)", "Temporizador, límite de batería, brillo de borde, carga, música y kits (XApps)",
                        "Kits de pantalla de bloqueo", "Fecha, tiempo, alarma, batería y más bajo el reloj (XApps)"};
            case "zh":
                return new String[] {"息屏显示扩展（XAod）", "定时关闭、电量下限、边缘光效、充电信息、正在播放与小组件（XApps）",
                        "锁屏小组件", "在时钟下方添加日期、天气、闹钟、电量等（XApps）"};
            default:
                return new String[] {"Always-on display extras (XAod)", "Timer, battery cutoff, edge glow, charging info, now playing and kits (XApps)",
                        "Lock screen kits", "Add date, weather, alarm, battery and more under the clock (XApps)"};
        }
    }
}
