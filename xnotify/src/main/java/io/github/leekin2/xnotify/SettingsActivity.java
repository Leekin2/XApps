package io.github.leekin2.xnotify;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import io.github.libxposed.service.XposedService;

/**
 * Opened from LSPosed: one switch per feature. The values live in LSPosed's remote preferences,
 * which the hooks read when System UI or Interconnectivity starts.
 */
public final class SettingsActivity extends Activity {
    static final String GROUP = "settings";
    /** Phone → tablet notifications (Interconnectivity, tablet pop-ups). */
    static final String FORWARD = "forward";
    /** Xiaomi reply box and buttons for every notification (System UI). */
    static final String REPLY = "reply";
    /** "Clear all" dissolves the cards into particles (System UI); off by default. */
    static final String CLEAR_PARTICLES = "clear_particles";
    /** The charging island shows the live charging power (System UI); off by default. */
    static final String CHARGE_ISLAND = "charge_island";
    /** The music island's bars follow the song (System UI); off by default. */
    static final String MUSIC_SPECTRUM = "music_spectrum";
    static final String ROUND_CLEAR = "round_clear";
    static final String CUSTOM_CARRIER = "custom_carrier";
    static final String CARRIER_TEXT = "carrier_text";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * dp);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, pad, pad, pad);

        XposedService service = ScopeApp.service;
        SharedPreferences prefs = service == null ? null : service.getRemotePreferences(GROUP);
        if (prefs == null) {
            list.addView(text(getString(R.string.settings_no_service), 14));
            // LSPosed binds a moment after the process starts; reopen once it has.
            list.postDelayed(() -> { if (ScopeApp.service != null && !isFinishing()) recreate(); }, 1_000);
        }
        // Grouped by where the change shows: the notification shade, the Super Island, other devices.
        list.addView(header(R.string.settings_section_shade));
        list.addView(toggle(prefs, REPLY, R.string.settings_reply, R.string.settings_reply_summary, true));
        list.addView(toggle(prefs, CLEAR_PARTICLES, R.string.settings_clear_particles, R.string.settings_clear_particles_summary, false));
        list.addView(toggle(prefs, ROUND_CLEAR, R.string.settings_round_clear, R.string.settings_round_clear_summary, false));
        list.addView(toggle(prefs, CUSTOM_CARRIER, R.string.settings_custom_carrier, R.string.settings_custom_carrier_summary, false));
        list.addView(carrierText(prefs));
        list.addView(header(R.string.settings_section_island));
        list.addView(toggle(prefs, CHARGE_ISLAND, R.string.settings_charge_island, R.string.settings_charge_island_summary, false));
        list.addView(toggle(prefs, MUSIC_SPECTRUM, R.string.settings_music_spectrum, R.string.settings_music_spectrum_summary, false));
        list.addView(header(R.string.settings_section_devices));
        list.addView(toggle(prefs, FORWARD, R.string.settings_forward, R.string.settings_forward_summary, true));
        list.addView(restartButton());
        list.addView(text(getString(R.string.settings_restart), 13));

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(list);
        setContentView(scroll);
    }

    private TextView carrierText(SharedPreferences prefs) {
        TextView row = text(getString(R.string.settings_carrier_text), 16);
        row.setEnabled(prefs != null);
        int dp = Math.round(16 * getResources().getDisplayMetrics().density);
        row.setPadding(0, 0, 0, dp);
        if (prefs == null) return row;
        Runnable refresh = () -> {
            String value = prefs.getString(CARRIER_TEXT, "");
            row.setText(getString(R.string.settings_carrier_text) + (value.isEmpty() ? "" : "   " + value));
        };
        refresh.run();
        row.setOnClickListener(v -> {
            android.widget.EditText edit = new android.widget.EditText(this);
            edit.setSingleLine(true);
            edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
            edit.setText(prefs.getString(CARRIER_TEXT, ""));
            edit.setSelectAllOnFocus(true);
            new android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.settings_carrier_text).setView(edit)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, button) -> {
                        prefs.edit().putString(CARRIER_TEXT, edit.getText().toString().strip()).apply();
                        refresh.run();
                    }).show();
        });
        return row;
    }

    private LinearLayout toggle(SharedPreferences prefs, String key, int title, int summary, boolean byDefault) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, Math.round(20 * getResources().getDisplayMetrics().density));
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        toggle.setChecked(prefs == null ? byDefault : prefs.getBoolean(key, byDefault));
        toggle.setEnabled(prefs != null);
        toggle.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
        row.addView(toggle);
        TextView hint = text(getString(summary), 13);
        hint.setAlpha(0.7f);
        row.addView(hint);
        return row;
    }

    /** Indents a switch that belongs to the one above it. */
    private LinearLayout sub(LinearLayout row) {
        row.setPadding(Math.round(24 * getResources().getDisplayMetrics().density), row.getPaddingTop(), 0, row.getPaddingBottom());
        return row;
    }

    /** A small coloured title above a group of switches. */
    private TextView header(int title) {
        TextView view = text(getString(title), 13);
        float dp = getResources().getDisplayMetrics().density;
        view.setPadding(0, Math.round(8 * dp), 0, Math.round(12 * dp));
        view.setTextColor(0xFFFF8A00);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setAllCaps(true);
        view.setLetterSpacing(0.05f);
        return view;
    }

    /** One-tap root restart of System UI so shade/island switches apply without a full reboot. */
    private android.widget.Button restartButton() {
        android.widget.Button btn = new android.widget.Button(this);
        btn.setText(restartLabel());
        btn.setOnClickListener(v -> {
            btn.setEnabled(false);
            new Thread(() -> {
                boolean ok = false;
                try {
                    Process p = new ProcessBuilder("su", "-c", "id -u; killall com.android.systemui; true")
                            .redirectErrorStream(true).start();
                    byte[] out = p.getInputStream().readAllBytes();
                    ok = p.waitFor() == 0 && new String(out).trim().startsWith("0");
                } catch (Throwable ignored) { }
                boolean done = ok;
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    if (!done) android.widget.Toast.makeText(this, rootLabel(), android.widget.Toast.LENGTH_SHORT).show();
                });
            }, "XNotify restart").start();
        });
        return btn;
    }

    private static String rootLabel() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return "Root izni gerekli";
            case "ru": return "Нужен root-доступ";
            case "es": return "Se necesita acceso root";
            case "zh": return "需要 Root 权限";
            default: return "Root access required";
        }
    }

    private static String restartLabel() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return "Sistem Arayüzünü yeniden başlat";
            case "ru": return "Перезапустить System UI";
            case "es": return "Reiniciar System UI";
            case "zh": return "重启系统界面";
            default: return "Restart System UI";
        }
    }

    private TextView text(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return view;
    }
}
