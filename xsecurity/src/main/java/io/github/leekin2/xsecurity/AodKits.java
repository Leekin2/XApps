package io.github.leekin2.xsecurity;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glass kits on the always-on display, as in kashi's HyperOS AOD mod: date and the next event,
 * alarm, battery, weather, steps and earbuds. A kit without data stays hidden. Data is read off
 * the main thread once per clock update. Written anew.
 */
final class AodKits {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AodKits() { }

    static void refresh(Context context, LinearLayout row) {
        WORKER.execute(() -> {
            List<String> texts = new ArrayList<>();
            add(texts, date(context));
            add(texts, alarm(context));
            add(texts, battery(context));
            add(texts, weather(context));
            add(texts, steps(context));
            add(texts, buds());
            MAIN.post(() -> show(context, row, texts));
        });
    }

    private static void add(List<String> list, String text) {
        if (!TextUtils.isEmpty(text)) list.add(text);
    }

    private static void show(Context context, LinearLayout row, List<String> texts) {
        row.removeAllViews();
        float dp = context.getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = null;
        // Three per line keeps them readable; the rest go on a second line.
        for (int i = 0; i < texts.size(); i++) {
            if (i % 3 == 0) {
                line = new LinearLayout(context);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(android.view.Gravity.CENTER);
                column.addView(line);
            }
            TextView kit = new TextView(context);
            kit.setText(texts.get(i));
            kit.setTextColor(0xE6FFFFFF);
            kit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            kit.setSingleLine(true);
            kit.setEllipsize(TextUtils.TruncateAt.END);
            kit.setMaxWidth((int) (150 * dp));
            kit.setPadding((int) (12 * dp), (int) (6 * dp), (int) (12 * dp), (int) (6 * dp));
            GradientDrawable glass = new GradientDrawable();
            glass.setColor(0x1FFFFFFF);
            glass.setStroke((int) Math.max(1, dp), 0x26FFFFFF);
            glass.setCornerRadius(16 * dp);
            kit.setBackground(glass);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int) (4 * dp), (int) (3 * dp), (int) (4 * dp), (int) (3 * dp));
            line.addView(kit, lp);
        }
        row.addView(column);
        row.setVisibility(texts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** "Wed 25 · Meeting 14:00" for an event in the next 12 hours. */
    private static String date(Context context) {
        String day = DateFormat.format("EEE d", Calendar.getInstance()).toString();
        try {
            long now = System.currentTimeMillis();
            Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
            android.content.ContentUris.appendId(uri, now);
            android.content.ContentUris.appendId(uri, now + 12 * 3_600_000L);
            try (Cursor c = context.getContentResolver().query(uri.build(),
                    new String[] {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN},
                    CalendarContract.Instances.ALL_DAY + "=0 AND " + CalendarContract.Instances.BEGIN + ">=" + now,
                    null, CalendarContract.Instances.BEGIN + " ASC")) {
                if (c != null && c.moveToFirst() && !TextUtils.isEmpty(c.getString(0))) {
                    return "📅 " + day + " · " + c.getString(0) + " " + DateFormat.getTimeFormat(context).format(c.getLong(1));
                }
            }
        } catch (Throwable ignored) { }
        return "📅 " + day;
    }

    private static String alarm(Context context) {
        AlarmManager.AlarmClockInfo next = context.getSystemService(AlarmManager.class).getNextAlarmClock();
        if (next == null || next.getTriggerTime() - System.currentTimeMillis() > 24 * 3_600_000L) return null;
        return "⏰ " + DateFormat.getTimeFormat(context).format(next.getTriggerTime());
    }

    private static String battery(Context context) {
        Intent b = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null || b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) return null; // the charging line shows it
        return "🔋 %" + b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
    }

    /** Xiaomi Weather's own provider, which the AOD already reads for sunrise. */
    private static String weather(Context context) {
        try (Cursor c = context.getContentResolver().query(Uri.parse("content://weather/weather"), null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            String temp = column(c, "temperature");
            String desc = column(c, "description");
            if (TextUtils.isEmpty(temp)) return null;
            // Xiaomi Weather writes "8℃" with the single ℃ sign; shown as "8°" like the lock screen.
            temp = temp.replace("℃", "°").replace("℉", "°F").trim();
            if (!temp.contains("°")) temp = temp + "°";
            return "🌤 " + temp + (TextUtils.isEmpty(desc) ? "" : " " + desc);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String column(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        return i < 0 ? null : c.getString(i);
    }

    /** Mi Fitness steps, the provider the AOD's own step style uses. */
    private static String steps(Context context) {
        try (Cursor c = context.getContentResolver().query(Uri.parse("content://com.mi.health.provider.main/widget/steps/simple"),
                new String[] {"content"}, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            String value = c.getString(0);
            return TextUtils.isEmpty(value) ? null : "👣 " + value;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Battery of connected earbuds (Android reports it for most Bluetooth headsets). */
    private static String buds() {
        try {
            android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return null;
            java.lang.reflect.Method connected = android.bluetooth.BluetoothDevice.class.getMethod("isConnected");
            java.lang.reflect.Method level = android.bluetooth.BluetoothDevice.class.getMethod("getBatteryLevel");
            for (android.bluetooth.BluetoothDevice d : adapter.getBondedDevices()) {
                if (!(boolean) connected.invoke(d)) continue;
                int battery = (int) level.invoke(d);
                if (battery >= 0) return "🎧 %" + battery;
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
