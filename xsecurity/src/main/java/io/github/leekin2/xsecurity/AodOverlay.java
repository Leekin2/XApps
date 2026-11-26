package io.github.leekin2.xsecurity;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Method;

/**
 * Extras drawn on the always-on display, as in kashi's HyperOS AOD mod: an edge glow when a
 * notification arrives, the charging state and watts while plugged in, and the song playing.
 * They sit in one layer over the AOD and move a little at every time update, against burn-in.
 * Written anew.
 */
final class AodOverlay {
    static final int[] GLOW_COLORS = {0xFF3D8BFF, 0xFF22D3EE, 0xFF34D399, 0xFF6366F1, 0xFFFB923C,
            0xFFF472B6, 0xFFA855F7, 0xFFEF4444, 0xFF14B8A6, 0xFFFACC15};
    private static final String TAG = "xsecurity.aod.overlay";
    private static volatile java.lang.ref.WeakReference<Layer> keyguardLayer;
    /** The lock screen clock's bottom as System UI computes it, without the kits. */
    static volatile int clockBase = -1;
    /** Room the lock screen kits take under the clock; the notifications start below it. */
    static volatile int kitsExtra;
    /** The PIN/pattern screen is up (or coming): the kits step aside for it. */
    static volatile boolean bouncer;
    /** Sent by XApps' kit picker; System UI's copy of the settings does not change by itself. */
    static final String ACTION_KITS = "io.github.leekin2.xapps.LOCK_KITS";
    static final String KITS_PERMISSION = "io.github.leekin2.xapps.permission.LOCK_KITS";
    static volatile java.util.List<String> liveOrder;
    static volatile Boolean liveOnAod, liveHideNotif;
    static volatile Integer liveY;
    static volatile Boolean liveEnabled;

    /** The kits switch: the live value from XApps when it changed since System UI started. */
    static boolean kitsOn() {
        Boolean live = liveEnabled;
        return live != null ? live : Features.enabled(Features.AOD_KITS);
    }
    private final Hooks hooks;
    private final ClassLoader loader;

