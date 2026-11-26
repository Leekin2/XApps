package io.github.leekin2.xsecurity;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Unlocks HyperOS's enhanced keyboard bottom toolbar (clipboard, phrases, keyboard switcher,
 * cursor/selection bar) for Gboard and other third-party keyboards.
 * Approach based on RC1844/MIUI_IME_Unlock and HyperCeiler (AGPL-3.0).
 */
final class EnhancedKeyboardHooks {
    private static final Set<String> STOCK_MIUI_IMES = Set.of(
            "com.iflytek.inputmethod.miui",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.baidu.input_mi",
            "com.xiaomi.type",
            "com.miui.catcherpatch"
    );

    /** Non-IME packages that share XApps's LSPosed scope and must never run IME hooks. */
    private static final Set<String> NON_IME_PACKAGES = Set.of(
            "android", "com.android.camera", "com.android.vending", "com.google.android.apps.maps",
            "com.google.android.apps.photos", "com.google.android.googlequicksearchbox",
            "com.milink.service", "com.miui.aod", "com.miui.securitycore", "com.xiaomi.scanner",
            "io.github.leekin2.xapps"
    );

    /** Known third-party keyboards; any other app in XApps's scope never gets these hooks. */
    private static final Set<String> KEYBOARDS = Set.of(
            "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey",
            "com.touchtype.swiftkey.beta",
            "ru.yandex.androidkeyboard",
            "com.samsung.android.honeyboard",
            "com.syntellia.fleksy.keyboard",
            "org.futo.inputmethod.latin",
            "helium314.keyboard",
            "com.simplemobiletools.keyboard"
    );

    static boolean isCandidateIme(String pkg) {
        return pkg != null && KEYBOARDS.contains(pkg) && !STOCK_MIUI_IMES.contains(pkg) && !NON_IME_PACKAGES.contains(pkg);
    }

    private final Hooks hooks;
    private final Set<ClassLoader> hookedLoaders = Collections.newSetFromMap(new WeakHashMap<>());

    EnhancedKeyboardHooks(Hooks hooks) {
        this.hooks = hooks;
    }

    /** Installs the IME-process hooks (called when the process is a third-party keyboard). */
    void installIme(String pkg, ClassLoader loader) {
        if (!Features.enabled(Features.ENHANCED_KEYBOARD) || STOCK_MIUI_IMES.contains(pkg)) return;
        try {
            Class<?> injector = findClass("android.inputmethodservice.InputMethodServiceInjector", loader);
            if (injector == null) injector = findClass("android.inputmethodservice.InputMethodServiceStubImpl", loader);
            if (injector != null) {
                hookSupport(injector);
                hookXiaoAiOff(injector);
            }
            Class<?> moduleMgr = findClass("android.inputmethodservice.InputMethodModuleManager", loader);
            if (moduleMgr != null) {
                Method loadDex = moduleMgr.getDeclaredMethod("loadDex", ClassLoader.class, String.class);
                hooks.install(loadDex, chain -> {
                    Object result = chain.proceed();
                    Object arg = chain.getArg(0);
                    if (arg instanceof ClassLoader) {
                        ClassLoader imeLoader = (ClassLoader) arg;
                        if (hookedLoaders.add(imeLoader)) {
                            hookBottomPlugin(imeLoader);
                        }
                    }
                    return result;
                });
            }
            hooks.info("enhanced keyboard ready in " + pkg);
        } catch (Throwable failure) {
            hooks.error("enhanced keyboard unavailable in " + pkg, failure);
        }
    }

    private void hookBottomPlugin(ClassLoader imeLoader) {
        Class<?> manager = findClass("com.miui.inputmethod.InputMethodBottomManager", imeLoader);
        if (manager == null) return;
        hookSupport(manager);
        hookXiaoAiOff(manager);
        hookAllImesList(manager);
        hookInsets(manager);
        hooks.info("enhanced keyboard bottom plugin hooked");
    }

