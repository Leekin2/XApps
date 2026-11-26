package io.github.leekin2.xsecurity;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lock screen kits inside HyperOS's lock screen editor (long-press the lock screen), as in kashi's
 * HyperOS AOD mod: the kits show in the preview under the clock, framed like the clock's own
 * parts. (−) removes a kit, a tap opens the "Add kit" sheet, a long press then a drag up or down
 * places the kits and a drag sideways reorders them. Written anew for XApps; the editor runs in
 * com.miui.aod's ":keyguardeditor" process, which cannot write XApps' settings, so every change
 * goes to System UI at once and to XApps (KitsProvider), which saves it.
 */
final class EditorKits {
    private static final String TAG = "xapps.editor.kits";
    private static final String XAPPS = "io.github.leekin2.xapps";
    private static List<String> order;
    private static int chosenY;
    private static boolean tipShown;
    /** A gesture that turned into a pinch or a page swipe, passed to the editor to its end. */
    private static boolean handedOff;
    /** The gesture's own DOWN, replayed to the editor when the gesture is handed to it. */
    private static MotionEvent firstDown;
    /** True while that DOWN is replayed, so it goes straight to the editor. */
    private static boolean replaying;
    /** Counts the edits made here, so a slower load never undoes one. */
    private static int edits;
    private static View.OnLayoutChangeListener layouts;
    private static java.lang.ref.WeakReference<View> layoutsOn = new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<Frame> current = new java.lang.ref.WeakReference<>(null);

    private EditorKits() {
    }

