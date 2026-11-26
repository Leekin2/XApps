package io.github.leekin2.xsecurity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Optimized HyperOS 4 Security sidebar tools:
 * - Clipboard history (last 50 texts, pin/unpin/delete, sensitive copies excluded)
 * - Clean 2-column sidebar with a 2x2 rounded glass tool header (screenshot, screen record,
 *   clipboard, Circle to Search) sized to the widened panel, dynamic height calculation
 *   for 2-column rows, and All-Apps drawer width adjustment so nothing clips off-screen.
 */
final class SidebarHooks {
    private static final String HEADER = "xsecurity.sidebar.header";
    private static final String CLIPS = "xsecurity_clipboard";
    private static final int MAX = 50;
    private static final int HEADER_HEIGHT_2X2_DP = 88;
    private static final int HEADER_HEIGHT_SINGLE_DP = 46;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean listening;
    private final Hooks hooks;
    private final ClassLoader loader;
    private final boolean clipboard;
    private final boolean twoColumns;

    SidebarHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
        this.clipboard = Features.enabled(Features.SIDEBAR_CLIPBOARD);
        this.twoColumns = false;
    }

    void install(DexTargets targets) throws Throwable {
        if (clipboard) {
            Class<?> service = Class.forName("com.miui.dock.DockMonitorService", false, loader);
            hooks.install(service.getDeclaredMethod("onCreate"), chain -> {
                Object result = chain.proceed();
                listen((Context) chain.getThisObject());
                return result;
            });
        }
        List<Class<?>> panels = targets.classesUsing("kashi_sidebar_panel", "NewDock updateDockModelList data size: ");
        if (panels.isEmpty()) throw new ClassNotFoundException("Sidebar panel not found");
        for (Class<?> panel : panels) {
            for (Constructor<?> c : panel.getDeclaredConstructors()) {
                hooks.install(c, chain -> {
                    Object result = chain.proceed();
                    ViewGroup view = (ViewGroup) chain.getThisObject();
                    MAIN.post(() -> {
                        try { decorate(view); } catch (Throwable t) { hooks.error("sidebar tools", t); }
                    });
                    return result;
                });
            }
        }
        adjustDockHeight(targets);
        if (twoColumns) {
            widenPanel();
            fitAllAppsDrawer();
        }
    }

    // --- Clipboard history ------------------------------------------------------------------

    private static void listen(Context context) {
        if (listening) return;
        listening = true;
        Context app = context.getApplicationContext();
        ClipboardManager clip = app.getSystemService(ClipboardManager.class);
        if (clip == null) return;
        clip.addPrimaryClipChangedListener(() -> {
            try {
                ClipData data = clip.getPrimaryClip();
                if (data == null || data.getItemCount() == 0) return;
                CharSequence text = data.getItemAt(0).coerceToText(app);
                if (text == null || text.toString().trim().isEmpty()) return;
                // Passwords from password managers are marked sensitive: never kept.
                if (data.getDescription() != null && data.getDescription().getExtras() != null
                        && data.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE")) return;
                String value = text.toString();
                if (secret(value)) return;
                // Long texts are kept only in part; the file is written off the main thread.
                String kept = value.length() > MAX_CHARS ? value.substring(0, MAX_CHARS) : value;
                WRITER.execute(() -> add(app, kept));
            } catch (Throwable t) {
                android.util.Log.w("XSecurity", "clipboard history", t);
            }
        });
    }

    /** One entry is kept up to this many characters. */
    private static final int MAX_CHARS = 6000;
    private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("\\s*\\d{4,8}\\s*");
    private static final java.util.regex.Pattern IBAN = java.util.regex.Pattern.compile("(?i)\\s*[A-Z]{2}\\d{2}[ A-Z0-9]{11,30}\\s*");
    private static final java.util.regex.Pattern CARD = java.util.regex.Pattern.compile("\\s*(?:\\d[ -]?){13,19}\\s*");

    /** One-time codes, card numbers and IBANs are never kept in the history. */
    private static boolean secret(String text) {
        return CODE.matcher(text).matches() || CARD.matcher(text).matches() || IBAN.matcher(text).matches();
    }

    private static final class Clip {
        String text;
        boolean pinned;
        long time;
    }

    private static List<Clip> load(Context context) {
        List<Clip> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString("items", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Clip c = new Clip();
                c.text = o.getString("t");
                c.pinned = o.optBoolean("p");
                c.time = o.optLong("d");
                out.add(c);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static void save(Context context, List<Clip> clips) {
        JSONArray array = new JSONArray();
        try {
            for (Clip c : clips) array.put(new JSONObject().put("t", c.text).put("p", c.pinned).put("d", c.time));
        } catch (Throwable ignored) { }
        prefs(context).edit().putString("items", array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(CLIPS, Context.MODE_PRIVATE);
    }

    private static synchronized void add(Context context, String text) {
        List<Clip> clips = load(context);
        boolean pinned = false;
        for (int i = clips.size() - 1; i >= 0; i--) {
            if (clips.get(i).text.equals(text)) { pinned = clips.get(i).pinned; clips.remove(i); }
        }
        Clip c = new Clip();
        c.text = text;
        c.pinned = pinned;
        c.time = System.currentTimeMillis();
        clips.add(0, c);
        int unpinned = 0;
        for (int i = 0; i < clips.size(); i++) {
            if (clips.get(i).pinned) continue;
            if (++unpinned > MAX) { clips.remove(i); i--; }
        }
        save(context, clips);
    }

    /** Title, empty, copied, pin, unpin, delete, clear, clear question. */
    private static String[] words() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return new String[] {"Pano geçmişi", "Henüz kopyalanan bir şey yok", "Kopyalandı", "Sabitle", "Sabitlemeyi kaldır", "Sil", "Temizle", "Sabitlenmemiş tüm kayıtlar silinsin mi?"};
            case "ru": return new String[] {"История буфера", "Пока ничего не скопировано", "Скопировано", "Закрепить", "Открепить", "Удалить", "Очистить", "Удалить все незакреплённые записи?"};
            case "es": return new String[] {"Historial del portapapeles", "Aún no se ha copiado nada", "Copiado", "Fijar", "Desfijar", "Eliminar", "Borrar", "¿Borrar todo lo no fijado?"};
            case "zh": return new String[] {"剪贴板历史", "还没有复制内容", "已复制", "置顶", "取消置顶", "删除", "清空", "清空所有未置顶的记录？"};
            default: return new String[] {"Clipboard history", "Nothing copied yet", "Copied", "Pin", "Unpin", "Delete", "Clear", "Clear everything that is not pinned?"};
        }
    }

    private static void showHistory(Context context) {
        String[] w = words();
        List<Clip> clips = load(context);
        List<Clip> ordered = new ArrayList<>();
        for (Clip c : clips) if (c.pinned) ordered.add(c);
        for (Clip c : clips) if (!c.pinned) ordered.add(c);
        String[] items = new String[ordered.size()];
        for (int i = 0; i < items.length; i++) {
            String t = ordered.get(i).text.replaceAll("\\s+", " ").trim();
            items[i] = (ordered.get(i).pinned ? "📌 " : "") + (t.length() > 90 ? t.substring(0, 89).trim() + "…" : t);
        }
        Context themed = new android.view.ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        AlertDialog.Builder builder = new AlertDialog.Builder(themed).setTitle(w[0]);
        if (items.length == 0) builder.setMessage(w[1]);
        else builder.setItems(items, (d, which) -> {
            ClipboardManager cm = context.getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", ordered.get(which).text));
            Toast.makeText(context, w[2], Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        if (items.length > 0) builder.setNeutralButton(w[6], (d, x) -> confirm(context, () -> {
            List<Clip> kept = new ArrayList<>();
            for (Clip c : load(context)) if (c.pinned) kept.add(c);
            save(context, kept);
        }));
        AlertDialog dialog = builder.create();
        show(dialog);
        if (dialog.getListView() != null) {
            // A thin line between entries, so long texts do not run into each other.
            dialog.getListView().setDivider(new android.graphics.drawable.ColorDrawable(0x33888888));
            dialog.getListView().setDividerHeight(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)));
        }
        if (dialog.getListView() != null) dialog.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            Clip c = ordered.get(position);
            String[] actions = {c.pinned ? w[4] : w[3], w[5]};
            AlertDialog menu = new AlertDialog.Builder(themed).setItems(actions, (d2, which) -> {
                synchronized (SidebarHooks.class) {
                    List<Clip> all = load(context);
                    for (int i = 0; i < all.size(); i++) {
                        if (!all.get(i).text.equals(c.text)) continue;
                        if (which == 0) all.get(i).pinned = !all.get(i).pinned; else all.remove(i);
                        break;
                    }
                    save(context, all);
                }
                dialog.dismiss();
                showHistory(context);
            }).create();
            show(menu);
            return true;
        });
    }

    private static void confirm(Context context, Runnable action) {
        Context themed = new android.view.ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        AlertDialog d = new AlertDialog.Builder(themed).setMessage(words()[7])
                .setPositiveButton(android.R.string.ok, (x, y) -> action.run())
                .setNegativeButton(android.R.string.cancel, null).create();
        show(d);
    }

    /** Window types that sit above the sidebar panel itself (TYPE_DISPLAY_OVERLAY), then the usual overlay. */
    private static final int[] OVERLAY_TYPES = {2026, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY};

    /**
     * Shows a dialog above the open sidebar. The sidebar panel is a display overlay, so a dialog
     * of the ordinary overlay type opened under it; the higher type is tried first.
     */
    private static void show(AlertDialog dialog) {
        for (int type : OVERLAY_TYPES) {
            try {
                if (dialog.getWindow() != null) dialog.getWindow().setType(type);
                dialog.show();
                return;
            } catch (Throwable t) {
                android.util.Log.w("XSecurity", "sidebar dialog type " + type + " refused", t);
            }
        }
    }

    // --- Tools on top of the sidebar ----------------------------------------------------------

    private void decorate(ViewGroup panel) {
        if (panel.findViewWithTag(HEADER) != null) return;
        Context context = panel.getContext();
        float dp = context.getResources().getDisplayMetrics().density;
        boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        View list = null;
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (child.getClass().getName().contains("RecyclerView")) list = child;
        }
        if (list == null) return;

        int padH = Math.round(6 * dp);
        int gap = Math.round(6 * dp);
        LinearLayout header = new LinearLayout(context);
        header.setTag(HEADER);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(padH, Math.round(4 * dp), padH, Math.round(4 * dp));

        int headerHeight;
        if (twoColumns) {
            int btnH = Math.round(37 * dp);
            LinearLayout row1 = toolRow(context);
            row1.addView(toolButton(context, Glyph.SCREENSHOT, dark, dp, v -> screenshot(panel)), rowCell(btnH, gap / 2));
            row1.addView(toolButton(context, Glyph.RECORD, dark, dp, v -> closeThen(panel, () -> record(context))), rowCell(btnH, gap / 2));

            LinearLayout row2 = toolRow(context);
            if (clipboard) {
                row2.addView(toolButton(context, Glyph.CLIPBOARD, dark, dp, v -> closeThen(panel, () -> showHistory(context))), rowCell(btnH, gap / 2));
            }
            row2.addView(toolButton(context, Glyph.SEARCH, dark, dp, v -> closeThen(panel, () -> circleToSearch(context))), rowCell(btnH, gap / 2));

            header.addView(row1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, btnH));
            LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, btnH);
            r2p.topMargin = gap;
            header.addView(row2, r2p);
            headerHeight = Math.round(HEADER_HEIGHT_2X2_DP * dp);
        } else if (clipboard) {
            int btnH = Math.round(38 * dp);
            header.addView(toolButton(context, Glyph.CLIPBOARD, dark, dp, v -> closeThen(panel, () -> showHistory(context))),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, btnH));
            headerHeight = Math.round(HEADER_HEIGHT_SINGLE_DP * dp);
        } else {
            return;
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, headerHeight, Gravity.TOP);
        lp.topMargin = Math.round(8 * dp);
        panel.addView(header, lp);

        if (list.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams listParams = (ViewGroup.MarginLayoutParams) list.getLayoutParams();
            listParams.topMargin += headerHeight + Math.round(10 * dp);
            list.setLayoutParams(listParams);
        }

        if (twoColumns) {
            int sidePad = Math.round(4 * dp);
            list.setPadding(sidePad, list.getPaddingTop(), sidePad, list.getPaddingBottom());
            try {
                Class<?> grid = Class.forName("androidx.recyclerview.widget.GridLayoutManager", false, list.getClass().getClassLoader());
                Object manager = grid.getConstructor(Context.class, int.class).newInstance(context, 2);
                for (Method m : list.getClass().getMethods()) {
                    if (m.getName().equals("setLayoutManager") && m.getParameterCount() == 1) {
                        m.invoke(list, manager);
                        break;
                    }
                }
            } catch (Throwable t) {
                hooks.error("sidebar two columns", t);
            }
        }
    }

    private static LinearLayout toolRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private static LinearLayout.LayoutParams rowCell(int height, int marginH) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height, 1f);
        p.leftMargin = marginH;
        p.rightMargin = marginH;
        return p;
    }

    private static View toolButton(Context context, int kind, boolean dark, float dp, View.OnClickListener click) {
        ImageView b = new ImageView(context);
        b.setImageDrawable(new Glyph(kind, dark ? 0xF0FFFFFF : 0xDD1A1A1A));
        int pad = Math.round(8 * dp);
        b.setPadding(pad, pad, pad, pad);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * dp);
        bg.setColor(dark ? 0x26FFFFFF : 0x16000000);
        bg.setStroke(Math.max(1, Math.round(0.7f * dp)), dark ? 0x2AFFFFFF : 0x1C000000);
        b.setBackground(new RippleDrawable(
                ColorStateList.valueOf(dark ? 0x44FFFFFF : 0x28000000), bg, null));
        b.setOnClickListener(click);
        return b;
    }

    /**
     * Closes the sidebar the way a tap beside it does: the panel sits in a full-screen container
     * (sidebar_panel_container) whose click closes it with HyperOS's own animation. Then the tool
     * runs, so the tool's window, recording or Circle to Search capture is never covered by the
     * sidebar. If no container is found, the tool still runs.
     */
    private static void closeThen(ViewGroup panel, Runnable tool) {
        try {
            View container = null;
            for (android.view.ViewParent p = panel.getParent(); p instanceof View; p = p.getParent()) {
                View v = (View) p;
                if (v.getId() != View.NO_ID) {
                    String name = null;
                    try { name = v.getResources().getResourceEntryName(v.getId()); } catch (Throwable ignored) { }
                    if ("sidebar_panel_container".equals(name)) { container = v; break; }
                }
            }
            if (container != null && container.isClickable()) container.performClick();
        } catch (Throwable t) {
            android.util.Log.w("XSecurity", "sidebar close", t);
        }
        MAIN.postDelayed(() -> {
            try { tool.run(); } catch (Throwable t) { android.util.Log.w("XSecurity", "sidebar tool", t); }
        }, 250);
    }

    private static void screenshot(ViewGroup panel) {
        View root = panel.getRootView();
        root.setAlpha(0f);
        MAIN.postDelayed(() -> {
            try {
                String action = (String) Class.forName("android.content.MiuiIntent").getField("ACTION_CAPTURE_SCREENSHOT").get(null);
                Object current = android.os.UserHandle.class.getField("CURRENT").get(null);
                panel.getContext().sendBroadcastAsUser(new Intent(action), (android.os.UserHandle) current);
            } catch (Throwable t) {
                android.util.Log.w("XSecurity", "sidebar screenshot", t);
            }
        }, 250);
        // After the capture the sidebar closes as after any other tool; it is shown again
        // (alpha back to 1) only once it is gone, so it does not flash back on screen.
        MAIN.postDelayed(() -> closeThen(panel, () -> root.setAlpha(1f)), 1200);
    }

    private static void record(Context context) {
        try {
            Intent intent = new Intent("miui.intent.screenrecorder.RECORDER_SERVICE")
                    .setPackage("com.miui.screenrecorder").putExtra("is_start_immediately", true);
            context.startForegroundService(intent);
        } catch (Throwable t) {
            Toast.makeText(context, "Screen recorder unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private static void circleToSearch(Context context) {
        Intent ask = new Intent("io.github.leekin2.xgoogle.CIRCLE_TO_SEARCH").setPackage("com.android.systemui");
        context.sendOrderedBroadcast(ask, null, new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (getResultCode() == 1) return;
                try {
                    c.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("googleapp://lens"))
                            .setPackage("com.google.android.googlequicksearchbox").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable ignored) { }
            }
        }, null, 0, null, null);
    }

    /** Crisp vector icons drawn in theme-adaptive color so every tool button is sharp and visible. */
    private static final class Glyph extends Drawable {
        static final int CLIPBOARD = 0, SEARCH = 1, SCREENSHOT = 2, RECORD = 3;
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glyph(int kind, int color) {
            this.kind = kind;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            fill.setColor(kind == RECORD ? 0xFFEF4444 : color);
            fill.setStyle(Paint.Style.FILL);
        }

        @Override public void draw(Canvas canvas) {
            float w = getBounds().width(), h = getBounds().height();
            float s = Math.min(w, h);
            paint.setStrokeWidth(s * 0.085f);
            float l = getBounds().left + (w - s) / 2, t = getBounds().top + (h - s) / 2;
            switch (kind) {
                case SEARCH:
                    canvas.drawCircle(l + s * 0.44f, t + s * 0.44f, s * 0.27f, paint);
                    canvas.drawLine(l + s * 0.64f, t + s * 0.64f, l + s * 0.86f, t + s * 0.86f, paint);
                    break;
                case SCREENSHOT: {
                    float a = s * 0.16f, b = s * 0.36f, c = s * 0.64f, d = s * 0.84f;
                    // Four corner crop brackets
                    canvas.drawLine(l + a, t + b, l + a, t + a, paint);
                    canvas.drawLine(l + a, t + a, l + b, t + a, paint);
                    canvas.drawLine(l + c, t + a, l + d, t + a, paint);
                    canvas.drawLine(l + d, t + a, l + d, t + b, paint);
                    canvas.drawLine(l + a, t + c, l + a, t + d, paint);
                    canvas.drawLine(l + a, t + d, l + b, t + d, paint);
                    canvas.drawLine(l + c, t + d, l + d, t + d, paint);
                    canvas.drawLine(l + d, t + c, l + d, t + d, paint);
                    break;
                }
                case RECORD:
                    canvas.drawCircle(l + s * 0.5f, t + s * 0.5f, s * 0.36f, paint);
                    canvas.drawCircle(l + s * 0.5f, t + s * 0.5f, s * 0.18f, fill);
                    break;
                default: {
                    RectF board = new RectF(l + s * 0.2f, t + s * 0.18f, l + s * 0.8f, t + s * 0.9f);
                    canvas.drawRoundRect(board, s * 0.10f, s * 0.10f, paint);
                    canvas.drawRoundRect(new RectF(l + s * 0.36f, t + s * 0.1f, l + s * 0.64f, t + s * 0.26f), s * 0.05f, s * 0.05f, paint);
                    canvas.drawLine(l + s * 0.33f, t + s * 0.45f, l + s * 0.67f, t + s * 0.45f, paint);
                    canvas.drawLine(l + s * 0.33f, t + s * 0.61f, l + s * 0.67f, t + s * 0.61f, paint);
                    canvas.drawLine(l + s * 0.33f, t + s * 0.76f, l + s * 0.55f, t + s * 0.76f, paint);
                    break;
                }
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); fill.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); fill.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // --- Dynamic height & width sizing ------------------------------------------------------

    /** Adds header height to TurboUtils.getNewDockHeight (y2.c) and clamps cleanly to max dock height. */
    private void adjustDockHeight(DexTargets targets) {
        try {
            List<Method> methods = targets.usingStrings("kashi_sidebar_height", "int", "getNewDockHeight dockModelList: ");
            if (methods.isEmpty()) return;
            Method getHeight = methods.get(0);
            Class<?> util = getHeight.getDeclaringClass();
            Method maxHeightMethod = null;
            for (Method m : util.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == int.class && m.getName().equals("i")) {
                    m.setAccessible(true);
                    maxHeightMethod = m;
                    break;
                }
            }
            Method maxMethod = maxHeightMethod;
            hooks.install(getHeight, chain -> {
                Object result = chain.proceed();
                if (!(result instanceof Integer)) return result;
                try {
                    int h = (Integer) result;
                    Context app = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                    if (app == null) return result;
                    float dp = app.getResources().getDisplayMetrics().density;
                    int extra = Math.round((twoColumns ? HEADER_HEIGHT_2X2_DP + 18 : HEADER_HEIGHT_SINGLE_DP + 18) * dp);
                    int out = h + extra;
                    if (maxMethod != null) {
                        int maxH = (Integer) maxMethod.invoke(null);
                        if (maxH > 0) out = Math.min(out, maxH);
                    }
                    return out;
                } catch (Throwable ignored) {
                    return result;
                }
            });
        } catch (Throwable t) {
            hooks.error("sidebar dock height", t);
        }
    }

    /** Doubles dock_panel_width for two columns of icons + 2x2 tool grid. */
    private void widenPanel() throws Throwable {
        Method offset = android.content.res.Resources.class.getDeclaredMethod("getDimensionPixelOffset", int.class);
        Method size = android.content.res.Resources.class.getDeclaredMethod("getDimensionPixelSize", int.class);
        int[] id = {0};
        XposedFriendly hook = chain -> {
            Object value = chain.proceed();
            if (id[0] == 0) {
                // Looked up in the Security app's own resources. The first caller can be the
                // system's resources, where the name does not exist: that answer is not kept,
                // so the lookup is tried again until the app's resources are ready.
                try {
                    Context app = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                    if (app != null) {
                        int found = app.getResources().getIdentifier("dock_panel_width", "dimen", app.getPackageName());
                        // The app's own answer is final: -1 means this version has no such size.
                        id[0] = found != 0 ? found : -1;
                    }
                } catch (Throwable ignored) { }
            }
            if (id[0] != 0 && (int) chain.getArg(0) == id[0] && value instanceof Integer) {
                float dp = ((android.content.res.Resources) chain.getThisObject()).getDisplayMetrics().density;
                return (Integer) value * 2 - Math.round(8 * dp);
            }
            return value;
        };
        hooks.install(offset, hook::intercept);
        hooks.install(size, hook::intercept);
    }

    /** Shrinks the All-Apps drawer width when opened beside a 2-column sidebar so it never clips off-screen. */
    private void fitAllAppsDrawer() {
        try {
            Class<?> helper = Class.forName("com.miui.dock.allapps.x", false, loader);
            for (Method m : helper.getDeclaredMethods()) {
                if (!m.getName().equals("c") || m.getParameterCount() != 2) continue;
                hooks.install(m, chain -> {
                    Object res = chain.proceed();
                    if (res == null || !(chain.getArg(0) instanceof Context)) return res;
                    try {
                        Context ctx = (Context) chain.getArg(0);
                        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
                        float dp = ctx.getResources().getDisplayMetrics().density;
                        int dockId = ctx.getResources().getIdentifier("dock_panel_width", "dimen", "com.miui.securitycenter");
                        int dockW = dockId != 0 ? ctx.getResources().getDimensionPixelOffset(dockId) : Math.round(120 * dp);
                        int maxDrawerW = Math.max(Math.round(180 * dp), screenW - dockW - Math.round(32 * dp));
                        // Result object y(columns, width): clamp its int width field if wider than available space.
                        for (Field f : res.getClass().getDeclaredFields()) {
                            if (f.getType() == int.class) {
                                f.setAccessible(true);
                                int v = f.getInt(res);
                                if (v > maxDrawerW) f.setInt(res, maxDrawerW);
                            }
                        }
                    } catch (Throwable ignored) { }
                    return res;
                });
            }
        } catch (Throwable t) {
            hooks.error("sidebar all-apps width", t);
        }
    }

    private interface XposedFriendly {
        Object intercept(io.github.libxposed.api.XposedInterface.Chain chain) throws Throwable;
    }
}
