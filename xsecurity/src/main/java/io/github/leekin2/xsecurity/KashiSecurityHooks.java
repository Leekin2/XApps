package io.github.leekin2.xsecurity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Security app features from kashi's modded Security Center (Mods Center, used with the author's
 * permission). Kashi's code is not included: each feature is written anew as a hook on the stock
 * app, at the places his patch changes.
 */
final class KashiSecurityHooks {
    private final Hooks hooks;
    private final ClassLoader loader;

    KashiSecurityHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    /** The security scan always shows 100 and never asks to fix anything to raise it. */
    void lockScore(DexTargets targets) throws Throwable {
        int hooked = 0;
        // The stored score and the points taken off it.
        for (Method m : targets.usingStrings("kashi_score_stored", "int", "key_score_in_security")) { hooks.constant(m, 100); hooked++; }
        for (Method m : targets.usingStrings("kashi_score_minus", "int", "key_minus_score_in_security_exclude_virus")) { hooks.constant(m, 0); hooked++; }
        for (Method m : targets.usingStrings("kashi_score_predict", "int", "getMinusPredictScore------------------------------------------------ ")) { hooks.constant(m, 0); hooked++; }
        Class<?> manager = Class.forName("com.miui.securityscan.scanner.ScoreManager", false, loader);
        // The live sum of risky items: an int method without arguments that walks the item groups.
        for (String name : new String[] {"l"}) {
            try {
                Method m = manager.getDeclaredMethod(name);
                if (m.getReturnType() == int.class) { hooks.constant(m, 0); hooked++; }
            } catch (NoSuchMethodException ignored) { }
        }
        Class<?> text = Class.forName("com.miui.common.customview.ScoreTextView", false, loader);
        hooks.install(text.getDeclaredMethod("setScore", int.class), chain -> {
            Object[] args = chain.getArgs().toArray();
            args[0] = 100;
            return chain.proceed(args);
        });
        hooked++;
        if (hooked < 3) throw new NoSuchMethodException("Score methods not found (" + hooked + ")");
    }