    AodOverlay(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    void install() throws Throwable {
        readKitAppearance();
        Class<?> view = Class.forName("com.miui.aod.AODView", false, loader);
        Method attach = view.getDeclaredMethod("onAttachedToWindow");
        Method time = view.getDeclaredMethod("handleUpdateTime");
        Method notify = view.getDeclaredMethod("scheduleNotificationAnimation");
        hooks.install(attach, chain -> {
            Object result = chain.proceed();
            try { layer((ViewGroup) chain.getThisObject()); hooks.info("AOD overlay attached"); } catch (Throwable t) { hooks.error("AOD overlay", t); }
            return result;
        });
        hooks.install(time, chain -> {
            Object result = chain.proceed();
            try { Layer l = layerOf((ViewGroup) chain.getThisObject()); if (l != null) l.refresh(true); } catch (Throwable t) { hooks.error("AOD overlay refresh", t); }
            return result;
        });
        if (Features.enabled(Features.AOD_GLOW)) hooks.install(notify, chain -> {
            Object result = chain.proceed();
            try {
                Layer l = layerOf((ViewGroup) chain.getThisObject());
                if (l != null) l.glow();
                java.lang.ref.WeakReference<Layer> ref = keyguardLayer;
                Layer kg = ref == null ? null : ref.get();
                if (kg != null && kg != l && kg.isAttachedToWindow()) kg.glow();
            } catch (Throwable t) { hooks.error("AOD glow", t); }
            return result;
        });
    }

    private static void readKitAppearance() {
        android.content.SharedPreferences prefs = Features.preferences();
        LockKits.grid = LockKits.Grid.read(prefs == null ? null : prefs.getString(Features.AOD_KITS_GRID, null));
        LockKits.square = Features.flag(Features.AOD_KITS_SQUARE, false);
    }

    private static Layer layerOf(ViewGroup aod) {
        View v = aod.findViewWithTag(TAG);
        return v instanceof Layer ? (Layer) v : null;
    }

    /**
     * For System UI's full-screen AOD and lock screen: HyperOS keeps the lock screen
     * (HyperOSKeyguardRootView) visible in doze while AodView stays 0x0 and transparent.
     */
    static void installKeyguard(Hooks hooks, ClassLoader systemUi) throws Throwable {
        readKitAppearance();
        Class<?> view = Class.forName("com.android.keyguard.widget.HyperOSKeyguardRootView", false, systemUi);
        Method setVis = view.getDeclaredMethod("setVisibility", int.class);
        // The notifications on the lock screen start at the clock's bottom: with kits under the
        // clock, they start below the kits instead (as in kashi's lock screen kits).
        try {
            Class<?> clock = Class.forName("com.android.keyguard.clock.KeyguardClockContainer", false, systemUi);
            Method bottom = clock.getDeclaredMethod("getClockBottom");
            if (bottom.getReturnType() == int.class) hooks.install(bottom, chain -> {
                Object result = chain.proceed();
                if (!(result instanceof Integer)) return result;
                int base = (Integer) result;
                if (base > 0) clockBase = base;
                return base > 0 ? base + kitsExtra : base;
            });
        } catch (Throwable t) { hooks.error("lock screen kits: clock position unavailable", t); }
        try {
            Class<?> manager = Class.forName("com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager", false, systemUi);
            Method update = manager.getDeclaredMethod("updateStates");
            Method showing = manager.getDeclaredMethod("primaryBouncerIsOrWillBeShowing");
            if (showing.getReturnType() == boolean.class) hooks.install(update, chain -> {
                Object result = chain.proceed();
                boolean now = (boolean) showing.invoke(chain.getThisObject());
                if (now != bouncer) {
                    bouncer = now;
                    java.lang.ref.WeakReference<Layer> ref = keyguardLayer;
                    Layer l = ref == null ? null : ref.get();
                    if (l != null) l.refreshLockKits();
                }
                return result;
            });
        } catch (Throwable t) { hooks.error("lock screen kits: PIN screen state unavailable", t); }
        hooks.install(setVis, chain -> {
            Object result = chain.proceed();
            if (Integer.valueOf(View.VISIBLE).equals(chain.getArg(0))) {
                ViewGroup root = (ViewGroup) chain.getThisObject();
                try {
                    boolean added = layerOf(root) == null;
                    Layer l = layer(root);
                    l.keyguard = true;
                    l.refresh(false);
                    keyguardLayer = new java.lang.ref.WeakReference<>(l);
                    if (added) {
                        root.post(() -> hooks.info("full screen AOD overlay on " + root.getClass().getSimpleName()
                                + " " + root.getWidth() + "x" + root.getHeight() + " shown=" + root.isShown()));
                    }
                } catch (Throwable t) { hooks.error("full screen AOD overlay", t); }
            }
            return result;
        });
    }

    /** Gives the layer the parent's exact size (see layer()). */
    private static void fit(Layer layer, int width, int height) {
        if (width <= 0 || height <= 0) return;
        ViewGroup.LayoutParams lp = layer.getLayoutParams();
        if (lp == null || (lp.width == width && lp.height == height)) return;
        lp.width = width;
        lp.height = height;
        layer.post(() -> layer.setLayoutParams(lp));
    }

    private static Layer layer(ViewGroup aod) {
        Layer layer = layerOf(aod);
        if (layer == null) {
            layer = new Layer(aod.getContext());
            layer.setId(View.generateViewId());
            layer.setTag(TAG);
            aod.addView(layer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // The lock screen root is a ConstraintLayout, where MATCH_PARENT without constraints
            // measures to 0 wide and the layer never showed. The layer follows the root's size
            // in pixels instead, which every parent type honours.
            Layer added = layer;
            View.OnLayoutChangeListener follow = (v, l, t, r, b, ol, ot, or, ob) -> fit(added, r - l, b - t);
            aod.addOnLayoutChangeListener(follow);
            fit(layer, aod.getWidth(), aod.getHeight());
        }
        layer.bringToFront();
        layer.refresh(false);
        return layer;
    }

    /** One full-screen layer: glow drawn on the canvas, text rows at the bottom. */
    static final class Layer extends FrameLayout {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final LinearLayout bottom;
        private final TextView playing;
        private final LinearLayout kits;
        private final TextView charging;
        private float glow;
        private ValueAnimator pulse;
        private int shift;
        /** The lock screen's layer (not the AOD plugin's): it carries the lock screen kits. */
        boolean keyguard;
        private final LinearLayout lockKits;
        private String shownKits = "";

        Layer(Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(false);
            float dp = getResources().getDisplayMetrics().density;
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(5 * dp);
            int color = GLOW_COLORS[Math.max(0, Math.min(GLOW_COLORS.length - 1, Features.intValue(Features.AOD_GLOW_COLOR, 0)))];
            glowPaint.setColor(color);
            glowPaint.setShadowLayer(14 * dp, 0, 0, color);

            bottom = new LinearLayout(context);
            bottom.setOrientation(LinearLayout.VERTICAL);
            bottom.setGravity(Gravity.CENTER_HORIZONTAL);
            playing = row(context);
            charging = row(context);
            kits = new LinearLayout(context);
            kits.setOrientation(LinearLayout.HORIZONTAL);
            kits.setGravity(Gravity.CENTER);
            kits.setVisibility(GONE);
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kp.bottomMargin = (int) (10 * dp);
            bottom.addView(kits, kp);
            bottom.addView(playing);
            bottom.addView(charging);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            lp.bottomMargin = (int) (Features.intValue(Features.AOD_CHARGING_POS, 120) * dp);
            addView(bottom, lp);

            lockKits = new LinearLayout(context);
            lockKits.setOrientation(LinearLayout.VERTICAL);
            lockKits.setVisibility(GONE);
            addView(lockKits, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        }

        /**
         * Lock screen kits under the clock: on the lock screen, and on the AOD only when chosen;
         * hidden while notifications show if chosen. Rebuilt only when the chosen kits change.
         */
        void refreshLockKits() {
            if (!kitsOn() || bouncer) { hideLockKits(); return; }
            Context context = getContext();
            Boolean onAod = liveOnAod, hideNotif = liveHideNotif;
            boolean aodToo = onAod != null ? onAod : Features.flag(Features.AOD_KITS_ON_AOD, false);
            boolean show;
            if (keyguard) {
                // Only while locked: the unlocked notification shade shows the same clock.
                try {
                    android.app.KeyguardManager km = context.getSystemService(android.app.KeyguardManager.class);
                    if (km != null && !km.isKeyguardLocked()) { hideLockKits(); return; }
                } catch (Throwable ignored) { }
                boolean screenOn;
                try { screenOn = context.getSystemService(android.os.PowerManager.class).isInteractive(); } catch (Throwable t) { screenOn = true; }
                show = screenOn || aodToo;
            } else {
                // The AOD app's own screen: kits only when chosen for the AOD.
                show = aodToo;
            }
            // "Hide while notifications show": the cards the lock screen really draws (the AOD
            // app's own screen asks the notification service instead).
            if (show && hideWithNotifications() && (keyguard ? visibleCards() > 0 : hasNotifications(context))) show = false;
            java.util.List<String> live = liveOrder;
            java.util.List<String> order = live != null ? live : LockKits.stored(Features.preferences());
            if (!show || order.isEmpty()) { hideLockKits(); return; }
            String key = LockKits.grid.name() + ":" + LockKits.square + ":" + String.join(",", order);
            if (!key.equals(shownKits) || lockKits.getChildCount() == 0) {
                LockKits.build(context, lockKits, order, false);
                shownKits = key;
            } else {
                java.util.List<LockKits.Kit> views = new java.util.ArrayList<>();
                collectKits(lockKits, views);
                LockKits.fillKits(context, views, false);
            }
            lockKits.setVisibility(VISIBLE);
            post(this::placeLockKits);
        }

        private boolean hideWithNotifications() {
            Boolean live = liveHideNotif;
            return live != null ? live : Features.flag(Features.AOD_KITS_HIDE_NOTIF, false);
        }

        /** How many notification cards the lock screen shows now. */
        private int visibleCards() {
            if (!(getParent() instanceof ViewGroup)) return 0;
            View stack = stack((ViewGroup) getParent());
            if (!(stack instanceof ViewGroup) || !stack.isShown()) return 0;
            ViewGroup list = (ViewGroup) stack;
            int count = 0;
            for (int i = 0; i < list.getChildCount(); i++) {
                View card = list.getChildAt(i);
                if (card.getVisibility() == VISIBLE && card.getAlpha() >= 0.05f && card.getHeight() > 0
                        && card.getClass().getName().endsWith("ExpandableNotificationRow")) count++;
            }
            return count;
        }

        private void hideLockKits() {
            lockKits.setVisibility(GONE);
            // Only the lock screen's own layer owns the notifications' offset.
            if (keyguard) setExtra(0);
        }

        /** Tells System UI the notifications' new start (it lays them out again on root layout). */
        private void setExtra(int extra) {
            if (kitsExtra == extra) return;
            kitsExtra = extra;
            if (getParent() instanceof View) ((View) getParent()).requestLayout();
        }

        /** Redraws every kit (the shape changed). */
        private static void invalidateKits(View v) {
            if (v instanceof LockKits.Kit) { v.invalidate(); return; }
            if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) invalidateKits(((ViewGroup) v).getChildAt(i));
        }

        private static void collectKits(View v, java.util.List<LockKits.Kit> out) {
            if (v instanceof LockKits.Kit) { out.add((LockKits.Kit) v); return; }
            if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectKits(((ViewGroup) v).getChildAt(i), out);
        }

        /** Just below the lock screen clock (the lowest visible clock view in the top half). */
        private void placeLockKits() {
            if (!(getParent() instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) getParent();
            int height = getHeight() > 0 ? getHeight() : root.getHeight();
            int[] origin = new int[2];
            getLocationInWindow(origin);
            int[] bottom = {-1};
            findClockBottom(root, height / 2 + origin[1], bottom);
            float dp = getResources().getDisplayMetrics().density;
            int gap = Math.round(14 * dp);
            int base = clockBase;
            // Below whichever is lower: where System UI starts the notifications, or the clock's
            // own drawn bottom (big clock styles draw past the value System UI reports).
            int seen = bottom[0] > 0 ? bottom[0] - origin[1] : -1;
            int top = Math.max(base, seen) > 0 ? Math.max(base, seen) + gap : Math.round(height * 0.32f);
            // Placed by hand in the lock screen editor (never above the clock).
            Integer chosenY = liveY;
            int y = chosenY != null ? chosenY : Features.intValue(Features.AOD_KITS_Y, 0);
            if (y > 0) top = Math.max(top, Math.round(height * Math.min(900, y) / 1000f));
            int extra = 0;
            if (keyguard && lockKits.getVisibility() == VISIBLE) {
                int auto = Math.max(base, seen) > 0 ? Math.max(base, seen) + gap : Math.round(height * 0.32f);
                lockKits.measure(MeasureSpec.makeMeasureSpec(getWidth() > 0 ? getWidth() : height, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int h = lockKits.getMeasuredHeight();
                // Never past the bottom tenth (shortcuts, fingerprint), as in the editor.
                top = Math.max(auto, Math.min(top, Math.round(height * 0.9f) - h));
                // The notifications' offset follows only from where the kits want to be, never from
                // where the cards ended up, so the two never chase each other. High on the screen
                // the notifications move down below the kits; low, they stay where they are.
                if (top + h + gap <= height * 0.75f) extra = pushFor(top + h + gap, base, h, gap);
                // System UI keeps the cards above a limit of its own: wherever the first card is,
                // the kits sit just above it (never above the clock).
                int card = firstCardTop(root, origin[1]);
                if (card >= 0 && top + h + gap > card) top = Math.max(auto, card - gap - h);
            }
            LayoutParams lp = (LayoutParams) lockKits.getLayoutParams();
            // A few pixels of drift from the cards' own animations do not move the kits.
            if (Math.abs(lp.topMargin - top) > Math.round(2 * dp)) {
                lp.topMargin = top;
                lockKits.setLayoutParams(lp);
            }
            if (keyguard && android.util.Log.isLoggable("XAodKits", android.util.Log.DEBUG)) {
                android.util.Log.d("XAodKits", "top=" + top + " h=" + lockKits.getMeasuredHeight() + " realH=" + lockKits.getHeight()
                        + " base=" + base + " seen=" + seen + " card=" + firstCardTop(root, origin[1]) + " extra=" + kitsExtra + "->" + extra + " H=" + height);
            }
            if (keyguard) setExtra(lockKits.getVisibility() == VISIBLE ? extra : 0);
        }

        private static int pushFor(int end, int base, int h, int gap) {
            return base > 0 ? Math.max(0, end - base) : h + 2 * gap;
        }

        private java.lang.ref.WeakReference<View> watchedStack = new java.lang.ref.WeakReference<>(null);
        private long placedAt;
        private Boolean hadCards;
        /** Cards come and go without a layout of this view: look again at most twice a second. */
        private java.lang.ref.WeakReference<View> clockView = new java.lang.ref.WeakReference<>(null);

        /**
         * How visible the lock screen clock is now: it fades out when the shade comes down over
         * the lock screen, and the kits fade with it.
         */
        private float clockAlpha() {
            View clock = clockView.get();
            if (clock == null || !clock.isAttachedToWindow()) {
                clock = findByName(getRootView(), "KeyguardClockContainer");
                clockView = new java.lang.ref.WeakReference<>(clock);
                if (clock == null) return 1f;
            }
            float alpha = 1f;
            for (View v = clock; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
                if (v.getVisibility() != VISIBLE) return 0f;
                alpha *= v.getAlpha();
            }
            return alpha;
        }

        private final android.view.ViewTreeObserver.OnPreDrawListener follow = () -> {
            if (!keyguard || !kitsOn() || !isShown()) return true;
            boolean shown = Layer.this.lockKits.getVisibility() == VISIBLE;
            if (shown) {
                try {
                    float alpha = clockAlpha();
                    if (Math.abs(Layer.this.lockKits.getAlpha() - alpha) > 0.01f) Layer.this.lockKits.setAlpha(alpha);
                } catch (Throwable ignored) { }
            }
            if (!shown && !hideWithNotifications()) return true;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - placedAt <= 500) {
                // One more look once the window ends, so the last frame of an animation counts.
                if (!Layer.this.trailing) {
                    Layer.this.trailing = true;
                    postDelayed(Layer.this.again, 500 - (now - placedAt) + 16);
                }
                return true;
            }
            placedAt = now;
            post(Layer.this.check);
            return true;
        };
        private boolean trailing;
        private final Runnable again = () -> {
            trailing = false;
            if (!keyguard || !kitsOn()) return;
            placedAt = android.os.SystemClock.uptimeMillis();
            Layer.this.check.run();
        };
        /** Runs from a posted callback inside System UI, so nothing may escape it. */
        private final Runnable check = () -> {
            try {
                if (!keyguard || !kitsOn()) return;
                if (hideWithNotifications()) {
                    boolean has = visibleCards() > 0;
                    if (hadCards == null || hadCards != has) {
                        hadCards = has;
                        refreshLockKits();
                        return;
                    }
                }
                if (Layer.this.lockKits.getVisibility() == VISIBLE) placeLockKits();
            } catch (Throwable t) {
                android.util.Log.w("XAod", "Lock kits check failed", t);
            }
        };

        /**
         * The top of the first notification card shown below the kits' own top (in this view's
         * coordinates), or -1.
         */
        private View stack(ViewGroup root) {
            View stack = watchedStack.get();
            if (stack == null || !stack.isAttachedToWindow()) {
                stack = findByName(root.getRootView(), "NotificationStackScrollLayout");
                watchedStack = new java.lang.ref.WeakReference<>(stack);
            }
            return stack;
        }

        private int firstCardTop(ViewGroup root, int originY) {
            View stack = stack(root);
            if (!(stack instanceof ViewGroup) || !stack.isShown()) return -1;
            int best = -1;
            ViewGroup list = (ViewGroup) stack;
            int[] at = new int[2];
            for (int i = 0; i < list.getChildCount(); i++) {
                View card = list.getChildAt(i);
                if (card.getVisibility() != VISIBLE || card.getAlpha() < 0.05f || card.getHeight() <= 0
                        || !card.getClass().getName().endsWith("ExpandableNotificationRow")) continue;
                card.getLocationInWindow(at);
                int top = Math.round(at[1] - originY);
                if (best < 0 || top < best) best = top;
            }
            return best;
        }

        private static View findByName(View v, String simpleName) {
            if (v.getClass().getName().endsWith(simpleName)) return v;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View found = findByName(g.getChildAt(i), simpleName);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private void findClockBottom(View v, int limit, int[] bottom) {
            if (v == this || v.getVisibility() != VISIBLE || v.getAlpha() <= 0.01f) return;
            String name = v.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            String id = "";
            if (v.getId() != View.NO_ID) {
                try { id = v.getResources().getResourceEntryName(v.getId()).toLowerCase(java.util.Locale.ROOT); } catch (Throwable ignored) { }
            }
            if ((name.contains("clock") || id.contains("clock")) && v.getHeight() > 60 && v.getHeight() < limit) {
                int[] at = new int[2];
                v.getLocationInWindow(at);
                int b = at[1] + v.getHeight();
                if (at[1] < limit && b > bottom[0] && b < limit + v.getHeight()) bottom[0] = b;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) findClockBottom(g.getChildAt(i), limit, bottom);
            }
        }

        /** Whether the lock screen has notifications a person would see (clearable ones). */
        private static boolean hasNotifications(Context context) {
            try {
                Object service = android.app.NotificationManager.class.getMethod("getService").invoke(null);
                Object list = service.getClass().getMethod("getActiveNotificationsWithAttribution", String.class, String.class)
                        .invoke(service, context.getPackageName(), null);
                if (!(list instanceof android.service.notification.StatusBarNotification[])) return false;
                for (android.service.notification.StatusBarNotification sbn : (android.service.notification.StatusBarNotification[]) list) {
                    if (sbn.isClearable() && !sbn.isOngoing()) return true;
                }
            } catch (Throwable ignored) { }
            return false;
        }

        private static TextView row(Context context) {
            TextView t = new TextView(context);
            t.setTextColor(0xCCFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            int pad = (int) (24 * context.getResources().getDisplayMetrics().density);
            t.setPadding(pad, 0, pad, 0);
            t.setVisibility(GONE);
            return t;
        }

        private android.content.BroadcastReceiver ticks, kitChanges;

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ticks = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    boolean timeTick = Intent.ACTION_TIME_TICK.equals(i.getAction());
                    // Do nothing on minute ticks while the phone is unlocked and this layer is hidden.
                    if (timeTick && !isShown()) return;
                    try { refresh(timeTick); } catch (Throwable ignored) { }
                }
            };
            // System broadcasts only: they reach a receiver that is not exported.
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            try { getContext().registerReceiver(ticks, filter, Context.RECEIVER_NOT_EXPORTED); } catch (Throwable ignored) { ticks = null; }
            // Kit changes from XApps' picker and the lock screen editor; only senders holding
            // XApps' signature permission (XApps itself, and the system) get through.
            kitChanges = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    try {
                        String order = i.getStringExtra("order");
                        if (order != null) {
                            java.util.List<String> list = new java.util.ArrayList<>();
                            for (String id : order.split(",")) if (LockKits.known(id) && !list.contains(id)) list.add(id);
                            liveOrder = list;
                        }
                        if (i.hasExtra("y")) liveY = Math.max(0, Math.min(900, i.getIntExtra("y", 0)));
                        if (i.hasExtra("on_aod")) liveOnAod = i.getBooleanExtra("on_aod", false);
                        if (i.hasExtra("hide_notif")) liveHideNotif = i.getBooleanExtra("hide_notif", false);
                        if (i.hasExtra("enabled")) liveEnabled = i.getBooleanExtra("enabled", false);
                        if (i.hasExtra("grid")) LockKits.grid = LockKits.Grid.read(i.getStringExtra("grid"));
                        if (i.hasExtra("square")) {
                            boolean square = i.getBooleanExtra("square", false);
                            if (square != LockKits.square) {
                                LockKits.square = square;
                                invalidateKits(Layer.this);
                            }
                        }
                        refresh(false);
                    } catch (Throwable ignored) { }
                }
            };
            try {
                getContext().registerReceiver(kitChanges, new IntentFilter(ACTION_KITS), KITS_PERMISSION, null, Context.RECEIVER_EXPORTED);
            } catch (Throwable ignored) { kitChanges = null; }
            getViewTreeObserver().addOnPreDrawListener(follow);
        }

        @Override protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(follow);
            removeCallbacks(again);
            removeCallbacks(check);
            trailing = false;
            if (ticks != null) try { getContext().unregisterReceiver(ticks); } catch (Throwable ignored) { }
            if (kitChanges != null) try { getContext().unregisterReceiver(kitChanges); } catch (Throwable ignored) { }
            ticks = null;
            kitChanges = null;
            super.onDetachedFromWindow();
        }

