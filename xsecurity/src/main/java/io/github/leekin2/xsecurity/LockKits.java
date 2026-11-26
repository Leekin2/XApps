package io.github.leekin2.xsecurity;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lock screen kits: glass gauges under the lock screen clock (and, if chosen, on the AOD).
 * Round kits take one slot, wide kits (device battery, earbuds) take two; a row has four slots
 * and there are two rows. The order is kept in XAod's settings as "index:id" entries.
 * Written for XApps; the idea and the kit list follow kashi's HyperOS AOD mod.
 */
final class LockKits {
    static final String DATE = "date", TEMP = "temp", ALARM = "alarm", BATTERY = "battery",
            SUN = "sun", UV = "uv", AQI = "aqi", STEPS = "steps", DEVICE = "device", BUDS = "buds",
            // The kits kashi added later: shortcuts and switches, today's weather and the next event.
            TIMER = "timer", STOPWATCH = "stopwatch", TORCH = "torch", DND = "dnd", CALC = "calculator",
            NOTES = "notes", RECORDER = "recorder", CALL = "call", WEATHER = "weather", EVENT = "event";
    /** Every kit, in the order the "Add kit" sheet shows them (round ones, then wide ones). */
    static final String[] ALL = {STEPS, TEMP, ALARM, BATTERY, SUN, UV, AQI, DATE,
            TIMER, STOPWATCH, TORCH, DND, CALC, NOTES, RECORDER, CALL, DEVICE, BUDS, WEATHER, EVENT};
    static final String[] EDITOR_CATALOG = {STEPS, TEMP, ALARM, BATTERY, SUN, UV, AQI, DATE,
            DEVICE, BUDS, TIMER, STOPWATCH, TORCH, DND, CALC, NOTES, RECORDER, CALL, WEATHER, EVENT};
    static final int SLOTS_PER_ROW = 4;

    enum Grid {
        WIDE("4×2", 4, 2), STRIP("4×1", 4, 1), COMPACT("2×2", 2, 2);
        final String label;
        final int columns, maxRows;

        Grid(String label, int columns, int maxRows) {
            this.label = label;
            this.columns = columns;
            this.maxRows = maxRows;
        }

        static Grid read(String value) {
            for (Grid grid : values()) if (grid.name().equals(value)) return grid;
            return WIDE;
        }
    }

    static volatile Grid grid = Grid.WIDE;

