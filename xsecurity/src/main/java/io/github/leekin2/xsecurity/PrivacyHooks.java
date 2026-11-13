package io.github.leekin2.xsecurity;

import android.app.job.JobParameters;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Stops background data collection in four China ROM system apps. Each switch is off until chosen
 * and only stops the upload or collection step; nothing the user sees is changed. The places to
 * stop follow Hyper Privacy Guard (io.github.hyperprivacyguard, AGPL-3.0), written anew; see NOTICE.
 * A target that is not found exactly once is left alone.
 */
final class PrivacyHooks {
    static final String OPERATOR = "com.mobiletools.systemhelper";
    static final String TELECOM = "com.xiaomi.registration";
    static final String MISIGHT = "com.miui.misightservice";
    static final String ADS = "com.miui.systemAdSolution";

    private final Hooks hooks;
    private final ClassLoader loader;
    private int installed;

    PrivacyHooks(Hooks hooks, ClassLoader loader) {
        this.hooks = hooks;
        this.loader = loader;
    }

    static boolean handles(String name) {
        return OPERATOR.equals(name) || TELECOM.equals(name) || MISIGHT.equals(name) || ADS.equals(name);
    }

    void install(String name, String apk, String data) {
        String feature = OPERATOR.equals(name) ? Features.PRIVACY_OPERATOR
                : TELECOM.equals(name) ? Features.PRIVACY_TELECOM
                : MISIGHT.equals(name) ? Features.PRIVACY_MISIGHT : Features.PRIVACY_AD_USAGE;
        if (!Features.enabled(feature)) return;
        if (apk == null || !new java.io.File(apk).isFile() || !new java.io.File(apk).canRead()) {
            hooks.info("privacy: " + name + " APK unavailable; no collection hooks installed");
            return;
        }
        try (DexTargets targets = new DexTargets(loader, apk, data)) {
            if (OPERATOR.equals(name)) operator(targets);
            else if (TELECOM.equals(name)) telecom(targets);
            else if (MISIGHT.equals(name)) miSight(targets);
            else adUsage(targets);
            hooks.info("privacy: " + name + " hooks=" + installed);
        } catch (Throwable failure) {
            hooks.error("privacy: " + name + " scan unavailable; stock behaviour kept", failure);
        }
    }

    /** Carrier device registration: the network listener, the history upload and both senders. */
    private void operator(DexTargets targets) {
        answer(targets, "op_listen", "void", null, new Class<?>[] {Context.class}, true, "listenNetworkState---->");
        answer(targets, "op_history", "void", null, new Class<?>[0], false, "HistoryPostThread", "post history result:");
        answer(targets, "op_http", "boolean", false, new Class<?>[] {String.class, byte[].class}, null,
                "sendHttpFallbackRequest()", "application/encrypted-json");
        answer(targets, "op_https", "boolean", false, new Class<?>[] {String.class, byte[].class, byte[].class}, null,
                "sendHttpRequest() path =", "application/encrypted-json");
    }

    /** China Telecom terminal registration: the trigger and the upload itself. */
    private void telecom(DexTargets targets) {
        answer(targets, "tel_trigger", "void", null, new Class<?>[] {String.class}, null, "AutoReg_DataWork", "tryToRegister reason=");
        answer(targets, "tel_upload", "void", null, new Class<?>[0], false, "https://zzhc.vnet.cn", "http://typdm.tydevice.com?jsonstr=");
    }

    /** MiSight event upload: the "upload allowed" answer, the upload job and its queue. */
    private void miSight(DexTargets targets) {
        try {
            Method allowed = loader.loadClass("com.miui.misightservice.utils.Device").getDeclaredMethod("isEventUploadEnable");
            if (allowed.getReturnType() == boolean.class) {
                hooks.constant(allowed, false);
                installed++;
            }
        } catch (ReflectiveOperationException missing) {
            hooks.info("privacy: MiSight upload switch not found");
        }
        answer(targets, "ms_job", "boolean", false, new Class<?>[] {JobParameters.class}, false,
                "EventUploadJobService boot time less than 70min, kill self");
        answer(targets, "ms_enqueue", "void", null, new Class<?>[] {Context.class, Intent.class}, null,
                "EventUploadIntentService equework");
    }

    /** The ad service's app usage profile: it gets "never used" instead of the real usage times. */
    private void adUsage(DexTargets targets) {
        answer(targets, "ad_last_used", "long", -1L, new Class<?>[] {String.class, long.class, long.class}, null,
                "AppUsageStateModel", "getLastUsedTime start:");
        answer(targets, "ad_first_active", "long", -1L, new Class<?>[] {Context.class, String.class}, null,
                "getFirstActiveTime pkg=");
    }

    /**
     * Makes the one method with this shape answer {@code value}. {@code isStatic} null accepts both.
     * Not found, or found more than once: nothing is hooked.
     */
    private void answer(DexTargets targets, String name, String returnType, Object value, Class<?>[] params,
            Boolean isStatic, String... strings) {
        try {
            List<Method> found = new ArrayList<>();
            for (Method method : targets.usingStrings("privacy_" + name, returnType, strings)) {
                if (!java.util.Arrays.equals(method.getParameterTypes(), params)) continue;
                if (Modifier.isAbstract(method.getModifiers())) continue;
                if (isStatic != null && isStatic != Modifier.isStatic(method.getModifiers())) continue;
                found.add(method);
            }
            if (found.size() != 1) {
                hooks.info("privacy: " + name + " not unique (" + found.size() + "), left alone");
                return;
            }
            hooks.constant(found.get(0), value);
            installed++;
        } catch (Throwable failure) {
            hooks.error("privacy: " + name + " unavailable", failure);
        }
    }
}