    private void hookSupport(Class<?> owner) {
        Field cache = null;
        try {
            cache = owner.getDeclaredField("sIsImeSupport");
            cache.setAccessible(true);
            cache.set(null, 1);
        } catch (Throwable ignored) {
            cache = null;
        }
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals("isImeSupport") && m.getReturnType() == boolean.class) {
                hooks.constant(m, true);
            }
        }
        if (cache != null) {
            Field f = cache;
            for (Method m : owner.getDeclaredMethods()) {
                if (m.getName().equals("addMiuiBottomView")) {
                    hooks.install(m, chain -> {
                        try {
                            if (!Integer.valueOf(1).equals(f.get(null))) f.set(null, 1);
                        } catch (Throwable ignored) { }
                        return chain.proceed();
                    });
                }
            }
        }
    }

    private void hookXiaoAiOff(Class<?> owner) {
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getName().equals("isXiaoAiEnable") && m.getReturnType() == boolean.class) {
                hooks.constant(m, false);
            }
        }
    }

    /** Show every enabled keyboard in the bottom-bar switcher, not just Xiaomi's CN whitelist. */
    private void hookAllImesList(Class<?> manager) {
        try {
            Field helperField = manager.getDeclaredField("sBottomViewHelper");
            helperField.setAccessible(true);
            for (Method m : manager.getDeclaredMethods()) {
                if (!m.getName().equals("getSupportIme")) continue;
                hooks.install(m, chain -> {
                    try {
                        Object helper = helperField.get(null);
                        if (helper != null) {
                            Field immField = helper.getClass().getDeclaredField("mImm");
                            immField.setAccessible(true);
                            Object imm = immField.get(helper);
                            if (imm != null) {
                                Object list = imm.getClass().getMethod("getEnabledInputMethodList").invoke(imm);
                                if (list != null) return list;
                            }
                        }
                    } catch (Throwable ignored) { }
                    return chain.proceed();
                });
            }
        } catch (Throwable ignored) { }
    }

    /** Prevents duplicate bottom navigation bar inset on Android 15+ (Gboard / third-party IMEs). */
    private void hookInsets(Class<?> manager) {
        if (Build.VERSION.SDK_INT < 35) return;
        InsetGuard guard = new InsetGuard();
        try {
            for (Method m : manager.getDeclaredMethods()) {
                if (m.getName().equals("addMiuiBottomView")) {
                    hooks.install(m, chain -> {
                        Object result = chain.proceed();
                        List<Object> args = chain.getArgs();
                        if (args.size() > 5 && args.get(3) instanceof ViewGroup && args.get(5) instanceof ViewGroup) {
                            guard.bind((ViewGroup) args.get(3), (ViewGroup) args.get(5));
                        }
                        return result;
                    });
                } else if (m.getName().equals("onWindowShown")) {
                    hooks.install(m, chain -> {
                        Object result = chain.proceed();
                        guard.requestApplyInsets();
                        return result;
                    });
                }
            }
            Method viewDispatch = View.class.getDeclaredMethod("dispatchApplyWindowInsets", WindowInsets.class);
            Method groupDispatch = ViewGroup.class.getDeclaredMethod("dispatchApplyWindowInsets", WindowInsets.class);
            for (Method dispatch : new Method[] {viewDispatch, groupDispatch}) {
                hooks.install(dispatch, chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length > 0 && args[0] instanceof WindowInsets && chain.getThisObject() instanceof View) {
                        args[0] = guard.filter((View) chain.getThisObject(), (WindowInsets) args[0]);
                        return chain.proceed(args);
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable failure) {
            hooks.error("enhanced keyboard inset guard unavailable", failure);
        }
    }

    private static Class<?> findClass(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (Throwable ignored) {
            try { return Class.forName(name); }
            catch (Throwable ignored2) { return null; }
        }
    }

    private static final class InsetGuard {
        private WeakReference<ViewGroup> inputFrame = new WeakReference<>(null);
        private WeakReference<ViewGroup> bottomArea = new WeakReference<>(null);
        private boolean bottomReady;
        private ViewTreeObserver observer;
        private final ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            ViewGroup area = bottomArea.get();
            boolean ready = area != null && area.getVisibility() == View.VISIBLE && area.getChildCount() > 0;
            boolean becameReady = ready && !bottomReady;
            bottomReady = ready;
            if (becameReady) requestApplyInsets();
        };

        void bind(ViewGroup frame, ViewGroup area) {
            if (frame.getContext().getApplicationInfo().targetSdkVersion < 35) return;
            clear();
            inputFrame = new WeakReference<>(frame);
            bottomArea = new WeakReference<>(area);
            observer = area.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(listener);
            frame.requestApplyInsets();
        }

        void requestApplyInsets() {
            ViewGroup frame = inputFrame.get();
            if (frame != null) frame.requestApplyInsets();
        }

        WindowInsets filter(View view, WindowInsets original) {
            ViewGroup frame = inputFrame.get();
            ViewGroup area = bottomArea.get();
            if (frame == null || area == null || view.getParent() != frame
                    || area.getVisibility() != View.VISIBLE || area.getChildCount() == 0) return original;
            int type = WindowInsets.Type.navigationBars();
            Insets visible = original.getInsets(type);
            Insets stable = original.getInsetsIgnoringVisibility(type);
            if (visible.bottom == 0 && stable.bottom == 0) return original;
            return new WindowInsets.Builder(original)
                    .setInsets(type, Insets.of(visible.left, visible.top, visible.right, 0))
                    .setInsetsIgnoringVisibility(type, Insets.of(stable.left, stable.top, stable.right, 0))
                    .build();
        }

        void clear() {
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
            observer = null;
            inputFrame.clear();
            bottomArea.clear();
            bottomReady = false;
        }
    }
}