    static String gridTitle() {
        return switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr" -> "Kit yerleşimi";
            case "ru" -> "Расположение виджетов";
            case "es" -> "Distribución de kits";
            case "zh" -> "小组件布局";
            default -> "Kit layout";
        };
    }

    static void chooseGrid(Context context, List<String> order, java.util.function.Consumer<Grid> select) {
        Grid[] options = Grid.values();
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = options[i].label;
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle(gridTitle())
                .setSingleChoiceItems(labels, grid.ordinal(), (d, index) -> {
                    Grid selected = options[index];
                    if (!fitsGrid(order, selected)) {
                        android.widget.Toast.makeText(context, gridFullText(), android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    select.accept(selected);
                    d.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).create();
        dialog.show();
    }

    private static String gridFullText() {
        return switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr" -> "Bu yerleşim için önce bir veya daha fazla kit kaldırın";
            case "ru" -> "Удалите один или несколько виджетов для этого размещения";
            case "es" -> "Quita uno o más kits para usar esta distribución";
            case "zh" -> "请先移除一个或多个小组件";
            default -> "Remove one or more kits to use this layout";
        };
    }
    static final List<String> DEFAULT = List.of(DATE, TEMP, ALARM, BATTERY);

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    /** Rounded square & rectangle instead of pill & circle (kashi's "Kit shape"). */
    static volatile boolean square;

    /** "%80" in Turkish, "80 %" in Russian and Spanish, "80%" elsewhere. */
    static String percent(int value) {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return "%" + value;
            case "ru": case "es": return value + " %";
            default: return value + "%";
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LockKits() {
    }

    static boolean wide(String id) {
        return DEVICE.equals(id) || BUDS.equals(id) || WEATHER.equals(id) || EVENT.equals(id);
    }

    /** Kits that are a button (open an app, or switch something) rather than a gauge. */
    static boolean action(String id) {
        return TIMER.equals(id) || STOPWATCH.equals(id) || TORCH.equals(id) || DND.equals(id) || CALC.equals(id)
                || NOTES.equals(id) || RECORDER.equals(id) || CALL.equals(id);
    }

    static int slots(String id) {
        return wide(id) ? 2 : 1;
    }

    // --- Stored order -------------------------------------------------------------------------

    /** The chosen kits in order; the default set when nothing was chosen yet. */
    static List<String> order(Set<String> stored) {
        if (stored == null || stored.isEmpty()) return new ArrayList<>(DEFAULT);
        TreeMap<Integer, String> sorted = new TreeMap<>();
        for (String entry : stored) {
            int colon = entry.indexOf(':');
            if (colon <= 0) continue;
            try {
                String id = entry.substring(colon + 1);
                if (known(id)) sorted.put(Integer.parseInt(entry.substring(0, colon)), id);
            } catch (NumberFormatException ignored) { }
        }
        List<String> list = new ArrayList<>(sorted.values());
        // "none" keeps an emptied list empty instead of falling back to the default.
        return list;
    }

    static Set<String> encode(List<String> order) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < order.size(); i++) set.add(i + ":" + order.get(i));
        if (set.isEmpty()) set.add("0:none");
        return set;
    }

    static boolean known(String id) {
        for (String k : ALL) if (k.equals(id)) return true;
        return false;
    }

    /** Splits the kits into rows of four slots; kits that do not fit are left out. */
    static List<List<String>> rows(List<String> order) { return rows(order, grid); }

    private static List<List<String>> rows(List<String> order, Grid layout) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        int used = 0;
        for (String id : order) {
            int need = slots(id);
            if (used + need > layout.columns) {
                if (!row.isEmpty()) rows.add(row);
                if (rows.size() == layout.maxRows) return rows;
                row = new ArrayList<>();
                used = 0;
            }
            row.add(id);
            used += need;
        }
        if (!row.isEmpty() && rows.size() < layout.maxRows) rows.add(row);
        return rows;
    }

    static int usedSlots(List<String> order) {
        int used = 0;
        for (List<String> row : rows(order)) for (String id : row) used += slots(id);
        return used;
    }

    /** Whether one more kit of this kind fits (in the rows as they would be laid out). */
    static boolean fits(List<String> order, String id) {
        List<String> more = new ArrayList<>(order);
        more.add(id);
        return fitsGrid(more, grid);
    }

    static boolean fitsGrid(List<String> order, Grid layout) {
        int count = 0;
        for (List<String> row : rows(order, layout)) count += row.size();
        return count == order.size();
    }

    // --- Views -------------------------------------------------------------------------------

    /** Builds the kit rows into {@code into} and fills them with live values. */
    static void build(Context context, LinearLayout into, List<String> order, boolean preview) {
        into.removeAllViews();
        into.setOrientation(LinearLayout.VERTICAL);
        into.setGravity(Gravity.CENTER_HORIZONTAL);
        float dp = context.getResources().getDisplayMetrics().density;
        int size = Math.round(64 * dp), gap = Math.round(12 * dp);
        List<Kit> kits = new ArrayList<>();
        for (List<String> ids : rows(order)) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                Kit kit = new Kit(context, id);
                kits.add(kit);
                // On the lock screen a tap opens or switches what the kit shows.
                if (!preview) kit.setOnClickListener(v -> tap(v, id));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wide(id) ? size * 2 + gap : size, size);
                if (i > 0) lp.leftMargin = gap;
                row.addView(kit, lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (into.getChildCount() > 0) rp.topMargin = gap;
            into.addView(row, rp);
        }
        fill(context.getApplicationContext() != null ? context.getApplicationContext() : context, kits, preview);
    }

    /** Fills already placed kit views with live values (samples in the editor preview). */
    static void fillKits(Context context, List<Kit> kits, boolean preview) {
        fill(context.getApplicationContext() != null ? context.getApplicationContext() : context, kits, preview);
    }

    private static void fill(Context context, List<Kit> kits, boolean preview) {
        if (kits.isEmpty()) return;
        WORKER.execute(() -> {
            Weather weather = null;
            List<Value> values = new ArrayList<>();
            for (Kit kit : kits) {
                if (weather == null && (TEMP.equals(kit.id) || SUN.equals(kit.id) || UV.equals(kit.id) || AQI.equals(kit.id) || WEATHER.equals(kit.id))) {
                    weather = Weather.read(context);
                }
                Value v;
                try { v = value(context, kit.id, weather); } catch (Throwable t) { v = null; }
                if (v == null && preview) v = sample(kit.id);
                values.add(v);
            }
            MAIN.post(() -> {
                for (int i = 0; i < kits.size(); i++) kits.get(i).set(values.get(i));
            });
        });
    }

    /** What a kit shows: ring fill (0..1), the big value, a small line and the label for wide kits. */
    static final class Value {
        final float progress;
        final String value, sub, label;

        Value(float progress, String value, String sub, String label) {
            this.progress = Math.max(0f, Math.min(1f, progress));
            this.value = value;
            this.sub = sub;
            this.label = label;
        }
    }

    private static Value value(Context context, String id, Weather w) {
        switch (id) {
            case DATE: {
                Calendar c = Calendar.getInstance();
                String day = c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
                float month = c.get(Calendar.DAY_OF_MONTH) / (float) c.getActualMaximum(Calendar.DAY_OF_MONTH);
                return new Value(month, String.valueOf(c.get(Calendar.DAY_OF_MONTH)), day == null ? "" : day.toUpperCase(Locale.getDefault()), null);
            }
            case ALARM: {
                AlarmManager.AlarmClockInfo next = context.getSystemService(AlarmManager.class).getNextAlarmClock();
                if (next == null) return new Value(0f, "--:--", "", null);
                long left = next.getTriggerTime() - System.currentTimeMillis();
                float p = 1f - Math.min(1f, left / (float) (24 * 3_600_000L));
                return new Value(p, DateFormat.getTimeFormat(context).format(next.getTriggerTime()), "", null);
            }
            case BATTERY:
            case DEVICE: {
                Intent b = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int level = b == null ? -1 : b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                if (level < 0) return null;
                String name = DEVICE.equals(id) ? deviceName(context) : null;
                return new Value(level / 100f, percent(level), "", name);
            }
            case TEMP: {
                if (w == null || w.temp == null) return null;
                float p = w.high > w.low ? (w.tempValue - w.low) / (float) (w.high - w.low) : 0.5f;
                return new Value(p, w.temp, w.low + "  " + w.high, null);
            }
            case UV: {
                if (w == null || w.uv < 0) return null;
                return new Value(w.uv / 11f, String.valueOf(w.uv), "UV", null);
            }
            case AQI: {
                if (w == null || w.aqi < 0) return new Value(0f, "--", "AQI", null);
                return new Value(w.aqi / 300f, String.valueOf(w.aqi), "AQI", null);
            }
            case SUN: {
                if (w == null || w.sunrise < 0 || w.sunset < 0) return null;
                long now = System.currentTimeMillis();
                long rise = todayAt(w.sunrise), set = todayAt(w.sunset);
                boolean day = now >= rise && now < set;
                long next = day ? set : (now < rise ? rise : rise + 86_400_000L);
                float p = day ? (now - rise) / (float) Math.max(1, set - rise) : 0f;
                return new Value(p, DateFormat.getTimeFormat(context).format(next), day ? "sunset" : "sunrise", null);
            }
            case STEPS: {
                String steps = steps(context);
                if (steps == null) return null;
                int n = 0;
                try { n = Integer.parseInt(steps.replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) { }
                return new Value(n / 10000f, steps, "", null);
            }
            case BUDS:
                return buds();
            case WEATHER: {
                if (w == null || w.temp == null) return new Value(0f, "--", "", word(context, 2));
                float p = w.high > w.low ? (w.tempValue - w.low) / (float) (w.high - w.low) : 0.5f;
                String label = TextUtils.isEmpty(w.description) ? w.low + "° / " + w.high + "°" : w.description;
                return new Value(p, w.temp, "", label);
            }
            case EVENT:
                return nextEvent(context);
            case TORCH:
                watchTorch(context);
                return new Value(torchOn ? 1f : 0f, "", word(context, torchOn ? 0 : 1), null);
            case DND: {
                android.app.NotificationManager nm = context.getSystemService(android.app.NotificationManager.class);
                boolean on = nm != null && nm.getCurrentInterruptionFilter() > android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
                return new Value(on ? 1f : 0f, "", word(context, on ? 0 : 1), null);
            }
            default:
                // Shortcut kits: just the glyph.
                return action(id) ? new Value(0f, "", "", null) : null;
        }
    }

    // --- Taps on the lock screen ---------------------------------------------------------------

    private static volatile boolean torchOn;
    private static volatile String torchCamera;
    private static boolean torchWatched;

    private static void watchTorch(Context context) {
        if (torchWatched) return;
        torchWatched = true;
        try {
            android.hardware.camera2.CameraManager cm = context.getSystemService(android.hardware.camera2.CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) { torchCamera = id; break; }
            }
            cm.registerTorchCallback(new android.hardware.camera2.CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String cameraId, boolean enabled) {
                    if (cameraId.equals(torchCamera)) torchOn = enabled;
                }
            }, MAIN);
        } catch (Throwable ignored) { }
    }

    /** What a tap on a lock screen kit does. Runs inside System UI. */
    static void tap(View kit, String id) {
        Context context = kit.getContext();
        try {
            kit.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            switch (id) {
                case TORCH: {
                    watchTorch(context);
                    if (torchCamera == null) return;
                    context.getSystemService(android.hardware.camera2.CameraManager.class).setTorchMode(torchCamera, !torchOn);
                    torchOn = !torchOn;
                    refill(kit);
                    return;
                }
                case DND: {
                    android.app.NotificationManager nm = context.getSystemService(android.app.NotificationManager.class);
                    boolean on = nm.getCurrentInterruptionFilter() > android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
                    // System UI may set the zen mode itself (as its own DND tile does).
                    android.app.NotificationManager.class.getMethod("setZenMode", int.class, Uri.class, String.class)
                            .invoke(nm, on ? 0 : 1, null, "XApps lock screen kit");
                    MAIN.postDelayed(() -> refill(kit), 250);
                    return;
                }
                default:
                    break;
            }
            Intent intent = intentFor(context, id);
            if (intent != null) startDismissingKeyguard(context, intent);
        } catch (Throwable t) {
            android.util.Log.w("XAod", "Kit tap failed: " + id, t);
        }
    }

    private static void refill(View kit) {
        if (kit instanceof Kit) {
            List<Kit> one = new ArrayList<>();
            one.add((Kit) kit);
            fillKits(kit.getContext(), one, false);
        }
    }

    private static Intent intentFor(Context context, String id) {
        switch (id) {
            case TIMER: return new Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS);
            case ALARM: return new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
            case STOPWATCH: {
                Intent i = launch(context, "com.android.deskclock");
                if (i != null) i.putExtra("DeskClockTabActivity.tab", 2).putExtra("tab", 2);
                return i;
            }
            case CALC: return launch(context, "com.miui.calculator");
            case NOTES: return launch(context, "com.miui.notes");
            case RECORDER: return launch(context, "com.android.soundrecorder");
            case CALL: return new Intent(Intent.ACTION_DIAL);
            case WEATHER: case TEMP: case UV: case AQI: case SUN: return launch(context, "com.miui.weather2");
            case EVENT: case DATE: return new Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI.buildUpon()
                    .appendPath("time").appendPath(String.valueOf(System.currentTimeMillis())).build());
            case STEPS: return launch(context, "com.mi.health");
            default: return null;
        }
    }

    private static Intent launch(Context context, String pkg) {
        Intent i = context.getPackageManager().getLaunchIntentForPackage(pkg);
        return i == null ? null : i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** System UI's own starter: it asks for the unlock first, as the lock screen shortcuts do. */
    private static void startDismissingKeyguard(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ClassLoader loader = context.getClassLoader();
            Class<?> dependency = Class.forName("com.android.systemui.Dependency", false, loader);
            Object instance = dependency.getField("sDependency").get(null);
            Class<?> starterType = Class.forName("com.android.systemui.plugins.ActivityStarter", false, dependency.getClassLoader());
            Object starter = dependency.getMethod("getDependencyInner", Object.class).invoke(instance, starterType);
            starterType.getMethod("postStartActivityDismissingKeyguard", Intent.class, int.class).invoke(starter, intent, 0);
        } catch (Throwable t) {
            context.startActivity(intent);
        }
    }

    /** The next calendar event in the coming day: its time, and its title as the label. */
    private static Value nextEvent(Context context) {
        String name = word(context, 3);
        long now = System.currentTimeMillis();
        Uri.Builder uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(uri, now);
        android.content.ContentUris.appendId(uri, now + 86_400_000L);
        try (Cursor c = context.getContentResolver().query(uri.build(),
                new String[] {android.provider.CalendarContract.Instances.TITLE, android.provider.CalendarContract.Instances.BEGIN,
                        android.provider.CalendarContract.Instances.ALL_DAY},
                android.provider.CalendarContract.Instances.END + " > ?", new String[] {String.valueOf(now)},
                android.provider.CalendarContract.Instances.BEGIN + " ASC")) {
            if (c == null || !c.moveToFirst()) return new Value(0f, "--", "", name);
            String title = c.getString(0);
            long begin = c.getLong(1);
            boolean allDay = c.getInt(2) != 0;
            float p = 1f - Math.min(1f, Math.max(0f, (begin - now) / (float) 86_400_000L));
            String when = allDay ? word(context, 4) : DateFormat.getTimeFormat(context).format(begin);
            return new Value(p, when, "", TextUtils.isEmpty(title) ? name : title);
        } catch (Throwable t) {
            return new Value(0f, "--", "", name);
        }
    }

    /** On, off, today's weather, event, all day. */
    private static String word(Context context, int i) {
        String[] w;
        switch (Locale.getDefault().getLanguage()) {
            case "tr": w = new String[] {"Açık", "Kapalı", "Bugün", "Etkinlik", "Tüm gün"}; break;
            case "ru": w = new String[] {"Вкл", "Выкл", "Сегодня", "Событие", "Весь день"}; break;
            case "es": w = new String[] {"Sí", "No", "Hoy", "Evento", "Todo el día"}; break;
            case "zh": w = new String[] {"开", "关", "今天", "日程", "全天"}; break;
            default: w = new String[] {"On", "Off", "Today", "Event", "All day"}; break;
        }
        return w[i];
    }

    /** Values shown in the editor when there is no live value. */
    private static Value sample(String id) {
        switch (id) {
            case STEPS: return new Value(0.2f, "2000", "", null);
            case TEMP: return new Value(0.5f, "11°", "9  14", null);
            case ALARM: return new Value(0.5f, "12:00", "", null);
            case UV: return new Value(0f, "0", "UV", null);
            case AQI: return new Value(0.05f, "9", "AQI", null);
            case SUN: return new Value(0.4f, "4:33", "sunset", null);
            case BUDS: return new Value(0.8f, "80%", "", "Buds");
            case WEATHER: return new Value(0.5f, "11°", "", "Sunny");
            case EVENT: return new Value(0.6f, "14:00", "", "Meeting");
            case TORCH: case DND: return new Value(0f, "", "Off", null);
            default:
                if (action(id)) return new Value(0f, "", "", null);
                return new Value(0.83f, "83%", "", "Phone");
        }
    }

    private static long todayAt(long msOfDayUtc) {
        Calendar c = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() + msOfDayUtc;
    }

    private static String deviceName(Context context) {
        try {
            String name = android.provider.Settings.Global.getString(context.getContentResolver(), "device_name");
            if (!TextUtils.isEmpty(name)) return name;
        } catch (Throwable ignored) { }
        return android.os.Build.MODEL;
    }

    private static String steps(Context context) {
        try (Cursor c = context.getContentResolver().query(Uri.parse("content://com.mi.health.provider.main/widget/steps/simple"),
                new String[] {"content"}, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            String value = c.getString(0);
            return TextUtils.isEmpty(value) ? null : value;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Battery of connected earbuds (Android reports it for most Bluetooth headsets). */
    private static Value buds() {
        try {
            android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return new Value(0f, "--", "", "Buds");
            java.lang.reflect.Method connected = android.bluetooth.BluetoothDevice.class.getMethod("isConnected");
            java.lang.reflect.Method level = android.bluetooth.BluetoothDevice.class.getMethod("getBatteryLevel");
            for (android.bluetooth.BluetoothDevice d : adapter.getBondedDevices()) {
                if (!(boolean) connected.invoke(d)) continue;
                int battery = (int) level.invoke(d);
                if (battery >= 0) {
                    String name = null;
                    try { name = d.getAlias(); } catch (Throwable ignored) { }
                    if (TextUtils.isEmpty(name)) name = d.getName();
                    return new Value(battery / 100f, percent(battery), "", TextUtils.isEmpty(name) ? "Buds" : name);
                }
            }
        } catch (Throwable ignored) { }
        return new Value(0f, "--", "", "Buds");
    }

    /** Xiaomi Weather's own provider (the AOD reads it too). */
    private static final class Weather {
        String temp, description;
        int tempValue, low, high, uv = -1, aqi = -1;
        long sunrise = -1, sunset = -1;

        static Weather read(Context context) {
            try (Cursor c = context.getContentResolver().query(Uri.parse("content://weather/weather"), null, null, null, null)) {
                if (c == null || !c.moveToFirst()) return null;
                Weather w = new Weather();
                String t = col(c, "temperature");
                if (!TextUtils.isEmpty(t)) {
                    w.temp = t.replace("℃", "°").replace("℉", "°").trim();
                    if (!w.temp.contains("°")) w.temp += "°";
                    w.tempValue = number(t, 0);
                }
                w.low = number(col(c, "tmplows"), w.tempValue);
                w.high = number(col(c, "tmphighs"), w.tempValue);
                w.uv = number(col(c, "ultraviolet"), -1);
                w.aqi = number(col(c, "aqilevel"), -1);
                w.sunrise = longNumber(col(c, "sunrise"));
                w.sunset = longNumber(col(c, "sunset"));
                for (String name : new String[] {"description", "weather_desc", "weathername", "weather"}) {
                    String d = col(c, name);
                    if (!TextUtils.isEmpty(d) && !d.matches("[0-9-]+")) { w.description = d; break; }
                }
                return w;
            } catch (Throwable t) {
                return null;
            }
        }

        private static String col(Cursor c, String name) {
            int i = c.getColumnIndex(name);
            return i < 0 ? null : c.getString(i);
        }

        private static int number(String s, int byDefault) {
            if (TextUtils.isEmpty(s)) return byDefault;
            String digits = s.replaceAll("[^0-9-]", "");
            try { return digits.isEmpty() || digits.equals("-") ? byDefault : Integer.parseInt(digits); }
            catch (NumberFormatException e) { return byDefault; }
        }

        private static long longNumber(String s) {
            try { return TextUtils.isEmpty(s) ? -1 : Long.parseLong(s.trim()); } catch (NumberFormatException e) { return -1; }
        }
    }

    /** One kit: a glass circle (or a glass pill) with a gauge ring, a glyph and the value. */
    static final class Kit extends View {
        final String id;
        private Value value;
        private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
                track = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                text = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG),
                glyph = new Paint(Paint.ANTI_ALIAS_FLAG), glyphFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint workTextPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF(), workRect = new RectF();
        private final Path workPath = new Path(), workPath2 = new Path();
        private final float dp;

        Kit(Context context, String id) {
            super(context);
            this.id = id;
            dp = context.getResources().getDisplayMetrics().density;
            glass.setColor(0x33FFFFFF);
            edge.setColor(0x40FFFFFF);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1f, 0.8f * dp));
            track.setColor(0x33FFFFFF);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(3.5f * dp);
            track.setStrokeCap(Paint.Cap.ROUND);
            ring.setColor(0xF2FFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(3.5f * dp);
            ring.setStrokeCap(Paint.Cap.ROUND);
            text.setColor(0xFFFFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            small.setColor(0xB3FFFFFF);
            small.setTextAlign(Paint.Align.CENTER);
            glyph.setColor(0xE6FFFFFF);
            glyph.setStyle(Paint.Style.STROKE);
            glyph.setStrokeWidth(1.6f * dp);
            glyph.setStrokeCap(Paint.Cap.ROUND);
            glyph.setStrokeJoin(Paint.Join.ROUND);
            glyphFill.set(glyph);
            glyphFill.setStyle(Paint.Style.FILL);
            setContentDescription(id);
        }

        void set(Value v) {
            value = v;
            setVisibility(View.VISIBLE);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (wide(id)) drawWide(canvas, w, h); else drawRound(canvas, Math.min(w, h), (w - Math.min(w, h)) / 2f);
        }

        private void drawRound(Canvas canvas, float s, float left) {
            float cx = left + s / 2f, cy = s / 2f, r = s / 2f - edge.getStrokeWidth();
            if (square) {
                rect.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawRoundRect(rect, r * 0.55f, r * 0.55f, glass);
                canvas.drawRoundRect(rect, r * 0.55f, r * 0.55f, edge);
            } else {
                canvas.drawCircle(cx, cy, r, glass);
                canvas.drawCircle(cx, cy, r, edge);
            }
            float inset = 6.5f * dp;
            rect.set(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset);
            float p = value == null ? 0f : value.progress;
            if (action(id)) {
                if (p > 0.5f) canvas.drawArc(rect, 0, 360, false, ring);
            } else {
                canvas.drawArc(rect, 135, 270, false, track);
                if (p > 0.005f) canvas.drawArc(rect, 135, 270 * p, false, ring);
            }
            String big = value == null ? "--" : value.value;
            String sub = value == null ? "" : value.sub;
            if (action(id)) {
                if (TORCH.equals(id) || DND.equals(id)) {
                    drawGlyph(canvas, cx, cy - 4.5f * dp, 7f * dp);
                    small.setTextSize(8.5f * dp);
                    if (!TextUtils.isEmpty(sub)) canvas.drawText(sub, cx, cy + 15 * dp, small);
                } else {
                    drawGlyph(canvas, cx, cy, 8.5f * dp);
                }
                return;
            }
            boolean glyphOnTop = ALARM.equals(id) || BATTERY.equals(id) || SUN.equals(id) || STEPS.equals(id);
            text.setTextSize((big.length() > 4 ? 11.5f : big.length() > 3 ? 13f : 16f) * dp);
            small.setTextSize(8.5f * dp);
            if (DATE.equals(id)) {
                canvas.drawText(sub, cx, cy - 6 * dp, small);
                text.setTextSize(18f * dp);
                canvas.drawText(big, cx, cy + 13 * dp, text);
                return;
            }
            if (glyphOnTop) {
                drawGlyph(canvas, cx, cy - 8.5f * dp, 5.5f * dp);
                canvas.drawText(big, cx, cy + 11 * dp, text);
            } else {
                canvas.drawText(big, cx, cy + 4 * dp, text);
                if (!TextUtils.isEmpty(sub)) canvas.drawText(sub, cx, cy + 16 * dp, small);
            }
        }

        private void drawWide(Canvas canvas, float w, float h) {
            float stroke = edge.getStrokeWidth();
            rect.set(stroke, stroke, w - stroke, h - stroke);
            float corner = square ? h * 0.28f : h / 2f;
            canvas.drawRoundRect(rect, corner, corner, glass);
            canvas.drawRoundRect(rect, corner, corner, edge);
            // The gauge circle on the left, the name and value on the right.
            float s = h - 12 * dp, cx = 6 * dp + s / 2f, cy = h / 2f;
            workRect.set(cx - s / 2f + 3 * dp, cy - s / 2f + 3 * dp, cx + s / 2f - 3 * dp, cy + s / 2f - 3 * dp);
            canvas.drawArc(workRect, 0, 360, false, track);
            float p = value == null ? 0f : value.progress;
            if (p > 0.005f) canvas.drawArc(workRect, -90, 360 * p, false, ring);
            drawGlyph(canvas, cx, cy, 7f * dp);
            float tx = cx + s / 2f + 8 * dp, avail = w - tx - 14 * dp;
            String label = value == null || value.label == null ? (BUDS.equals(id) ? "Buds" : "") : value.label;
            if (value != null && WEATHER.equals(id) && !TextUtils.isEmpty(value.label)) label = value.label;
            small.setTextAlign(Paint.Align.LEFT);
            small.setTextSize(10f * dp);
            workTextPaint.set(small);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(15f * dp);
            canvas.drawText(TextUtils.ellipsize(label, workTextPaint, avail, TextUtils.TruncateAt.END).toString(), tx, cy - 3 * dp, small);
            canvas.drawText(value == null ? "--" : value.value, tx, cy + 15 * dp, text);
            small.setTextAlign(Paint.Align.CENTER);
            text.setTextAlign(Paint.Align.CENTER);
        }

        /** Small line icons drawn in white, so they stay sharp on any wallpaper. */
        private void drawGlyph(Canvas canvas, float cx, float cy, float r) {
            switch (id) {
                case ALARM:
                    canvas.drawCircle(cx, cy, r, glyph);
                    canvas.drawLine(cx, cy, cx, cy - r * 0.6f, glyph);
                    canvas.drawLine(cx, cy, cx + r * 0.45f, cy, glyph);
                    canvas.drawLine(cx - r * 0.9f, cy - r * 1.05f, cx - r * 0.35f, cy - r * 1.35f, glyph);
                    canvas.drawLine(cx + r * 0.9f, cy - r * 1.05f, cx + r * 0.35f, cy - r * 1.35f, glyph);
                    break;
                case BATTERY:
                case DEVICE: {
                    workRect.set(cx - r * 0.55f, cy - r, cx + r * 0.55f, cy + r);
                    canvas.drawRoundRect(workRect, r * 0.2f, r * 0.2f, glyph);
                    float p = value == null ? 0f : value.progress;
                    canvas.drawRect(workRect.left + r * 0.22f, workRect.bottom - r * 0.22f - (workRect.height() - r * 0.44f) * p, workRect.right - r * 0.22f, workRect.bottom - r * 0.22f, glyphFill);
                    break;
                }
                case SUN:
                    workRect.set(cx - r * 0.7f, cy - r * 0.3f, cx + r * 0.7f, cy + r * 1.1f);
                    canvas.drawArc(workRect, 180, 180, false, glyph);
                    canvas.drawLine(cx - r * 1.2f, cy + r * 0.4f, cx + r * 1.2f, cy + r * 0.4f, glyph);
                    canvas.drawLine(cx, cy - r * 0.65f, cx, cy - r * 1.1f, glyph);
                    canvas.drawLine(cx - r * 0.85f, cy - r * 0.35f, cx - r * 1.15f, cy - r * 0.65f, glyph);
                    canvas.drawLine(cx + r * 0.85f, cy - r * 0.35f, cx + r * 1.15f, cy - r * 0.65f, glyph);
                    break;
                case STEPS: {
                    workPath.rewind();
                    workPath.moveTo(cx - r, cy + r * 0.6f);
                    workPath.lineTo(cx - r, cy - r * 0.6f);
                    workPath.lineTo(cx - r * 0.2f, cy - r * 0.6f);
                    workPath.quadTo(cx, cy + r * 0.1f, cx + r, cy + r * 0.25f);
                    workPath.lineTo(cx + r, cy + r * 0.6f);
                    workPath.close();
                    canvas.drawPath(workPath, glyph);
                    break;
                }
                case TIMER: {
                    // An hourglass.
                    workPath.rewind();
                    workPath.moveTo(cx - r * 0.7f, cy - r);
                    workPath.lineTo(cx + r * 0.7f, cy - r);
                    workPath.lineTo(cx - r * 0.7f, cy + r);
                    workPath.lineTo(cx + r * 0.7f, cy + r);
                    workPath.close();
                    canvas.drawPath(workPath, glyph);
                    break;
                }
                case STOPWATCH:
                    canvas.drawCircle(cx, cy + r * 0.15f, r * 0.85f, glyph);
                    canvas.drawLine(cx, cy + r * 0.15f, cx + r * 0.35f, cy - r * 0.25f, glyph);
                    canvas.drawLine(cx - r * 0.3f, cy - r * 1.05f, cx + r * 0.3f, cy - r * 1.05f, glyph);
                    canvas.drawLine(cx, cy - r * 1.05f, cx, cy - r * 0.7f, glyph);
                    break;
                case TORCH: {
                    workPath.rewind();
                    workPath.moveTo(cx - r * 0.6f, cy - r);
                    workPath.lineTo(cx + r * 0.6f, cy - r);
                    workPath.lineTo(cx + r * 0.6f, cy - r * 0.55f);
                    workPath.lineTo(cx + r * 0.3f, cy - r * 0.1f);
                    workPath.lineTo(cx + r * 0.3f, cy + r);
                    workPath.lineTo(cx - r * 0.3f, cy + r);
                    workPath.lineTo(cx - r * 0.3f, cy - r * 0.1f);
                    workPath.lineTo(cx - r * 0.6f, cy - r * 0.55f);
                    workPath.close();
                    canvas.drawPath(workPath, glyph);
                    canvas.drawLine(cx, cy + r * 0.15f, cx, cy + r * 0.45f, glyph);
                    break;
                }
                case DND: {
                    // A crescent moon.
                    workPath.rewind();
                    workPath.addCircle(cx, cy, r, Path.Direction.CW);
                    workPath2.rewind();
                    workPath2.addCircle(cx + r * 0.55f, cy - r * 0.45f, r * 0.85f, Path.Direction.CW);
                    workPath.op(workPath2, Path.Op.DIFFERENCE);
                    canvas.drawPath(workPath, glyph);
                    break;
                }
                case CALC: {
                    workRect.set(cx - r * 0.75f, cy - r, cx + r * 0.75f, cy + r);
                    canvas.drawRoundRect(workRect, r * 0.25f, r * 0.25f, glyph);
                    canvas.drawLine(cx - r * 0.4f, cy - r * 0.55f, cx + r * 0.4f, cy - r * 0.55f, glyph);
                    for (int row = 0; row < 2; row++) for (int col = 0; col < 3; col++) {
                        canvas.drawCircle(cx - r * 0.38f + col * r * 0.38f, cy + r * 0.05f + row * r * 0.45f, r * 0.09f, glyphFill);
                    }
                    break;
                }
                case NOTES: {
                    workRect.set(cx - r * 0.75f, cy - r, cx + r * 0.75f, cy + r);
                    canvas.drawRoundRect(workRect, r * 0.2f, r * 0.2f, glyph);
                    for (int k = 0; k < 3; k++) {
                        float y = cy - r * 0.45f + k * r * 0.45f;
                        canvas.drawLine(cx - r * 0.4f, y, cx + (k == 2 ? r * 0.1f : r * 0.4f), y, glyph);
                    }
                    break;
                }
                case RECORDER: {
                    workRect.set(cx - r * 0.35f, cy - r, cx + r * 0.35f, cy + r * 0.25f);
                    canvas.drawRoundRect(workRect, r * 0.35f, r * 0.35f, glyph);
                    workRect.set(cx - r * 0.7f, cy - r * 0.5f, cx + r * 0.7f, cy + r * 0.6f);
                    canvas.drawArc(workRect, 0, 180, false, glyph);
                    canvas.drawLine(cx, cy + r * 0.6f, cx, cy + r, glyph);
                    break;
                }
                case CALL: {
                    // A phone handset.
                    workPath.rewind();
                    workPath.moveTo(cx - r * 0.75f, cy - r * 0.85f);
                    workPath.quadTo(cx - r * 0.95f, cy + r * 0.2f, cx + r * 0.1f, cy + r * 0.85f);
                    workPath.lineTo(cx + r * 0.45f, cy + r * 0.85f);
                    workPath.lineTo(cx + r * 0.75f, cy + r * 0.45f);
                    workPath.lineTo(cx + r * 0.3f, cy + r * 0.1f);
                    workPath.lineTo(cx + r * 0.05f, cy + r * 0.35f);
                    workPath.quadTo(cx - r * 0.4f, cy, cx - r * 0.3f, cy - r * 0.35f);
                    workPath.lineTo(cx - r * 0.05f, cy - r * 0.55f);
                    workPath.lineTo(cx - r * 0.35f, cy - r);
                    workPath.close();
                    canvas.drawPath(workPath, glyph);
                    break;
                }
                case WEATHER:
                    canvas.drawCircle(cx - r * 0.2f, cy - r * 0.2f, r * 0.5f, glyph);
                    workRect.set(cx - r * 0.5f, cy, cx + r, cy + r * 0.9f);
                    canvas.drawArc(workRect, 180, 180, false, glyph);
                    canvas.drawLine(cx - r * 0.5f, cy + r * 0.9f, cx + r, cy + r * 0.9f, glyph);
                    break;
                case EVENT: {
                    workRect.set(cx - r * 0.85f, cy - r * 0.7f, cx + r * 0.85f, cy + r * 0.85f);
                    canvas.drawRoundRect(workRect, r * 0.2f, r * 0.2f, glyph);
                    canvas.drawLine(cx - r * 0.85f, cy - r * 0.25f, cx + r * 0.85f, cy - r * 0.25f, glyph);
                    canvas.drawLine(cx - r * 0.4f, cy - r, cx - r * 0.4f, cy - r * 0.5f, glyph);
                    canvas.drawLine(cx + r * 0.4f, cy - r, cx + r * 0.4f, cy - r * 0.5f, glyph);
                    break;
                }
                case BUDS:
                    canvas.drawCircle(cx - r * 0.5f, cy - r * 0.35f, r * 0.42f, glyph);
                    canvas.drawLine(cx - r * 0.5f, cy + r * 0.05f, cx - r * 0.5f, cy + r * 0.85f, glyph);
                    canvas.drawCircle(cx + r * 0.5f, cy - r * 0.35f, r * 0.42f, glyph);
                    canvas.drawLine(cx + r * 0.5f, cy + r * 0.05f, cx + r * 0.5f, cy + r * 0.85f, glyph);
                    break;
                default:
                    break;
            }
        }
    }

    /** Reads the stored order from XAod's settings (null-safe). */
    static List<String> stored(SharedPreferences prefs) {
        return order(prefs == null ? null : prefs.getStringSet(Features.AOD_KIT_LIST, null));
    }
}
