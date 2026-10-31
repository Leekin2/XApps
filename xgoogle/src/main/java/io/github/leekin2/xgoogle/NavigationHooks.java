package io.github.leekin2.xgoogle;

import android.content.Context;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.github.leekin2.xgoogle.HookInstaller.target;
import static io.github.leekin2.xgoogle.HookInstaller.booleanMethod;

final class NavigationHooks {
    private final HookInstaller hooks;
    private final AtomicBoolean installed = new AtomicBoolean();
    private final AtomicBoolean gateFailureLogged = new AtomicBoolean();

    NavigationHooks(HookInstaller hooks) { this.hooks = hooks; }

    void install(ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.NAVIGATION) || !installed.compareAndSet(false, true)) return;
        int navigationHandleStart = hooks.mark();
        try {
            Class<?> features = target(loader, "com.android.common.config.FeatureOption");
            Class<?> controller = target(loader, "com.android.launcher3.circlesearch.OplusHideNavHandleController");
            Class<?> instance = target(loader, controller.getName() + "$INSTANCE");
            Class<?> consumer = target(loader, "com.android.quickstep.inputconsumers.OplusCuiInputConsumer");
            Class<?> companion = target(loader, consumer.getName() + "$Companion");
            Class<?> listener = target(loader, "com.android.quickstep.inputconsumers.a");
            Class<?> service = target(loader, "com.android.quickstep.OplusBaseTouchInteractionService");
            GateBinding navigation = GateBinding.bind(target(loader, "com.oplus.quickstep.navigation.a"));
            // Preflight the entire inspected path before installing any behavioral hooks.
            Method circle = booleanMethod(features, "isSupportCircleToSearch");
            Method hidden = booleanMethod(features, "isSupportCircleToSearchNavbarHidden");
            Method supported = booleanMethod(instance, "isSupportCircleToSearchHandle");
            controller.getDeclaredMethod("init");
            companion.getDeclaredMethod("e", int.class, Context.class);
            // Only these three methods change behavior. Native event handlers and dispatch remain original.
            hooks.install(circle, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
            hooks.install(hidden, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
            hooks.install(supported, chain -> {
                if (!FeatureConfig.enabled(FeaturePolicy.NAVIGATION)) return chain.proceed();
                boolean eligible;
                try { eligible = navigation.eligible(); }
                catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    if (gateFailureLogged.compareAndSet(false, true)) hooks.error("Native observers unavailable; gate closed", error);
                    return false;
                }
                return eligible;
            });
            // Only inspected callers of the three gates need ART inlining invalidated.
            int deoptimized = hooks.deoptimizeCallers(controller, "init", "onChange", "isEducationNeeded", "isEducationShowing",
                            "isHandleShowing", "cancelEducation", "showEducation", "c")
                    + hooks.deoptimizeCallers(consumer, "n", "onMotionEvent")
                    + hooks.deoptimizeCallers(companion, "a", "e")
                    + hooks.deoptimizeCallers(listener, "onShowPress", "onLongPress")
                    + hooks.deoptimizeCallers(service, "onCreate");
            hooks.info("READY three navigation gates installed; deoptimized callers=" + deoptimized);
        } catch (Throwable error) {
            hooks.unhookSince(navigationHandleStart);
            hooks.error("SKIP incompatible navigation path; navigation hooks removed", error);
        }
    }
}
