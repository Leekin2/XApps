package io.github.leekin2.xsecurity;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Lock screen kits" editor: the chosen kits on a lock-screen-like preview, each with a remove
 * badge (tap a kit to move it one place earlier), and the "Add kit" grid below. Opened from the
 * XAod panel and from the lock screen editor; choices go to XAod's settings at once.
 */
public final class KitsActivity extends Activity {
    private SharedPreferences prefs;
    private List<String> order = new ArrayList<>();
    private LinearLayout preview, grid;
    private TextView slots;
    private float dp;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        io.github.libxposed.service.XposedService service = XSecurityApp.current();
        try { prefs = service == null ? null : service.getRemotePreferences(Features.GROUP); } catch (Throwable t) { prefs = null; }
        order = LockKits.stored(prefs);
        String[] t = texts();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = px(18);
        root.setPadding(pad, pad, pad, pad);

        root.addView(label(t[0], 26, true, 0xFFFFFFFF));
        TextView summary = label(t[1], 13, false, 0xB3FFFFFF);
        summary.setPadding(0, px(6), 0, px(14));
        root.addView(summary);
        if (prefs == null) {
            TextView none = label(t[12], 13, false, 0xFFFF8A80);
            none.setPadding(0, 0, 0, px(12));
            root.addView(none);
            // Opened from Settings, the process just started and LSPosed binds a moment later:
            // reopen as soon as it has.
            // Reopen once, and only when the settings really open now (at most 5 s of tries).
            Runnable[] wait = new Runnable[1];
            int[] tries = {0};
            wait[0] = () -> {
                if (isFinishing()) return;
                android.content.SharedPreferences ready = null;
                io.github.libxposed.service.XposedService now = XSecurityApp.current();
                try { if (now != null) ready = now.getRemotePreferences(Features.GROUP); } catch (Throwable ignored) { }
                if (ready != null) recreate();
                else if (++tries[0] < 20) root.postDelayed(wait[0], 250);
            };
            root.postDelayed(wait[0], 250);
        }

        // The feature's own switch, so this page works also when opened from HyperOS Settings.
        String[] m = master();
        root.addView(toggle(m[0], m[1], Features.AOD_KITS));
        TextView hint = label(m[2], 12, false, 0x99FFFFFF);
        hint.setPadding(0, 0, 0, px(6));
        root.addView(hint);
        android.widget.Button restart = new android.widget.Button(this);
        restart.setText(m[3]);
        restart.setAllCaps(false);
        restart.setOnClickListener(v -> restartSystemUi(restart, m[4]));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = px(14);
        root.addView(restart, rp);