    static void install(Hooks hooks, ClassLoader loader) throws Throwable {
        order = LockKits.stored(Features.preferences());
        chosenY = Features.intValue(Features.AOD_KITS_Y, 0);
        LockKits.grid = LockKits.Grid.read(Features.preferences() == null ? null : Features.preferences().getString(Features.AOD_KITS_GRID, null));
        LockKits.square = Features.flag(Features.AOD_KITS_SQUARE, false);
        Class<?> fragment = Class.forName("com.miui.keyguard.editor.edit.EditFragment", false, loader);
        Method created = fragment.getDeclaredMethod("onViewCreated", View.class, android.os.Bundle.class);
        hooks.install(created, chain -> {
            Object result = chain.proceed();
            View view = (View) chain.getArg(0);
            reload(view.getContext());
            view.post(() -> {
                try {
                    ensure(view.getRootView());
                    // One listener at a time, on the newest editor view (its old one goes away with it).
                    View old = layoutsOn.get();
                    if (old != null && layouts != null) old.removeOnLayoutChangeListener(layouts);
                    View root = view.getRootView();
                    layouts = (v, l, t, r, b, ol, ot, or, ob) -> {
                        try { ensure(v); } catch (Throwable ignored) { }
                    };
                    root.addOnLayoutChangeListener(layouts);
                    layoutsOn = new java.lang.ref.WeakReference<>(root);
                } catch (Throwable t) { hooks.error("editor kits", t); }
            });
            return result;
        });
        // The editor handles touches over the preview itself; touches on the kits go to them.
        Class<?> combine = Class.forName("com.miui.keyguard.editor.edit.base.CombineEditView", false, loader);
        Method dispatch = combine.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        hooks.install(dispatch, chain -> {
            View self = (View) chain.getThisObject();
            MotionEvent event = (MotionEvent) chain.getArg(0);
            if (replaying) return chain.proceed();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                handedOff = false;
                if (firstDown != null) firstDown.recycle();
                firstDown = MotionEvent.obtain(event);
            }
            if (handedOff) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) handedOff = false;
                return chain.proceed();
            }
            Frame frame = Frame.tracking;
            if (frame != null && !frame.dragging && firstDown != null
                    && (action == MotionEvent.ACTION_POINTER_DOWN || (action == MotionEvent.ACTION_MOVE && frame.movedPastSlop(event)))) {
                // A second finger, or a finger that moves before the long press: a pinch or a
                // page swipe for the editor. It gets the gesture's own DOWN now, then the rest.
                frame.cancelTouch();
                handedOff = true;
                replaying = true;
                try { self.dispatchTouchEvent(firstDown); } finally { replaying = false; }
                return chain.proceed();
            }
            if (frame == null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                View found = self.findViewWithTag(TAG);
                if (found instanceof Frame && ((Frame) found).contains(event.getRawX(), event.getRawY())) frame = (Frame) found;
            }
            if (frame == null) return chain.proceed();
            frame.touch(event);
            return true;
        });
    }

    /** Puts the kits frame into the current template's frame layer, once. */
    private static void ensure(View root) {
        Context context = root.getContext();
        int id = context.getResources().getIdentifier("kg_editor_overlay_layer", "id", "com.miui.aod");
        if (id == 0) return;
        View layer = root.findViewById(id);
        if (!(layer instanceof FrameLayout) || layer.getWidth() <= 0) return;
        View mine = layer.findViewWithTag(TAG);
        Frame frame;
        if (mine instanceof Frame) {
            frame = (Frame) mine;
        } else {
            frame = new Frame(layer.getContext(), (FrameLayout) layer);
            frame.setTag(TAG);
            ((FrameLayout) layer).addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            frame.rebuild();
        }
        frame.place();
        if (!tipShown && frame.getHeight() > 0) {
            tipShown = true;
            frame.post(frame::tip);
        }
    }

    private static final java.util.concurrent.ExecutorService SAVER = java.util.concurrent.Executors.newSingleThreadExecutor();

    private static final android.net.Uri PROVIDER = android.net.Uri.parse("content://" + KitsProvider.AUTHORITY);

    /**
     * Reads the kits as XApps has them now: this process's own copy of the settings dates from
     * when it started, and the kits may have changed in XApps since.
     */
    static void reload(Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        int before = edits;
        SAVER.execute(() -> {
            android.os.Bundle out;
            try { out = app.getContentResolver().call(PROVIDER, KitsProvider.LOAD, null, null); } catch (Throwable t) { out = null; }
            if (out == null || !out.getBoolean("loaded")) return;
            List<String> list = new ArrayList<>();
            String text = out.getString("order", "");
            for (String id : text.split(",")) if (LockKits.known(id) && !list.contains(id)) list.add(id);
            int y = out.getInt("y", 0);
            LockKits.Grid grid = LockKits.Grid.read(out.getString("grid"));
            boolean square = out.getBoolean("square", LockKits.square);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (edits != before) return;
                Frame frame = current.get();
                if (frame != null && (frame.dragging || Frame.tracking == frame)) {
                    // Busy with a kit: look again once the finger is up.
                    frame.postDelayed(() -> reload(app), 600);
                    return;
                }
                if (list.equals(order) && y == chosenY && grid == LockKits.grid && square == LockKits.square) return;
                LockKits.grid = grid;
                LockKits.square = square;
                order = list;
                chosenY = y;
                if (frame != null && frame.isAttachedToWindow()) {
                    frame.rebuild();
                    frame.place();
                }
            });
        });
    }

    /** System UI shows the change at once; XApps keeps it (see KitsProvider). */
    private static void save(Context context, boolean withY) {
        edits++;
        String text = String.join(",", order);
        int y = chosenY;
        String grid = LockKits.grid.name();
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        SAVER.execute(() -> {
            boolean onAod = Features.flag(Features.AOD_KITS_ON_AOD, false), hideNotif = Features.flag(Features.AOD_KITS_HIDE_NOTIF, false);
            try {
                android.os.Bundle extras = new android.os.Bundle();
                extras.putString("order", text);
                extras.putString("grid", grid);
                // The place only when it was changed here, so a stale copy never overwrites it.
                if (withY) extras.putInt("y", y);
                android.os.Bundle out = app.getContentResolver().call(PROVIDER, KitsProvider.SAVE, null, extras);
                if (out != null && out.getBoolean("saved")) {
                    onAod = out.getBoolean("on_aod", onAod);
                    hideNotif = out.getBoolean("hide_notif", hideNotif);
                }
            } catch (Throwable ignored) { }
            try {
                Intent tell = new Intent(AodOverlay.ACTION_KITS).setPackage("com.android.systemui")
                        .putExtra("order", text).putExtra("grid", grid).putExtra("on_aod", onAod).putExtra("hide_notif", hideNotif);
                if (withY) tell.putExtra("y", y);
                app.sendBroadcast(tell);
            } catch (Throwable ignored) { }
        });
    }

    /** The kits in the preview: real kits drawn at the preview's scale, with a frame and badges. */
    static final class Frame extends FrameLayout {
        static Frame tracking;
        private final FrameLayout layer;
        private final LinearLayout kits;
        private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG), minus = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private float scale = 1f, density;
        private Context scaled;
        private float downX, downY, startTy;
        private boolean moved, dragging;
        private int removeAt = -1, dragAt = -1;
        private final Runnable longPress = this::startDrag;

        Frame(Context context, FrameLayout layer) {
            super(context);
            this.layer = layer;
            density = context.getResources().getDisplayMetrics().density;
            kits = new LinearLayout(context);
            addView(kits, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            setWillNotDraw(false);
            badge.setColor(Color.WHITE);
            minus.setColor(Color.BLACK);
            minus.setStrokeCap(Paint.Cap.ROUND);
        }

        private Boolean shownEditing;
        private float shownAlpha = -1f;
        private final android.view.ViewTreeObserver.OnPreDrawListener watch = () -> {
            // The editor fades its parts' frames in and out (edit / full preview) without a
            // layout pass: follow it every frame and redraw when it changes.
            boolean now = editing();
            float a = frameAlpha();
            if (shownEditing == null || shownEditing != now || Math.abs(a - shownAlpha) > 0.01f) {
                shownEditing = now;
                shownAlpha = a;
                invalidate();
            }
            return true;
        };

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            current = new java.lang.ref.WeakReference<>(this);
            getViewTreeObserver().addOnPreDrawListener(watch);
        }

        @Override
        protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(watch);
            super.onDetachedFromWindow();
        }

        /** The preview is the screen made smaller: draw the kits in a context with that density. */
        private Context scaledContext() {
            int screen = getResources().getDisplayMetrics().widthPixels;
            float s = layer.getWidth() > 0 && screen > 0 ? layer.getWidth() / (float) screen : 0.72f;
            if (scaled == null || Math.abs(s - scale) > 0.01f) {
                scale = s;
                Configuration c = new Configuration(getResources().getConfiguration());
                c.densityDpi = Math.max(80, Math.round(c.densityDpi * s));
                scaled = getContext().createConfigurationContext(c);
            }
            return scaled;
        }

        void rebuild() {
            LockKits.build(scaledContext(), kits, order, true);
            Drawable frame = null;
            int sig = getResources().getIdentifier("kg_signature_area_frame", "id", "com.miui.aod");
            View sample = sig == 0 ? null : layer.findViewById(sig);
            if (sample != null && sample.getBackground() != null && sample.getBackground().getConstantState() != null) {
                frame = sample.getBackground().getConstantState().newDrawable().mutate();
            }
            if (frame == null) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(0x14FFFFFF);
                g.setStroke(Math.max(1, Math.round(1.2f * density)), 0x99FFFFFF);
                g.setCornerRadius(16 * density * scale);
                frame = g;
            }
            setBackground(order.isEmpty() ? empty() : frame);
            int pad = Math.round(10 * density * scale);
            setPadding(pad, pad, pad, pad);
            setMinimumHeight(Math.round(56 * density * scale));
            invalidate();
        }

        /** An empty list still shows a frame to tap, with a plus. */
        private Drawable empty() {
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x14FFFFFF);
            g.setStroke(Math.max(1, Math.round(1.2f * density)), 0x99FFFFFF, 8 * density, 6 * density);
            g.setCornerRadius(16 * density * scale);
            return g;
        }

        /** The top for "right under the clock": below the clock's own frames. */
        private int autoTop() {
            int bottom = 0;
            for (String name : new String[] {"kg_time_area_frame", "kg_signature_area_frame", "kg_text_area_frame"}) {
                int id = getResources().getIdentifier(name, "id", "com.miui.aod");
                View v = id == 0 ? null : layer.findViewById(id);
                if (v != null && v.getVisibility() == VISIBLE && v.getHeight() > 0) bottom = Math.max(bottom, v.getBottom());
            }
            if (bottom == 0) bottom = Math.round(layer.getHeight() * 0.32f);
            return bottom + Math.round(8 * density * scale);
        }

        private int[] sides() {
            int id = getResources().getIdentifier("kg_time_area_frame", "id", "com.miui.aod");
            View v = id == 0 ? null : layer.findViewById(id);
            if (v != null && v.getWidth() > 0) return new int[] {v.getLeft(), v.getRight()};
            int m = Math.round(layer.getWidth() * 0.03f);
            return new int[] {m, layer.getWidth() - m};
        }

        void place() {
            if (dragging) return;
            int[] side = sides();
            int auto = autoTop();
            int top = chosenY > 0 ? Math.max(auto, Math.round(layer.getHeight() * Math.min(900, chosenY) / 1000f)) : auto;
            // Same limit as on the lock screen: the kits end above the bottom tenth.
            if (getHeight() > 0) top = Math.max(auto, Math.min(top, Math.round(layer.getHeight() * 0.9f) - getHeight()));
            LayoutParams lp = (LayoutParams) getLayoutParams();
            int width = side[1] - side[0];
            if (lp.leftMargin != side[0] || lp.topMargin != top || lp.width != width) {
                lp.leftMargin = side[0];
                lp.topMargin = top;
                lp.width = width;
                setLayoutParams(lp);
            }
        }

        /** How much the editor shrinks the template on screen (it scales the whole page). */
        private float screenScale() {
            android.graphics.Rect r = new android.graphics.Rect();
            if (layer.getWidth() > 0 && layer.getGlobalVisibleRect(r) && r.width() > 0) return r.width() / (float) layer.getWidth();
            return 1f;
        }

        boolean contains(float rawX, float rawY) {
            if (getVisibility() != VISIBLE || getWidth() == 0 || !editing()) return false;
            int[] at = new int[2];
            getLocationOnScreen(at);
            float k = screenScale(), pad = 10 * density;
            return rawX >= at[0] - pad && rawX <= at[0] + getWidth() * k + pad
                    && rawY >= at[1] - pad && rawY <= at[1] + getHeight() * k + pad;
        }

        /** The clock's own frame's fade, so ours looks the same. */
        private float frameAlpha() {
            int id = getResources().getIdentifier("kg_time_area_frame", "id", "com.miui.aod");
            View v = id == 0 ? null : layer.findViewById(id);
            return v == null ? 1f : Math.max(0f, Math.min(1f, v.getAlpha()));
        }

        /** The editor shows the parts' frames while editing, not in its full preview. */
        private boolean editing() {
            int id = getResources().getIdentifier("kg_time_area_frame", "id", "com.miui.aod");
            View v = id == 0 ? null : layer.findViewById(id);
            return v == null || (v.isShown() && v.getAlpha() > 0.05f);
        }

        private List<LockKits.Kit> kitViews() {
            List<LockKits.Kit> out = new ArrayList<>();
            collect(kits, out);
            return out;
        }

        private static void collect(View v, List<LockKits.Kit> out) {
            if (v instanceof LockKits.Kit) { out.add((LockKits.Kit) v); return; }
            if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collect(((ViewGroup) v).getChildAt(i), out);
        }

        /** A kit's box in this view's coordinates. */
        private float[] box(View kit) {
            float x = 0, y = 0;
            for (View v = kit; v != this && v != null; v = (View) v.getParent()) {
                x += v.getLeft() + v.getTranslationX();
                y += v.getTop() + v.getTranslationY();
                if (!(v.getParent() instanceof View)) break;
            }
            return new float[] {x, y, x + kit.getWidth(), y + kit.getHeight()};
        }

        private float badgeRadius() { return 9 * density * scale; }

        @Override
        public void draw(Canvas canvas) {
            boolean edit = editing();
            if (getBackground() != null) getBackground().setAlpha(edit ? Math.round(255 * frameAlpha()) : 0);
            super.draw(canvas);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!editing()) return;
            float r = badgeRadius();
            minus.setStrokeWidth(Math.max(1.5f, 2f * density * scale));
            for (LockKits.Kit kit : kitViews()) {
                float[] b = box(kit);
                float cx = b[2] - r * 0.6f, cy = b[1] + r * 0.6f;
                canvas.drawCircle(cx, cy, r, badge);
                canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, minus);
            }
            if (order.isEmpty()) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f, s = 10 * density * scale;
                minus.setColor(Color.WHITE);
                canvas.drawLine(cx - s, cy, cx + s, cy, minus);
                canvas.drawLine(cx, cy - s, cx, cy + s, minus);
                minus.setColor(Color.BLACK);
            }
        }

        private int indexAt(float x, float y, boolean badgeOnly) {
            List<LockKits.Kit> views = kitViews();
            float r = badgeRadius() * 1.8f;
            for (int i = 0; i < views.size(); i++) {
                float[] b = box(views.get(i));
                if (badgeOnly) {
                    float cx = b[2] - badgeRadius() * 0.6f, cy = b[1] + badgeRadius() * 0.6f;
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) return i;
                } else if (x >= b[0] && x <= b[2] && y >= b[1] - 20 && y <= b[3] + 20) {
                    return i;
                }
            }
            return -1;
        }

        private float[] local(MotionEvent e) {
            int[] at = new int[2];
            getLocationOnScreen(at);
            float k = screenScale();
            return new float[] {(e.getRawX() - at[0]) / k, (e.getRawY() - at[1]) / k};
        }

        /** The editor takes over this gesture (a second finger came down before the long press). */
        boolean movedPastSlop(MotionEvent e) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            return Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > slop;
        }

        void cancelTouch() {
            handler.removeCallbacks(longPress);
            tracking = null;
            moved = true;
            removeAt = -1;
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);
            // Back from XApps' kit page or from the background: take the kits as stored now.
            if (hasWindowFocus) reload(getContext());
        }

        void touch(MotionEvent e) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    tracking = this;
                    downX = e.getRawX();
                    downY = e.getRawY();
                    moved = false;
                    dragging = false;
                    startTy = getTranslationY();
                    float[] p = local(e);
                    removeAt = indexAt(p[0], p[1], true);
                    dragAt = indexAt(p[0], p[1], false);
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    float k = screenScale();
                    float dx = (e.getRawX() - downX) / k, dy = (e.getRawY() - downY) / k;
                    if (!dragging) {
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                            moved = true;
                            handler.removeCallbacks(longPress);
                        }
                        break;
                    }
                    LayoutParams lp = (LayoutParams) getLayoutParams();
                    float min = autoTop() - lp.topMargin, max = layer.getHeight() * 0.9f - getHeight() - lp.topMargin;
                    setTranslationY(Math.max(min, Math.min(max, startTy + dy)));
                    // Sideways: the held kit takes the place of the kit under the finger.
                    float[] p = local(e);
                    int over = indexAt(p[0], p[1] - getTranslationY() + startTy, false);
                    if (dragAt >= 0 && over >= 0 && over != dragAt && dragAt < order.size() && over < order.size()) {
                        String id = order.remove(dragAt);
                        order.add(over, id);
                        int total = 0;
                        for (List<String> r : LockKits.rows(order)) total += r.size();
                        if (total != order.size()) { order.remove(over); order.add(dragAt, id); }
                        else { dragAt = over; rebuild(); performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    handler.removeCallbacks(longPress);
                    tracking = null;
                    boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                    if (dragging) {
                        endDrag();
                    } else if (up && !moved) {
                        if (removeAt >= 0 && removeAt < order.size()) {
                            order.remove(removeAt);
                            rebuild();
                            save(getContext(), false);
                        } else {
                            sheet();
                        }
                    }
                    dragging = false;
                    break;
                }
                default:
                    break;
            }
        }

        private void startDrag() {
            if (moved) return;
            dragging = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            animate().scaleX(1.04f).scaleY(1.04f).alpha(0.9f).setDuration(120).start();
        }

        private void endDrag() {
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
            LayoutParams lp = (LayoutParams) getLayoutParams();
            int top = Math.round(lp.topMargin + getTranslationY());
            int auto = autoTop();
            chosenY = top <= auto + 4 * density ? 0 : Math.round(top * 1000f / Math.max(1, layer.getHeight()));
            setTranslationY(0);
            dragging = false;
            place();
            save(getContext(), true);
        }

        /** A short hint the first time, as in kashi's editor. */
        void tip() {
            TextView hint = new TextView(getContext());
            hint.setText(words()[0]);
            hint.setTextColor(Color.WHITE);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            int h = Math.round(12 * density), v = Math.round(8 * density);
            hint.setPadding(h, v, h, v);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xE6202124);
            bg.setCornerRadius(10 * density);
            hint.setBackground(bg);
            LayoutParams own = (LayoutParams) getLayoutParams();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(own.width, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = own.leftMargin;
            lp.topMargin = Math.max(0, own.topMargin - Math.round(52 * density));
            layer.addView(hint, lp);
            hint.postDelayed(() -> layer.removeView(hint), 4500);
        }

        /** The "Add kit" sheet over the bottom of the editor. */
        private void sheet() {
            Context context = getContext();
            Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            float dp = density;
            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = Math.round(18 * dp);
            box.setPadding(pad, Math.round(9 * dp), pad, 0);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xD9202025);
            bg.setCornerRadii(new float[] {28 * dp, 28 * dp, 28 * dp, 28 * dp, 0, 0, 0, 0});


            View grip = new View(context);
            GradientDrawable gripBg = new GradientDrawable();
            gripBg.setColor(0x66FFFFFF);
            gripBg.setCornerRadius(3 * dp);
            grip.setBackground(gripBg);
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Math.round(36 * dp), Math.round(4 * dp));
            gp.gravity = Gravity.CENTER_HORIZONTAL;
            gp.bottomMargin = Math.round(10 * dp);
            box.addView(grip, gp);

            FrameLayout head = new FrameLayout(context);
            TextView title = new TextView(context);
            title.setText(words()[1]);
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            head.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            TextView close = new TextView(context);
            close.setText("✕");
            close.setTextColor(Color.WHITE);
            close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            close.setPadding(Math.round(8 * dp), 0, Math.round(4 * dp), 0);
            close.setOnClickListener(v -> dialog.dismiss());
            head.addView(close, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.bottomMargin = Math.round(8 * dp);
            box.addView(head, hp);

            TextView layout = new TextView(context);
            layout.setText(LockKits.gridTitle() + "  " + LockKits.grid.label);
            layout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            layout.setTextColor(Color.WHITE);
            layout.setPadding(0, 0, 0, Math.round(10 * dp));
            layout.setOnClickListener(v -> LockKits.chooseGrid(context, order, selected -> {
                LockKits.grid = selected;
                layout.setText(LockKits.gridTitle() + "  " + selected.label);
                rebuild();
                place();
                save(context, false);
            }));
            box.addView(layout);
            LinearLayout catalog = new LinearLayout(context);
            catalog.setOrientation(LinearLayout.VERTICAL);
            catalog.setPadding(0, 0, 0, Math.round(22 * dp));
            List<LockKits.Kit> shown = new ArrayList<>();
            List<View> cells = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            LinearLayout row = null;
            int inRow = 0;
            for (String id : LockKits.EDITOR_CATALOG) {
                boolean wide = LockKits.wide(id);
                int per = wide ? 2 : 4;
                if (row == null || inRow >= per || (wide && row.getTag() == null) || (!wide && Boolean.TRUE.equals(row.getTag()))) {
                    row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setTag(wide ? Boolean.TRUE : null);
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rp.bottomMargin = Math.round(14 * dp);
                    catalog.addView(row, rp);
                    inRow = 0;
                }
                LinearLayout cell = new LinearLayout(context);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                LockKits.Kit kit = new LockKits.Kit(context, id);
                int size = Math.round(54 * dp);
                cell.addView(kit, new LinearLayout.LayoutParams(wide ? size * 2 + Math.round(16 * dp) : size, size));
                if (!wide) {
                    TextView name = new TextView(context);
                    name.setText(KitsActivity.name(id));
                    name.setTextColor(0xCCFFFFFF);
                    name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    name.setGravity(Gravity.CENTER);
                    name.setMaxLines(2);
                    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    np.topMargin = Math.round(6 * dp);
                    cell.addView(name, np);
                }
                cell.setOnClickListener(v -> {
                    if (order.contains(id)) { Toast.makeText(context, words()[2], Toast.LENGTH_SHORT).show(); return; }
                    if (!LockKits.fits(order, id)) { Toast.makeText(context, words()[3], Toast.LENGTH_SHORT).show(); return; }
                    order.add(id);
                    rebuild();
                    save(context, false);
                    dim(cells, ids);
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                });
                row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                shown.add(kit);
                cells.add(cell);
                ids.add(id);
                inRow++;
            }
            LockKits.fillKits(context, shown, true);
            dim(cells, ids);

            android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            scroll.setClipToPadding(false);
            scroll.addView(catalog);
            box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            dialog.setContentView(box);
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(bg);
                window.setGravity(Gravity.BOTTOM);
                int screen = context.getResources().getDisplayMetrics().heightPixels;
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(screen * 0.48f));
                window.setDimAmount(0.09f);
                window.setBackgroundBlurRadius(Math.round(18 * dp));
            }
        }

        private static void dim(List<View> cells, List<String> ids) {
            for (int i = 0; i < cells.size(); i++) cells.get(i).setAlpha(order.contains(ids.get(i)) ? 0.35f : 1f);
        }

        /** Tip, title, already added, no space. */
        private static String[] words() {
            switch (Locale.getDefault().getLanguage()) {
                case "tr": return new String[] {"Bir kite uzun basın, sonra kitleri yerleştirmek için yukarı ya da aşağı sürükleyin. Sıralamak için yana sürükleyin.",
                        "Kit ekle", "Bu kit zaten ekli", "Yer kalmadı, önce bir kit kaldırın"};
                case "ru": return new String[] {"Удерживайте виджет, затем перетащите вверх или вниз, чтобы разместить виджеты. Перетащите вбок, чтобы изменить порядок.",
                        "Добавить виджет", "Этот виджет уже добавлен", "Нет места, сначала удалите виджет"};
                case "es": return new String[] {"Mantén pulsado un kit y arrastra arriba o abajo para colocar los kits. Arrastra de lado para reordenar.",
                        "Añadir kit", "Este kit ya está añadido", "No hay espacio, quita un kit primero"};
                case "zh": return new String[] {"长按小组件，然后上下拖动以放置小组件。左右拖动可调整顺序。",
                        "添加小组件", "已添加此小组件", "没有空间了，请先移除一个"};
                default: return new String[] {"Long-press a kit, then drag up or down to place the kits. Drag sideways to reorder.",
                        "Add kit", "This kit is already added", "No space left, remove a kit first"};
            }
        }
    }
}