        /**
         * The rows must never cover the under-display fingerprint sensor (its circle shows on
         * the AOD and lock screen). Xiaomi publishes the sensor box in
         * persist.vendor.sys.fp.fod.location.X_Y and .size.width_height; when the rows at the
         * chosen height would overlap it, they move just below it, or above it if there is no room.
         */
        private static boolean fodLoaded;
        private static int[] fodXy, fodWh;

        void dodgeFingerprint(int wantMargin) {
            try {
                int h = bottom.getHeight(), screen = getHeight();
                if (h <= 0 || screen <= 0 || !(bottom.getLayoutParams() instanceof LayoutParams)) return;
                if (!fodLoaded) {
                    fodXy = pair(prop("persist.vendor.sys.fp.fod.location.X_Y"));
                    fodWh = pair(prop("persist.vendor.sys.fp.fod.size.width_height"));
                    fodLoaded = true;
                }
                int[] xy = fodXy, wh = fodWh;
                int margin = wantMargin;
                if (xy != null && wh != null) {
                    int pad = Math.round(24 * getResources().getDisplayMetrics().density);
                    int fodTop = xy[1] - pad, fodBottom = xy[1] + wh[1] + pad;
                    int top = screen - wantMargin - h, bot = screen - wantMargin;
                    if (top < fodBottom && bot > fodTop) {
                        int below = screen - fodBottom - h;
                        margin = below >= pad ? below : screen - fodTop;
                    }
                }
                LayoutParams lp = (LayoutParams) bottom.getLayoutParams();
                if (lp.bottomMargin != margin) {
                    lp.bottomMargin = margin;
                    bottom.setLayoutParams(lp);
                }
            } catch (Throwable ignored) {
            }
        }