    /**
     * Frosted glass blur on Game Turbo and Video Toolbox panels (kashi's KashiPanelGlass.apply).
     * Hooks BlurUtils (com.miui.common.utils.n) so panel blur always activates with translucent
     * glass blend colors instead of falling back to an opaque solid background.
     */
    void panelGlassBlur(DexTargets targets) throws Throwable {
        List<Class<?>> blurClasses = targets.classesUsing("kashi_panel_blur", "setMiBackgroundBlendColors", "setPassWindowBlurEnabled");
        if (blurClasses.isEmpty()) {
            try { blurClasses = List.of(Class.forName("com.miui.common.utils.n", false, loader)); }
            catch (ClassNotFoundException ignored) { }
        }
        int hooked = 0;
        for (Class<?> blur : blurClasses) {
            for (Method m : blur.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getReturnType() == boolean.class && p.length == 1 && p[0] == android.content.Context.class) {
                    hooks.constant(m, true);
                    hooked++;
                } else if (m.getReturnType() == void.class && p.length == 3
                        && android.view.ViewGroup.class.isAssignableFrom(p[0])
                        && p[1] == int.class && p[2] == android.content.Context.class) {
                    hooks.install(m, chain -> {
                        Object res = chain.proceed();
                        try {
                            android.view.ViewGroup vg = (android.view.ViewGroup) chain.getArg(0);
                            android.content.Context ctx = (android.content.Context) chain.getArg(2);
                            if (vg != null && ctx != null) applyGlass(vg, ctx);
                        } catch (Throwable ignored) { }
                        return res;
                    });
                    hooked++;
                }
            }
        }
        if (hooked == 0) throw new NoSuchMethodException("Toolbox blur helper not found");
    }

    private static void applyGlass(android.view.ViewGroup view, android.content.Context ctx) {
        try {
            boolean dark = (ctx.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            Class<?> vc = android.view.View.class;
            try { vc.getMethod("setPassWindowBlurEnabled", boolean.class).invoke(view, true); } catch (Throwable ignored) { }
            try { vc.getMethod("setMiBackgroundBlurMode", int.class).invoke(view, 1); } catch (Throwable ignored) { }
            try { vc.getMethod("setMiBackgroundBlurRadius", int.class).invoke(view, 80); } catch (Throwable ignored) { }
            try { vc.getMethod("setMiViewBlurMode", int.class).invoke(view, 1); } catch (Throwable ignored) { }
            java.util.ArrayList<android.graphics.Point> blend = new java.util.ArrayList<>();
            if (dark) {
                blend.add(new android.graphics.Point(0x88181A1E, 3));
                blend.add(new android.graphics.Point(0x28FFFFFF, 15));
            } else {
                blend.add(new android.graphics.Point(0xB8F5F7FA, 3));
                blend.add(new android.graphics.Point(0x24000000, 15));
            }
            try { vc.getMethod("setMiBackgroundBlendColors", java.util.ArrayList.class).invoke(view, blend); } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
    }

    // --- Hidden features turned on, as in kashi's mod --------------------------------------------

    /** Answers every feature check of one group the way kashi's mod does. */
    void flags(String group) throws Throwable {
        // The map names R8-shortened classes of one Security build. Found first, hooked after:
        // when most of a group is missing, this is another build and none of it is touched,
        // so a short name that now means something else is never forced.
        List<Method[]> found = new ArrayList<>();
        int total = 0, missing = 0;
        for (String[] f : KashiFlags.FLAGS) {
            if (!f[0].equals(group)) continue;
            total++;
            Method target = null;
            try {
                Class<?> owner = Class.forName(f[1], false, loader);
                int count = Integer.parseInt(f[3]);
                for (Method m : owner.getDeclaredMethods()) {
                    if (!m.getName().equals(f[2]) || m.getParameterCount() != count) continue;
                    if (!fits(m.getReturnType(), f[4])) continue;
                    target = m;
                    break;
                }
            } catch (Throwable ignored) { }
            if (target == null) { missing++; continue; }
            found.add(new Method[] {target});
            answers.put(target, f[4]);
        }
        if (found.isEmpty()) throw new NoSuchMethodException("No " + group + " feature checks found");
        if (found.size() * 10 < total * 7) {
            throw new NoSuchMethodException(group + " map does not match this Security build (" + found.size() + " of " + total + ")");
        }
        int hooked = 0;
        for (Method[] m : found) {
            hooks.constant(m[0], answer(m[0].getReturnType(), answers.get(m[0])));
            hooked++;
        }
        hooks.info(group + " features: " + hooked + " on" + (missing > 0 ? ", " + missing + " not in this build" : ""));
    }

    private final java.util.Map<Method, String> answers = new java.util.HashMap<>();

    private static boolean fits(Class<?> type, String value) {
        switch (value) {
            case "true": case "false": return type == boolean.class;
            // Only the Intent getter is answered with null (its callers expect null there).
            case "null": return type == android.content.Intent.class;
            default: return type == int.class || type == long.class;
        }
    }

    private static Object answer(Class<?> type, String value) {
        switch (value) {
            case "true": return true;
            case "false": return false;
            case "null": return null;
            default: return type == long.class ? (Object) Long.parseLong(value) : (Object) Integer.parseInt(value);
        }
    }

    // --- App info: MIUI permission editor ------------------------------------------------------

    /**
     * App info shows the "Permissions" row of the MIUI (China) permission editor for every app,
     * not only for the few Xiaomi lists it is meant for.
     */
    void forceCnPermissions(DexTargets targets) throws Throwable {
        // The check sits next to the package lists in the App manager's config class: the only
        // static boolean(String) there.
        int hooked = 0;
        for (Class<?> config : targets.classesUsing("kashi_am_lists", "com.miui.greenguard", "com.android.browser")) {
            for (Method m : config.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getReturnType() != boolean.class) continue;
                if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != String.class) continue;
                hooks.constant(m, true);
                hooked++;
            }
        }
        if (hooked == 0) throw new NoSuchMethodException("Permission editor row check not found");
    }

    // --- Game Turbo: block the shade from the toolbox -------------------------------------------

    /** Toolbox item id; Game Turbo uses 100200xx and 1003xxxx for its own. */
    static final int SHADE_ITEM = 10029901;
    private static final String SHADE_PREF = "pref_function_shiled_pull_notification_bar";
    private static volatile Object shadeService;
    private static Method shadeStart, shadeStop, shadeRead, shadeGet, shadeSet;

    /**
     * A "Block shade" button in the game toolbox switches Game Turbo's own "no notification shade
     * in games" setting, and applies it to the running game at once.
     */
    void gameShade(DexTargets targets) throws Throwable {
        Class<?> item = Class.forName("com.miui.gamebooster.model.n", false, loader);
        // The toolbox list: (Context, String, int) -> List, with Game Turbo's own item ids.
        List<Method> lists = targets.matching("kashi_toolbox_list", () -> org.luckypray.dexkit.query.matchers.MethodMatcher.create()
                .returnType("java.util.List").paramTypes("android.content.Context", "java.lang.String", "int").usingNumbers(10020200));
        if (lists.isEmpty()) throw new NoSuchMethodException("Toolbox list not found");
        java.lang.reflect.Constructor<?> make = item.getDeclaredConstructor(int.class, String.class, int.class);
        for (Method m : lists) hooks.install(m, chain -> {
            Object result = chain.proceed();
            if (result instanceof List) {
                android.content.Context context = (android.content.Context) chain.getArg(0);
                int icon = context.getResources().getIdentifier("game_toolbox_slow_charge_notification_icon", "drawable", context.getPackageName());
                @SuppressWarnings("unchecked") List<Object> list = (List<Object>) result;
                // Before the Settings item, which Game Turbo keeps last.
                int at = Math.max(0, list.size() - 1);
                list.add(at, make.newInstance(SHADE_ITEM, shadeTitle(), icon));
            }
            return result;
        });

        // Game Turbo keeps the setting in its own settings provider; use its getter and setter.
        for (Method m : targets.usingStrings("kashi_shade_get", "boolean", SHADE_PREF))
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) shadeGet = m;
        for (Method m : targets.usingStrings("kashi_shade_set", "void", SHADE_PREF))
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) shadeSet = m;
        if (shadeGet == null || shadeSet == null) throw new NoSuchMethodException("Game shade setting not found");
        shadeGet.setAccessible(true); shadeSet.setAccessible(true);

        // The service that applies the setting when a game starts and stops.
        List<Method> start = targets.usingStrings("kashi_shade_start", "void", "misShieldPullNotificationBar...start ");
        List<Method> stop = targets.usingStrings("kashi_shade_stop", "void", "misShieldPullNotificationBar...stop");
        if (start.isEmpty() || stop.isEmpty()) throw new NoSuchMethodException("Game shade service not found");
        shadeStart = start.get(0);
        shadeStop = stop.get(0);
        for (Method m : shadeStart.getDeclaringClass().getDeclaredMethods()) {
            if (m.getReturnType() == void.class && m.getParameterCount() == 0 && java.lang.reflect.Modifier.isPublic(m.getModifiers())
                    && !m.equals(shadeStart) && !m.equals(shadeStop)) shadeRead = m;
        }
        if (shadeRead == null) throw new NoSuchMethodException("Game shade settings reader not found");
        shadeStart.setAccessible(true); shadeStop.setAccessible(true); shadeRead.setAccessible(true);
        hooks.install(shadeStart, chain -> { shadeService = chain.getThisObject(); return chain.proceed(); });
        hooks.install(shadeStop, chain -> { Object r = chain.proceed(); shadeService = null; return r; });

        // Tapping the button: (String, int, item, Context, View) -> boolean handles toolbox taps.
        List<Method> clicks = targets.matching("kashi_toolbox_click", () -> org.luckypray.dexkit.query.matchers.MethodMatcher.create()
                .returnType("boolean").paramTypes("java.lang.String", "int", "com.miui.gamebooster.model.n", "android.content.Context", "android.view.View"));
        if (clicks.isEmpty()) throw new NoSuchMethodException("Toolbox click not found");
        Method id = item.getDeclaredMethod("e");
        for (Method m : clicks) hooks.install(m, chain -> {
            Object tapped = chain.getArg(2);
            if (tapped == null || (int) id.invoke(tapped) != SHADE_ITEM) return chain.proceed();
            android.content.Context context = (android.content.Context) chain.getArg(3);
            boolean on = !shadeBlocked(context);
            setShadeBlocked(context, on);
            showShade(chain.getArg(4), on);
            android.widget.Toast.makeText(context, shadeTitle() + ": " + (on ? "✓" : "✕"), android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });

        // The newer toolbox panel handles taps itself: (..., holder, item) on GameToolboxMainView.
        try {
            Class<?> main = Class.forName("com.miui.gamebooster.windowmanager.newbox.GameToolboxMainView", false, loader);
            for (Method m : main.getDeclaredMethods()) {
                Class<?>[] types = m.getParameterTypes();
                if (m.getReturnType() != void.class || types.length < 3 || types[types.length - 1] != item) continue;
                hooks.install(m, chain -> {
                    Object[] args = chain.getArgs().toArray();
                    Object tapped = args[args.length - 1];
                    if (tapped == null || (int) id.invoke(tapped) != SHADE_ITEM) return chain.proceed();
                    android.content.Context context = ((android.view.View) chain.getThisObject()).getContext();
                    boolean on = !shadeBlocked(context);
                    setShadeBlocked(context, on);
                    Object holder = args[args.length - 2];
                    try { showShade(holder.getClass().getField("itemView").get(holder), on); } catch (Throwable ignored) { }
                    android.widget.Toast.makeText(context, shadeTitle() + ": " + (on ? "✓" : "✕"), android.widget.Toast.LENGTH_SHORT).show();
                    return null;
                });
            }
        } catch (ClassNotFoundException ignored) { }

        // Showing the button selected while the shade is blocked: (item, holder) binds a toolbox cell.
        List<Method> binds = targets.matching("kashi_toolbox_bind", () -> org.luckypray.dexkit.query.matchers.MethodMatcher.create()
                .returnType("void").paramCount(2).paramTypes("com.miui.gamebooster.model.n", null));
        for (Method m : binds) {
            if (m.getParameterTypes()[1].getSuperclass() == null || !m.getParameterTypes()[1].getSuperclass().getName().contains("RecyclerView")) continue;
            hooks.install(m, chain -> {
                Object result = chain.proceed();
                Object bound = chain.getArg(0);
                if (bound != null && (int) id.invoke(bound) == SHADE_ITEM) {
                    Object holder = chain.getArg(1);
                    android.view.View view = (android.view.View) holder.getClass().getField("itemView").get(holder);
                    showShade(view, shadeBlocked(view.getContext()));
                }
                return result;
            });
        }
    }

    private static String shadeTitle() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return "Paneli engelle";
            case "ru": return "Блок шторки";
            case "es": return "Bloquear panel";
            case "zh": return "禁止下拉";
            default: return "Block shade";
        }
    }

    private static boolean shadeBlocked(android.content.Context context) {
        try { return (boolean) shadeGet.invoke(null, false); } catch (Throwable t) { return false; }
    }

    private static void setShadeBlocked(android.content.Context context, boolean on) {
        try { shadeSet.invoke(null, on); } catch (Throwable t) { android.util.Log.w("XSecurity", "game shade setting not saved", t); return; }
        Object service = shadeService;
        if (service == null) return;
        // Restart the running game's shield with the new setting: stop, read settings, start.
        try {
            shadeStop.invoke(service);
            shadeRead.invoke(service);
            shadeStart.invoke(service);
        } catch (Throwable t) {
            android.util.Log.w("XSecurity", "game shade could not be applied now", t);
        }
    }

    /** Selected look for a toolbox cell: icon selected and the text in Game Turbo's accent colour. */
    private static void showShade(Object cell, boolean on) {
        if (!(cell instanceof android.view.View)) return;
        android.view.View view = (android.view.View) cell;
        android.content.Context context = view.getContext();
        android.content.res.Resources res = context.getResources();
        String pkg = "com.miui.securitycenter";
        android.view.View icon = view.findViewById(res.getIdentifier("function_icon", "id", pkg));
        android.view.View title = view.findViewById(res.getIdentifier("function_title", "id", pkg));
        if (icon != null) icon.setSelected(on);
        if (title instanceof android.widget.TextView) {
            int color = res.getIdentifier(on ? "game_toolbox_color_selected" : "gamebox_func_text", "color", pkg);
            if (color != 0) ((android.widget.TextView) title).setTextColor(res.getColor(color, null));
        }
    }

    // --- Game toolbox media controls -----------------------------------------------------------

    /** The song playing now, with previous / play-pause / next, at the bottom of the game toolbox. */
    void gameMedia(DexTargets targets) throws Throwable {
        int hooked = 0;
        for (Method m : targets.usingStrings("kashi_toolbox_init", "void", "init GameToolboxMainView gamePkg: ")) {
            if (m.getParameterCount() != 0) continue;
            hooks.install(m, chain -> {
                Object result = chain.proceed();
                android.view.ViewGroup root = (android.view.ViewGroup) chain.getThisObject();
                try { attachMedia(root); } catch (Throwable t) { android.util.Log.w("XSecurity", "game media controls", t); }
                return result;
            });
            hooked++;
        }
        if (hooked == 0) throw new NoSuchMethodException("Game toolbox init not found");
    }

    private static final String MEDIA_TAG = "xsecurity.media";

    private static void attachMedia(android.view.ViewGroup root) {
        if (root.findViewWithTag(MEDIA_TAG) != null) return;
        android.view.ViewGroup list = findVertical(root);
        if (list == null) return;
        android.content.Context context = root.getContext();
        float dp = context.getResources().getDisplayMetrics().density;

        android.widget.LinearLayout capsule = new android.widget.LinearLayout(context);
        capsule.setTag(MEDIA_TAG);
        capsule.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        capsule.setGravity(android.view.Gravity.CENTER_VERTICAL);
        capsule.setPadding((int) (10 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x24FFFFFF);
        bg.setCornerRadius(18 * dp);
        capsule.setBackground(bg);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, (int) (56 * dp));
        lp.setMargins((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        capsule.setLayoutParams(lp);

        int artSize = (int) (40 * dp);
        android.widget.FrameLayout artBox = new android.widget.FrameLayout(context);
        android.graphics.drawable.GradientDrawable artBg = new android.graphics.drawable.GradientDrawable();
        artBg.setColor(0x28FFFFFF);
        artBg.setCornerRadius(10 * dp);
        artBox.setBackground(artBg);
        artBox.setClipToOutline(true);
        android.widget.TextView note = new android.widget.TextView(context);
        note.setText("♪");
        note.setTextColor(0xCCFFFFFF);
        note.setTextSize(18);
        note.setGravity(android.view.Gravity.CENTER);
        artBox.addView(note, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.ImageView art = new android.widget.ImageView(context);
        art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        art.setVisibility(android.view.View.GONE);
        artBox.addView(art, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.LinearLayout.LayoutParams artLp = new android.widget.LinearLayout.LayoutParams(artSize, artSize);
        artLp.rightMargin = (int) (10 * dp);
        capsule.addView(artBox, artLp);

        android.widget.TextView title = new android.widget.TextView(context);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, 0, (int) (6 * dp), 0);
        capsule.addView(title, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.ImageButton prev = mediaButton(context, android.R.drawable.ic_media_previous, dp);
        android.widget.ImageButton play = mediaButton(context, android.R.drawable.ic_media_play, dp);
        android.widget.ImageButton next = mediaButton(context, android.R.drawable.ic_media_next, dp);
        capsule.addView(prev); capsule.addView(play); capsule.addView(next);
        // Place right under the FPS/CPU/RAM header (index 1), matching kashi's Game Turbo layout.
        list.addView(capsule, Math.min(1, list.getChildCount()));

        android.media.session.MediaSessionManager sessions = context.getSystemService(android.media.session.MediaSessionManager.class);
        android.media.session.MediaController[] current = new android.media.session.MediaController[1];
        android.media.session.MediaController.Callback update = new android.media.session.MediaController.Callback() {
            @Override public void onPlaybackStateChanged(android.media.session.PlaybackState state) { showMedia(current[0], art, note, title, play); }
            @Override public void onMetadataChanged(android.media.MediaMetadata metadata) { showMedia(current[0], art, note, title, play); }
        };
        Runnable pick = () -> {
            android.media.session.MediaController chosen = null;
            try {
                for (android.media.session.MediaController c : sessions.getActiveSessions(null)) {
                    if (chosen == null) chosen = c;
                    android.media.session.PlaybackState s = c.getPlaybackState();
                    if (s != null && s.getState() == android.media.session.PlaybackState.STATE_PLAYING) { chosen = c; break; }
                }
            } catch (Throwable t) {
                android.util.Log.w("XSecurity", "media sessions unavailable", t);
            }
            if (current[0] != null) current[0].unregisterCallback(update);
            current[0] = chosen;
            if (chosen != null) chosen.registerCallback(update, new android.os.Handler(android.os.Looper.getMainLooper()));
            showMedia(chosen, art, note, title, play);
        };
        prev.setOnClickListener(v -> { if (current[0] != null) current[0].getTransportControls().skipToPrevious(); });
        next.setOnClickListener(v -> { if (current[0] != null) current[0].getTransportControls().skipToNext(); });
        play.setOnClickListener(v -> {
            android.media.session.MediaController c = current[0];
            if (c == null) return;
            android.media.session.PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == android.media.session.PlaybackState.STATE_PLAYING) c.getTransportControls().pause();
            else c.getTransportControls().play();
        });
        capsule.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View v) { pick.run(); }
            @Override public void onViewDetachedFromWindow(android.view.View v) {
                if (current[0] != null) current[0].unregisterCallback(update);
                current[0] = null;
            }
        });
        if (capsule.isAttachedToWindow()) pick.run(); else showMedia(null, art, note, title, play);
    }

    /** The scroll view's vertical list inside the toolbox (panel on top, tools under it). */
    private static android.view.ViewGroup findVertical(android.view.ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child instanceof android.widget.ScrollView || child.getClass().getName().endsWith("NestedScrollView")) {
                android.view.View inner = ((android.view.ViewGroup) child).getChildAt(0);
                if (inner instanceof android.widget.LinearLayout) return (android.view.ViewGroup) inner;
            }
            if (child instanceof android.view.ViewGroup) {
                android.view.ViewGroup found = findVertical((android.view.ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static android.widget.ImageButton mediaButton(android.content.Context context, int icon, float dp) {
        android.widget.ImageButton button = new android.widget.ImageButton(context);
        button.setImageResource(icon);
        button.setBackground(null);
        button.setColorFilter(0xFFFFFFFF);
        button.setLayoutParams(new android.widget.LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp)));
        return button;
    }

    private static void showMedia(android.media.session.MediaController controller, android.widget.ImageView art,
                                  android.view.View note, android.widget.TextView title, android.widget.ImageButton play) {
        android.media.MediaMetadata meta = controller == null ? null : controller.getMetadata();
        CharSequence song = meta == null ? null : meta.getText(android.media.MediaMetadata.METADATA_KEY_TITLE);
        CharSequence artist = meta == null ? null : meta.getText(android.media.MediaMetadata.METADATA_KEY_ARTIST);
        if (controller == null || android.text.TextUtils.isEmpty(song)) {
            boolean tr = "tr".equals(java.util.Locale.getDefault().getLanguage());
            title.setText(tr ? "Şarkı yok..." : "No songs...");
            title.setTextColor(0xB3FFFFFF);
            art.setVisibility(android.view.View.GONE);
            note.setVisibility(android.view.View.VISIBLE);
            play.setImageResource(android.R.drawable.ic_media_play);
            return;
        }
        title.setTextColor(0xFFFFFFFF);
        title.setText(android.text.TextUtils.isEmpty(artist) ? song : song + " · " + artist);
        android.graphics.Bitmap cover = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (cover == null) cover = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
        if (cover != null) {
            art.setImageBitmap(cover);
            art.setVisibility(android.view.View.VISIBLE);
            note.setVisibility(android.view.View.GONE);
        } else {
            art.setVisibility(android.view.View.GONE);
            note.setVisibility(android.view.View.VISIBLE);
        }
        android.media.session.PlaybackState s = controller.getPlaybackState();
        boolean playing = s != null && s.getState() == android.media.session.PlaybackState.STATE_PLAYING;
        play.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    // --- Game session summary --------------------------------------------------------------------

    private static long sessionStart;
    private static int sessionBattery = -1;
    private static int sessionPeak; // tenths of a degree
    private static String sessionGame;
    private static android.content.BroadcastReceiver sessionWatch;

    /** After a game, a notification shows how long it was played, the battery used and the peak temperature. */
    void gameSession(DexTargets targets) throws Throwable {
        List<Method> boosting = targets.usingStrings("kashi_game_boosting", "void", "gb_boosting", "gb_boosting_pkg_name");
        int hooked = 0;
        for (Method m : boosting) {
            if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != int.class) continue;
            hooks.install(m, chain -> {
                Object result = chain.proceed();
                int state = (int) chain.getArg(0);
                try {
                    if (state == 1) sessionStarted(); else if (state == 0) sessionEnded();
                } catch (Throwable t) {
                    android.util.Log.w("XSecurity", "game session summary", t);
                }
                return result;
            });
            hooked++;
        }
        if (hooked == 0) throw new NoSuchMethodException("Game boosting state not found");
    }

    private static synchronized void sessionStarted() {
        android.content.Context context = app();
        if (context == null || sessionStart != 0) return;
        sessionStart = android.os.SystemClock.elapsedRealtime();
        sessionGame = android.provider.Settings.Secure.getString(context.getContentResolver(), "gb_boosting_pkg_name");
        sessionPeak = 0;
        sessionWatch = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context c, android.content.Intent intent) {
                int temp = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
                synchronized (KashiSecurityHooks.class) { if (temp > sessionPeak) sessionPeak = temp; }
            }
        };
        android.content.Intent sticky = context.registerReceiver(sessionWatch, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        sessionBattery = level(sticky);
    }

    private static int level(android.content.Intent battery) {
        if (battery == null) return -1;
        int level = battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
        return level < 0 || scale <= 0 ? -1 : level * 100 / scale;
    }

    private static synchronized void sessionEnded() {
        android.content.Context context = app();
        if (context == null || sessionStart == 0) return;
        long played = android.os.SystemClock.elapsedRealtime() - sessionStart;
        sessionStart = 0;
        try { context.unregisterReceiver(sessionWatch); } catch (Throwable ignored) { }
        sessionWatch = null;
        if (played < 60_000) return;
        int now = level(context.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)));
        int used = sessionBattery >= 0 && now >= 0 ? Math.max(0, sessionBattery - now) : -1;

        String game = sessionGame;
        CharSequence label = game;
        try { label = context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(game, 0)); } catch (Throwable ignored) { }
        long minutes = played / 60_000;
        String time = minutes >= 60 ? (minutes / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", minutes % 60) : minutes + "'";
        String[] words = sessionWords();
        StringBuilder text = new StringBuilder(words[1]).append(' ').append(time);
        if (used >= 0) text.append(" · ").append(words[2]).append(" %").append(used);
        if (sessionPeak > 0) text.append(" · ").append(words[3]).append(' ').append(String.format(java.util.Locale.ROOT, "%.1f °C", sessionPeak / 10f));

        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        String channel = "xsecurity_game_session";
        manager.createNotificationChannel(new android.app.NotificationChannel(channel, words[0], android.app.NotificationManager.IMPORTANCE_LOW));
        android.app.Notification notification = new android.app.Notification.Builder(context, channel)
                .setSmallIcon(context.getApplicationInfo().icon)
                .setContentTitle(label != null ? label : words[0])
                .setContentText(text)
                .setSubText(words[0])
                .setAutoCancel(true)
                .build();
        manager.notify("xsecurity_game_session", 1, notification);
    }

    /** Title, then the labels for play time, battery used and peak temperature. */
    private static String[] sessionWords() {
        switch (java.util.Locale.getDefault().getLanguage()) {
            case "tr": return new String[] {"Oyun özeti", "Oynandı", "Pil", "En yüksek"};
            case "ru": return new String[] {"Итоги игры", "Сыграно", "Заряд", "Пик"};
            case "es": return new String[] {"Resumen de partida", "Jugado", "Batería", "Pico"};
            case "zh": return new String[] {"游戏总结", "游戏时长", "耗电", "最高温度"};
            default: return new String[] {"Game session", "Played", "Battery", "Peak"};
        }
    }

    // --- No heads-up during videos ------------------------------------------------------------

    private static final String HEADS_UP = "heads_up_notifications_enabled";
    /** Kept in Settings too, so heads-up comes back even if Security is killed mid-video. */
    private static final String HEADS_UP_SAVED = "xsecurity_saved_heads_up";
    private static volatile boolean videoOn;
    /** Heads-up value saved when we turned it off, or null when we did not. */
    private static volatile Integer savedHeadsUp;
    private static android.content.ComponentCallbacks rotation;

    /**
     * While the video toolbox is active and the screen is in landscape (a full-screen video), new
     * notifications do not pop up over the video. They still arrive in the shade.
     */
    void videoHeadsUp(DexTargets targets) throws Throwable {
        List<Method> start = targets.usingStrings("kashi_video_start", "void", "startVideoBox: isDuringVideoBoxMode=");
        if (start.isEmpty()) throw new NoSuchMethodException("Video toolbox start not found");
        Class<?> manager = start.get(0).getDeclaringClass();
        for (Method m : start) hooks.install(m, chain -> { Object r = chain.proceed(); videoStarted(); return r; });
        int stops = 0;
        for (Method m : targets.usingStrings("kashi_video_exit", "void", "video box exit app...")) {
            if (m.getDeclaringClass() != manager) continue;
            hooks.install(m, chain -> { videoStopped(); return chain.proceed(); });
            stops++;
        }
        for (Method m : targets.usingStrings("kashi_video_close", "void", "VideoBoxServiceManager", "close")) {
            if (m.getDeclaringClass() != manager || m.getParameterCount() != 0) continue;
            hooks.install(m, chain -> { videoStopped(); return chain.proceed(); });
            stops++;
        }
        if (stops == 0) throw new NoSuchMethodException("Video toolbox stop not found");
    }

    /**
     * Puts heads-up back if Security died during a video while it was off. Runs at every start
     * of Security's main process, also when the switch has been turned off since.
     */
    static void restoreHeadsUp() {
        android.content.Context context = app();
        if (context == null) return;
        try {
            String left = android.provider.Settings.Global.getString(context.getContentResolver(), HEADS_UP_SAVED);
            if (left != null) {
                android.provider.Settings.Global.putInt(context.getContentResolver(), HEADS_UP, Integer.parseInt(left));
                android.provider.Settings.Global.putString(context.getContentResolver(), HEADS_UP_SAVED, null);
            }
        } catch (Throwable ignored) { }
    }

    private static android.content.Context app() {
        try {
            return (android.content.Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized void videoStarted() {
        android.content.Context context = app();
        if (context == null || videoOn) return;
        videoOn = true;
        rotation = new android.content.ComponentCallbacks() {
            @Override public void onConfigurationChanged(android.content.res.Configuration config) { applyHeadsUp(context, config); }
            @Override public void onLowMemory() { }
        };
        context.registerComponentCallbacks(rotation);
        applyHeadsUp(context, context.getResources().getConfiguration());
    }

    private static synchronized void videoStopped() {
        android.content.Context context = app();
        if (context == null || !videoOn) return;
        videoOn = false;
        if (rotation != null) context.unregisterComponentCallbacks(rotation);
        rotation = null;
        restoreHeadsUp(context);
    }

    private static synchronized void applyHeadsUp(android.content.Context context, android.content.res.Configuration config) {
        boolean landscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (videoOn && landscape) {
            if (savedHeadsUp != null) return;
            try {
                savedHeadsUp = android.provider.Settings.Global.getInt(context.getContentResolver(), HEADS_UP, 1);
                android.provider.Settings.Global.putString(context.getContentResolver(), HEADS_UP_SAVED, String.valueOf(savedHeadsUp));
                android.provider.Settings.Global.putInt(context.getContentResolver(), HEADS_UP, 0);
            } catch (Throwable t) {
                savedHeadsUp = null;
                android.util.Log.w("XSecurity", "heads-up could not be paused", t);
            }
        } else {
            restoreHeadsUp(context);
        }
    }

    private static synchronized void restoreHeadsUp(android.content.Context context) {
        Integer saved = savedHeadsUp;
        if (saved == null) return;
        savedHeadsUp = null;
        try {
            android.provider.Settings.Global.putInt(context.getContentResolver(), HEADS_UP, saved);
            android.provider.Settings.Global.putString(context.getContentResolver(), HEADS_UP_SAVED, null);
        } catch (Throwable t) {
            android.util.Log.w("XSecurity", "heads-up could not be restored", t);
        }
    }

    /**
     * Battery protection and smart charging stop at the chosen level instead of 80%. Night
     * charging keeps its own 80% (it is set elsewhere, as in kashi's mod).
     */
    void chargeLimit(DexTargets targets) throws Throwable {
        int limit = Features.chargeLimit();
        List<Method> open = targets.usingStrings("kashi_charge_open", "void", "openCommonProtectMode:");
        if (open.isEmpty()) throw new NoSuchMethodException("Charge protection mode not found");
        for (Method m : open) {
            if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != int.class) continue;
            hooks.install(m, chain -> {
                if ((int) chain.getArg(0) != 80) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[0] = limit;
                return chain.proceed(args);
            });
        }
    }
}