        // A lock-screen-like card behind the kits, so the glass looks as it will on the phone.
        FrameLayout card = new FrameLayout(this);
        GradientDrawable wall = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFFE8774A, 0xFFB8336A, 0xFF4B2C7A});
        wall.setCornerRadius(px(26));
        card.setBackground(wall);
        card.setPadding(px(10), px(22), px(10), px(22));
        preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        slots = label("", 12, false, 0xB3FFFFFF);
        slots.setPadding(0, px(10), 0, px(18));
        root.addView(slots);

        root.addView(label(t[2], 18, true, 0xFFFFFFFF));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, px(12), 0, px(12));
        root.addView(grid);

        root.addView(toggle(t[3], t[4], Features.AOD_KITS_ON_AOD));
        root.addView(toggle(t[5], t[6], Features.AOD_KITS_HIDE_NOTIF));
        root.addView(shapePicker());
        TextView layout = label("", 16, false, 0xFFFFFFFF);
        layout.setPadding(0, px(12), 0, px(12));
        if (prefs != null) LockKits.grid = LockKits.Grid.read(prefs.getString(Features.AOD_KITS_GRID, null));
        layout.setText(LockKits.gridTitle() + "  " + LockKits.grid.label);
        layout.setOnClickListener(v -> {
            if (prefs == null) return;
            LockKits.chooseGrid(this, order, selected -> {
                prefs.edit().putString(Features.AOD_KITS_GRID, selected.name()).apply();
                LockKits.grid = selected;
                layout.setText(LockKits.gridTitle() + "  " + selected.label);
                tellSystemUi();
                refresh();
            });
        });
        root.addView(layout);
        TextView note = label(t[11], 12, false, 0x99FFFFFF);
        note.setPadding(0, px(14), 0, 0);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(0xFF111114);
        scroll.addView(root);
        setContentView(scroll);
        getWindow().setStatusBarColor(0xFF111114);
        getWindow().setNavigationBarColor(0xFF111114);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The lock screen editor may have changed the kits meanwhile (saved in this process).
        if (prefs != null && preview != null) {
            List<String> now = LockKits.stored(prefs);
            if (!now.equals(order)) {
                order = now;
                refresh();
            }
        }
    }

    /** Applies the kits switch: System UI loads the lock screen kits when it starts. */
    private void restartSystemUi(android.widget.Button button, String noRoot) {
        button.setEnabled(false);
        new Thread(() -> {
            boolean ok = false;
            try {
                Process p = new ProcessBuilder("su", "-c", "id -u; killall com.android.systemui; true").redirectErrorStream(true).start();
                byte[] out = p.getInputStream().readAllBytes();
                ok = p.waitFor() == 0 && new String(out).trim().startsWith("0");
            } catch (Throwable ignored) { }
            boolean done = ok;
            runOnUiThread(() -> {
                button.setEnabled(true);
                if (!done) android.widget.Toast.makeText(this, noRoot, android.widget.Toast.LENGTH_SHORT).show();
            });
        }, "XApps restart System UI").start();
    }

    /** Switch title, its summary, restart hint, restart button, root needed. */
    private static String[] master() {
        switch (lang()) {
            case "tr": return new String[] {"Kilit ekranı kitlerini göster", "Saatin altında cam göstergeler; kilit ekranı düzenleyicisinde de düzenlenir",
                    "Anahtarı değiştirdikten sonra Sistem Arayüzünü yeniden başlatın.", "Sistem Arayüzünü yeniden başlat", "Root izni gerekli"};
            case "ru": return new String[] {"Показывать виджеты экрана блокировки", "Стеклянные индикаторы под часами; их можно менять и в редакторе экрана блокировки",
                    "После переключения перезапустите System UI.", "Перезапустить System UI", "Нужен root-доступ"};
            case "es": return new String[] {"Mostrar kits en la pantalla de bloqueo", "Indicadores de cristal bajo el reloj; también se editan en el editor de la pantalla de bloqueo",
                    "Tras cambiar el interruptor, reinicia System UI.", "Reiniciar System UI", "Se necesita acceso root"};
            case "zh": return new String[] {"显示锁屏小组件", "时钟下方的玻璃仪表；也可在锁屏编辑器中编辑",
                    "切换开关后请重启系统界面。", "重启系统界面", "需要 Root 权限"};
            default: return new String[] {"Show lock screen kits", "Glass gauges under the clock; also edited in the lock screen editor",
                    "After changing the switch, restart System UI.", "Restart System UI", "Root access required"};
        }
    }

    private void refresh() {
        String[] t = texts();
        preview.removeAllViews();
        int gap = px(10);
        int size = px(64);
        int index = 0;
        for (List<String> ids : LockKits.rows(order)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                final int at = index++;
                FrameLayout cell = new FrameLayout(this);
                LockKits.Kit kit = new LockKits.Kit(this, id);
                cell.addView(kit, new FrameLayout.LayoutParams(LockKits.wide(id) ? size * 2 + gap : size, size, Gravity.CENTER));
                cell.setPadding(px(6), px(6), px(6), px(6));
                cell.setOnClickListener(v -> move(at));
                TextView minus = new TextView(this);
                minus.setText("−");
                minus.setGravity(Gravity.CENTER);
                minus.setTextColor(0xFF000000);
                minus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(0xFFFFFFFF);
                minus.setBackground(dot);
                minus.setContentDescription(t[9]);
                minus.setOnClickListener(v -> remove(at));
                cell.addView(minus, new FrameLayout.LayoutParams(px(22), px(22), Gravity.TOP | Gravity.END));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                row.addView(cell, lp);
            }
            preview.addView(row);
        }
        if (order.isEmpty()) {
            TextView empty = label(t[10], 14, false, 0xE6FFFFFF);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, px(20), 0, px(20));
            preview.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        fillValues(preview);
        slots.setText(String.format(Locale.getDefault(), t[7], LockKits.usedSlots(order), LockKits.grid.columns * LockKits.grid.maxRows));

        grid.removeAllViews();
        LinearLayout row = null;
        int used = 0;
        for (String id : LockKits.ALL) {
            int need = LockKits.slots(id);
            if (row == null || used + need > LockKits.SLOTS_PER_ROW) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = px(12);
                grid.addView(row, rp);
                used = 0;
            }
            used += need;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            LockKits.Kit kit = new LockKits.Kit(this, id);
            item.addView(kit, new LinearLayout.LayoutParams(LockKits.wide(id) ? size * 2 + gap : size, size));
            TextView name = label(name(id), 11, false, 0xCCFFFFFF);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, px(4), 0, 0);
            item.addView(name, new LinearLayout.LayoutParams(LockKits.wide(id) ? size * 2 + gap : px(76), ViewGroup.LayoutParams.WRAP_CONTENT));
            boolean added = order.contains(id);
            item.setAlpha(added ? 0.35f : 1f);
            item.setOnClickListener(v -> add(id));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.leftMargin = px(4);
            ip.rightMargin = px(4);
            row.addView(item, ip);
        }
        fillValues(grid);
    }

    /** Live values for every kit view under {@code group} (samples where there is none). */
    private void fillValues(ViewGroup group) {
        List<LockKits.Kit> kits = new ArrayList<>();
        collect(group, kits);
        LockKits.fillKits(this, kits, true);
    }

    private static void collect(View v, List<LockKits.Kit> out) {
        if (v instanceof LockKits.Kit) { out.add((LockKits.Kit) v); return; }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    private void add(String id) {
        String[] t = texts();
        if (order.contains(id)) {
            Toast.makeText(this, t[13], Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LockKits.fits(order, id)) {
            Toast.makeText(this, t[8], Toast.LENGTH_SHORT).show();
            return;
        }
        order.add(id);
        save();
    }

    private void remove(int at) {
        if (at < 0 || at >= order.size()) return;
        order.remove(at);
        save();
    }

    private void move(int at) {
        if (at <= 0 || at >= order.size()) return;
        String id = order.remove(at);
        order.add(at - 1, id);
        // A wide kit moved into a full row would drop the last kit: keep the old order then.
        int before = 0;
        for (List<String> r : LockKits.rows(order)) before += r.size();
        if (before != order.size()) {
            order.remove(at - 1);
            order.add(at, id);
            return;
        }
        save();
    }

    private void save() {
        if (prefs != null) prefs.edit().putStringSet(Features.AOD_KIT_LIST, LockKits.encode(order)).apply();
        tellSystemUi();
        refresh();
    }

    /** System UI keeps the settings it started with: send it the new choice right away. */
    private void tellSystemUi() {
        if (prefs == null) return;
        try {
            sendBroadcast(new android.content.Intent(AodOverlay.ACTION_KITS).setPackage("com.android.systemui")
                    .putExtra("order", String.join(",", order))
                    .putExtra("on_aod", prefs.getBoolean(Features.AOD_KITS_ON_AOD, false))
                    .putExtra("hide_notif", prefs.getBoolean(Features.AOD_KITS_HIDE_NOTIF, false))
                    .putExtra("y", prefs.getInt(Features.AOD_KITS_Y, 0))
                    .putExtra("enabled", prefs.getBoolean(Features.AOD_KITS, false))
                    .putExtra("grid", prefs.getString(Features.AOD_KITS_GRID, LockKits.Grid.WIDE.name()))
                    .putExtra("square", prefs.getBoolean(Features.AOD_KITS_SQUARE, false)));
        } catch (Throwable ignored) { }
    }

    /** kashi's "Kit shape": pill & circle, or rounded square & rectangle. */
    private View shapePicker() {
        String[] s = shapeTexts();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, px(10), 0, px(10));
        box.addView(label(s[0], 16, false, 0xFFFFFFFF));
        LinearLayout choices = new LinearLayout(this);
        choices.setPadding(0, px(8), 0, 0);
        boolean squareNow = prefs != null && prefs.getBoolean(Features.AOD_KITS_SQUARE, false);
        TextView[] options = new TextView[2];
        for (int i = 0; i < 2; i++) {
            boolean square = i == 1;
            TextView option = label(s[1 + i], 14, false, 0xFFFFFFFF);
            option.setGravity(Gravity.CENTER);
            option.setPadding(px(10), px(10), px(10), px(10));
            options[i] = option;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i == 1) lp.leftMargin = px(8);
            choices.addView(option, lp);
            option.setOnClickListener(v -> {
                if (prefs == null) return;
                prefs.edit().putBoolean(Features.AOD_KITS_SQUARE, square).commit();
                LockKits.square = square;
                paintShape(options, square);
                refresh();
                tellSystemUi();
            });
        }
        paintShape(options, squareNow);
        LockKits.square = squareNow;
        box.addView(choices);
        return box;
    }

    private void paintShape(TextView[] options, boolean square) {
        for (int i = 0; i < options.length; i++) {
            boolean on = (i == 1) == square;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(14));
            bg.setColor(on ? 0xFF2F6BFF : 0x22FFFFFF);
            options[i].setBackground(bg);
            options[i].setAlpha(prefs == null ? 0.5f : 1f);
        }
    }

    private static String[] shapeTexts() {
        switch (lang()) {
            case "tr": return new String[] {"Kit şekli", "Hap ve daire", "Yuvarlak kare ve dikdörtgen"};
            case "ru": return new String[] {"Форма виджетов", "Капсула и круг", "Скруглённый квадрат и прямоугольник"};
            case "es": return new String[] {"Forma de los kits", "Pastilla y círculo", "Cuadrado redondeado y rectángulo"};
            case "zh": return new String[] {"小组件形状", "胶囊与圆形", "圆角方形与矩形"};
            default: return new String[] {"Kit shape", "Pill & circle", "Rounded square & rectangle"};
        }
    }

    private View toggle(String title, String summary, String key) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, px(10), 0, px(10));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(title, 16, false, 0xFFFFFFFF));
        texts.addView(label(summary, 12, false, 0x99FFFFFF));
        box.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        sw.setChecked(prefs != null && prefs.getBoolean(key, false));
        sw.setEnabled(prefs != null);
        sw.setOnCheckedChangeListener((v, on) -> {
            if (prefs == null) return;
            prefs.edit().putBoolean(key, on).commit();
            tellSystemUi();
        });
        box.addView(sw);
        return box;
    }

    private TextView label(String value, int sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        return v;
    }

    private int px(int value) {
        return Math.round(value * dp);
    }

    private static String lang() {
        return Locale.getDefault().getLanguage();
    }

    static String name(String id) {
        String l = lang();
        // In LockKits.ALL's order.
        String[] tr = {"Adımlar", "Sıcaklık", "Alarm", "Pil", "Gün doğumu/batımı", "UV", "Hava kalitesi", "Takvim",
                "Zamanlayıcı", "Kronometre", "El feneri", "Rahatsız etmeyin", "Hesap makinesi", "Notlar", "Ses kaydedici", "Arama",
                "Cihaz pili", "Kulaklık", "Bugünün havası", "Etkinlik"};
        String[] en = {"Steps", "Temperature", "Alarm", "Battery", "Sunrise & sunset", "UV", "Air quality", "Calendar",
                "Timer", "Stopwatch", "Flashlight", "Do not disturb", "Calculator", "Notes", "Recorder", "Call",
                "Device battery", "Buds", "Today's weather", "Event"};
        String[] ru = {"Шаги", "Температура", "Будильник", "Батарея", "Восход и закат", "УФ", "Качество воздуха", "Календарь",
                "Таймер", "Секундомер", "Фонарик", "Не беспокоить", "Калькулятор", "Заметки", "Диктофон", "Вызов",
                "Батарея устройства", "Наушники", "Погода на сегодня", "Событие"};
        String[] es = {"Pasos", "Temperatura", "Alarma", "Batería", "Amanecer y atardecer", "UV", "Calidad del aire", "Calendario",
                "Temporizador", "Cronómetro", "Linterna", "No molestar", "Calculadora", "Notas", "Grabadora", "Llamar",
                "Batería del dispositivo", "Auriculares", "El tiempo de hoy", "Evento"};
        String[] zh = {"步数", "温度", "闹钟", "电量", "日出日落", "紫外线", "空气质量", "日历",
                "计时器", "秒表", "手电筒", "勿扰", "计算器", "笔记", "录音机", "通话",
                "设备电量", "耳机", "今日天气", "日程"};
        String[] names = "tr".equals(l) ? tr : "ru".equals(l) ? ru : "es".equals(l) ? es : "zh".equals(l) ? zh : en;
        for (int i = 0; i < LockKits.ALL.length; i++) if (LockKits.ALL[i].equals(id)) return names[i];
        return id;
    }

    /** title, summary, add, aod, aod summary, hide, hide summary, slots, no space, remove, empty, note, no service, added. */
    private static String[] texts() {
        switch (lang()) {
            case "tr": return new String[] {"Kilit ekranı kitleri", "Saatin altında cam göstergeler. Bir kite dokunarak bir öne alın, (−) ile kaldırın.", "Kit ekle",
                    "AOD'de de göster", "Kitler her zaman açık ekranda saatin altında da görünür", "Bildirim varken gizle", "Kilit ekranında bildirim olduğunda kitler gizlenir",
                    "%1$d / %2$d yuva kullanılıyor", "Yer kalmadı, önce bir kit kaldırın", "Kaldır", "Kit yok. Aşağıdan ekleyin.",
                    "Değişiklikler kilit ekranında hemen görünür. Kilit ekranına uzun basıp Özelleştir'den de düzenleyebilirsiniz.", "XApps LSPosed'da etkin değil; seçimler kaydedilemez.", "Bu kit zaten ekli"};
            case "ru": return new String[] {"Виджеты экрана блокировки", "Стеклянные индикаторы под часами. Нажмите на виджет, чтобы сдвинуть его вперёд, (−) — удалить.", "Добавить виджет",
                    "Показывать на AOD", "Виджеты видны и под часами AOD", "Скрывать при уведомлениях", "Виджеты скрываются, когда на экране блокировки есть уведомления",
                    "Занято слотов: %1$d из %2$d", "Нет места, сначала удалите виджет", "Удалить", "Нет виджетов. Добавьте ниже.",
                    "Изменения сразу видны на экране блокировки. Их также можно менять: удерживайте экран блокировки и откройте «Настроить».", "XApps не активен в LSPosed; выбор не сохранится.", "Этот виджет уже добавлен"};
            case "es": return new String[] {"Kits de la pantalla de bloqueo", "Indicadores de cristal bajo el reloj. Toca un kit para adelantarlo, (−) para quitarlo.", "Añadir kit",
                    "Mostrar también en AOD", "Los kits se ven también bajo el reloj de la pantalla siempre activa", "Ocultar con notificaciones", "Los kits se ocultan cuando hay notificaciones en la pantalla de bloqueo",
                    "%1$d de %2$d espacios usados", "No hay espacio, quita un kit primero", "Quitar", "No hay kits. Añádelos abajo.",
                    "Los cambios se ven al momento en la pantalla de bloqueo. También puedes editarlos manteniendo pulsada la pantalla de bloqueo y abriendo Personalizar.", "XApps no está activo en LSPosed; no se guardará.", "Este kit ya está añadido"};
            case "zh": return new String[] {"锁屏小组件", "时钟下方的玻璃仪表。点按小组件可前移，点 (−) 移除。", "添加小组件",
                    "息屏也显示", "小组件也显示在息屏时钟下方", "有通知时隐藏", "锁屏有通知时隐藏小组件",
                    "已用 %1$d / %2$d 个位置", "没有空间了，请先移除一个", "移除", "没有小组件，请在下方添加。",
                    "更改会立即显示在锁屏上。也可长按锁屏并进入自定义进行编辑。", "XApps 未在 LSPosed 中启用，无法保存。", "已添加此小组件"};
            default: return new String[] {"Lock screen kits", "Glass gauges under the clock. Tap a kit to move it one place earlier, (−) to remove it.", "Add kit",
                    "Show on AOD too", "The kits also show under the always-on display clock", "Hide while notifications show", "The kits hide when the lock screen has notifications",
                    "%1$d of %2$d slots used", "No space left, remove a kit first", "Remove", "No kits. Add some below.",
                    "Changes show on the lock screen right away. You can also edit them: long-press the lock screen and open Customize.", "XApps is not active in LSPosed; choices cannot be saved.", "This kit is already added"};
        }
    }
}