        private static String prop(String key) {
            try {
                return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
            } catch (Throwable t) {
                return null;
            }
        }

        private static int[] pair(String value) {
            if (value == null) return null;
            String[] p = value.split(",");
            if (p.length != 2) return null;
            try { return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())}; }
            catch (NumberFormatException e) { return null; }
        }

        void refresh(boolean move) {
            Context context = getContext();
            float dp = getResources().getDisplayMetrics().density;
            int color = GLOW_COLORS[Math.max(0, Math.min(GLOW_COLORS.length - 1, Features.intValue(Features.AOD_GLOW_COLOR, 0)))];
            glowPaint.setColor(color);
            glowPaint.setShadowLayer(14 * dp, 0, 0, color);
            ViewGroup.LayoutParams blp = bottom.getLayoutParams();
            if (blp instanceof LayoutParams) {
                int wantMargin = (int) (Features.intValue(Features.AOD_CHARGING_POS, 120) * dp);
                if (((LayoutParams) blp).bottomMargin != wantMargin) {
                    ((LayoutParams) blp).bottomMargin = wantMargin;
                    bottom.setLayoutParams(blp);
                }
                // Once measured, step aside from the under-display fingerprint sensor.
                bottom.post(() -> dodgeFingerprint(wantMargin));
            }
            // Charging: level, state and watts.
            if (Features.enabled(Features.AOD_CHARGING)) {
                Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int status = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                boolean plugged = battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                if (plugged) {
                    int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                    long micro = context.getSystemService(BatteryManager.class).getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    double milliAmps = Math.abs(micro) > 20_000 ? micro / 1000.0 : micro;
                    double watts = Math.abs(milliAmps) * battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1_000_000.0;
                    String state = status == BatteryManager.BATTERY_STATUS_FULL ? words()[1] : words()[0];
                    charging.setText(String.format(java.util.Locale.getDefault(), "⚡ %%%d · %s%s", level, state,
                            watts >= 0.1 && status != BatteryManager.BATTERY_STATUS_FULL ? String.format(java.util.Locale.getDefault(), " · %.1f W", watts) : ""));
                    charging.setVisibility(VISIBLE);
                } else {
                    charging.setVisibility(GONE);
                }
            } else {
                charging.setVisibility(GONE);
            }
            // Now playing.
            if (Features.enabled(Features.AOD_NOW_PLAYING)) {
                CharSequence song = null;
                android.graphics.Bitmap cover = null;
                try {
                    MediaSessionManager sessions = context.getSystemService(MediaSessionManager.class);
                    for (MediaController c : sessions.getActiveSessions(null)) {
                        PlaybackState s = c.getPlaybackState();
                        MediaMetadata m = c.getMetadata();
                        if (s == null || s.getState() != PlaybackState.STATE_PLAYING || m == null) continue;
                        CharSequence title = m.getText(MediaMetadata.METADATA_KEY_TITLE);
                        CharSequence artist = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
                        if (TextUtils.isEmpty(title)) continue;
                        song = TextUtils.isEmpty(artist) ? title : title + " · " + artist;
                        cover = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                        if (cover == null) cover = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                        break;
                    }
                } catch (Throwable ignored) { }
                int style = Features.intValue(Features.AOD_NOW_PLAYING_STYLE, 0);
                playing.setText(song == null ? "" : (style == 0 || cover == null ? "♪ " : "") + song);
                playing.setVisibility(song == null ? GONE : VISIBLE);
                styleSong(style, song, cover);
            } else {
                playing.setVisibility(GONE);
                lastSongKey = null;
            }
            // The glass kits replace the old text kits everywhere, and their battery kit the
            // charging line, as in kashi's lock screen kits.
            kits.setVisibility(GONE);
            if (kitsOn()) charging.setVisibility(GONE);
            refreshLockKits();
            bottom.setVisibility(keyguard && kitsOn() ? GONE : VISIBLE);
            if (move) {
                // A few pixels each time the clock updates, like the AOD's own content.
                shift = (shift + 1) % 5;
                bottom.setTranslationX((shift - 2) * 3 * dp);
                bottom.setTranslationY(((shift * 7) % 5 - 2) * 3 * dp);
            }
        }

        private String lastSongKey;

        /** 0 text only; 1 pill with a round cover; 2 rounded square with a square cover. */
        private void styleSong(int style, CharSequence song, android.graphics.Bitmap cover) {
            if (style == 0 || cover == null) {
                lastSongKey = null;
                playing.setCompoundDrawables(null, null, null, null);
                playing.setBackground(null);
                return;
            }
            String key = style + ":" + song + ":" + System.identityHashCode(cover);
            if (key.equals(lastSongKey)) return;
            lastSongKey = key;
            float dp = getResources().getDisplayMetrics().density;
            int size = (int) (28 * dp);
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(cover, size, size, true);
            android.graphics.Bitmap round = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(round);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new android.graphics.BitmapShader(scaled, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
            float r = style == 1 ? size / 2f : 6 * dp;
            c.drawRoundRect(new RectF(0, 0, size, size), r, r, p);
            android.graphics.drawable.BitmapDrawable art = new android.graphics.drawable.BitmapDrawable(getResources(), round);
            art.setBounds(0, 0, size, size);
            playing.setCompoundDrawables(art, null, null, null);
            playing.setCompoundDrawablePadding((int) (8 * dp));
            android.graphics.drawable.GradientDrawable glass = new android.graphics.drawable.GradientDrawable();
            glass.setColor(0x1FFFFFFF);
            glass.setCornerRadius(style == 1 ? 24 * dp : 10 * dp);
            playing.setBackground(glass);
            playing.setPadding((int) (6 * dp), (int) (6 * dp), (int) (14 * dp), (int) (6 * dp));
            ViewGroup.LayoutParams lp = playing.getLayoutParams();
            if (lp != null) { lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; playing.setLayoutParams(lp); }
        }

        /** Two soft pulses around the screen edge (enables software shadow layer only during the pulse). */
        void glow() {
            if (pulse != null) pulse.cancel();
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            pulse = ValueAnimator.ofFloat(0f, 1f, 0.25f, 1f, 0f);
            pulse.setDuration(3200);
            pulse.addUpdateListener(a -> { glow = (float) a.getAnimatedValue(); invalidate(); });
            pulse.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    glow = 0f;
                    setLayerType(LAYER_TYPE_NONE, null);
                    invalidate();
                }
                @Override public void onAnimationCancel(android.animation.Animator animation) {
                    glow = 0f;
                    setLayerType(LAYER_TYPE_NONE, null);
                }
            });
            pulse.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (glow <= 0f) return;
            float inset = glowPaint.getStrokeWidth() / 2;
            bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            float radius = 40 * getResources().getDisplayMetrics().density;
            glowPaint.setAlpha((int) (255 * glow));
            canvas.drawRoundRect(bounds, radius, radius, glowPaint);
        }
    }

    /** Charging, Charged. */
    private static String[] words() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return new String[] {"Şarj oluyor", "Doldu"};
            case "ru": return new String[] {"Заряжается", "Заряжено"};
            case "es": return new String[] {"Cargando", "Cargado"};
            case "zh": return new String[] {"充电中", "已充满"};
            default: return new String[] {"Charging", "Charged"};
        }
    }
}
