package io.github.leekin2.xgoogle;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.CompoundButton;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

/**
 * Passkeys and password managers on the China ROM, through Google Play services:
 * - system: credential requests get Google's hybrid (phone-as-key) service and Google's
 *   account chooser, which the China ROM leaves unset;
 * - Settings: the password manager pickers behave as on the global ROM (IS_INTERNATIONAL_BUILD
 *   while they run), and the provider switches work again on Android 17;
 * - Security: it no longer resets the autofill and credential services to Xiaomi's defaults;
 * - Scanner: FIDO QR codes go to the system instead of Xiaomi's own handler.
 * Ported from Howard20181/HyperPasskey (GPL-3.0) for XGoogle. Google Play services must be enabled.
 */
final class PasskeyHooks {
    static final String SETTINGS = "com.android.settings", SECURITY = "com.miui.securitycenter", SCANNER = "com.xiaomi.scanner";
    private static final String HYBRID = "com.google.android.gms/.auth.api.credentials.credman.service.RemoteService";
    private static final String CHOOSER = "com.google.android.gms/.identitycredentials.ui.CredentialChooserActivity";
    private static final String CREDENTIALS = "com.android.settings.applications.credentials";
    private static final ReentrantLock GLOBAL = new ReentrantLock();

    static boolean handles(String packageName) {
        return SETTINGS.equals(packageName) || SECURITY.equals(packageName) || SCANNER.equals(packageName);
    }

    static void hookSystem(HookInstaller hooks, ClassLoader loader) {
        try {
            Field hybrid = Class.forName("com.android.server.credentials.RequestSession", false, loader).getDeclaredField("mHybridService");
            Class<?> session = hybrid.getDeclaringClass();
            for (String provider : new String[]{"ProviderGetSession", "ProviderCreateSession"}) {
                try { hooks.deoptimizeCallers(Class.forName("com.android.server.credentials." + provider, false, loader), "createNewSession"); }
                catch (ClassNotFoundException ignored) { }
            }
            for (Constructor<?> constructor : session.getDeclaredConstructors()) {
                hooks.install(constructor, chain -> {
                    Object result = chain.proceed();
                    if (FeatureConfig.enabled(FeaturePolicy.PASSKEY)) FinalFields.set(hybrid, chain.getThisObject(), HYBRID);
                    return result;
                });
            }
            hooks.info("PASSKEY: Google hybrid service set for credential requests");
        } catch (Throwable error) {
            hooks.error("PASSKEY: hybrid service left unset", error);
        }
        try {
            hookChooser(hooks);
        } catch (Throwable error) {
            hooks.error("PASSKEY: Google account chooser not offered", error);
        }
    }

    /** IntentFactory.getOemOverrideComponentName(Context, IntentCreationResult.Builder[, int]): offer Google's chooser. */
    private static void hookChooser(HookInstaller hooks) throws ReflectiveOperationException {
        Class<?> factory = Class.forName("android.credentials.selection.IntentFactory");
        Class<?> builder = Class.forName("android.credentials.selection.IntentCreationResult$Builder");
        Class<?> status = Class.forName("android.credentials.selection.IntentCreationResult$OemUiUsageStatus");
        Method setPackage = builder.getMethod("setOemUiPackageName", String.class);
        Method setStatus = builder.getMethod("setOemUiUsageStatus", status);
        @SuppressWarnings({"unchecked", "rawtypes"}) Object success = Enum.valueOf((Class<Enum>) status, "SUCCESS");
        int count = 0;
        for (Method method : factory.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.getName().equals("getOemOverrideComponentName") || types.length < 2
                    || types[0] != Context.class || types[1] != builder) continue;
            hooks.install(method, chain -> {
                if (!FeatureConfig.enabled(FeaturePolicy.PASSKEY) || !(chain.getArg(0) instanceof Context context)) return chain.proceed();
                ComponentName chooser = ComponentName.unflattenFromString(CHOOSER);
                if (!usable(context.getPackageManager(), chooser)) return chain.proceed();
                setPackage.invoke(chain.getArg(1), chooser.getPackageName());
                setStatus.invoke(chain.getArg(1), success);
                return chooser;
            });
            count++;
        }
        hooks.info("PASSKEY: Google account chooser offered through " + count + " method(s)");
    }

    /** A system-app activity that is exported and not switched off. */
    private static boolean usable(PackageManager pm, ComponentName component) {
        try {
            ActivityInfo info = pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY));
            int state = pm.getComponentEnabledSetting(component);
            boolean enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED && info.enabled;
            return enabled && info.exported;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    static void install(HookInstaller hooks, ClassLoader loader, String packageName, String apkPath) {
        try {
            switch (packageName) {
                case SETTINGS -> hookSettings(hooks, loader, apkPath);
                case SECURITY -> hookSecurity(hooks, loader, apkPath);
                case SCANNER -> {
                    Method owner = Class.forName("com.xiaomi.scanner.module.code.utils.bean.MiFiDoBean", false, loader)
                            .getDeclaredMethod("getAppPackageName");
                    hooks.install(owner, chain -> "");
                    hooks.info("PASSKEY: Scanner hands FIDO codes to the system");
                }
                default -> { }
            }
        } catch (Throwable error) {
            hooks.error("PASSKEY: " + packageName + " not adjusted", error);
        }
    }

    private static void hookSettings(HookInstaller hooks, ClassLoader loader, String apkPath) throws Exception {
        Field international = Class.forName("miui.os.Build", false, loader).getDeclaredField("IS_INTERNATIONAL_BUILD");
        international.setAccessible(true);
        List<Method> global = new ArrayList<>();
        addMethod(global, loader, CREDENTIALS + ".DefaultCombinedPicker", "setDefaultKey", String.class);
        addMethod(global, loader, CREDENTIALS + ".DefaultCombinedPreferenceController", "getCombinedProviderInfos",
                Class.forName("android.credentials.CredentialManager"), int.class);
        int switches = 0;
        try (DexKitBridge bridge = dexKit(apkPath)) {
            String launch = "L" + CREDENTIALS.replace('.', '/') + "/CombinedProviderInfo;->launchSettingsActivityIntent(Landroid/content/Context;Ljava/lang/CharSequence;Ljava/lang/CharSequence;I)";
            for (MethodData data : bridge.findMethod(FindMethod.create().searchPackages(CREDENTIALS).matcher(MethodMatcher.create()
                    .name("onLeftSideClicked").paramCount(0)
                    .anyOf(MethodMatcher.create().addInvoke(launch + "Z"), MethodMatcher.create().addInvoke(launch + "V")))))
                global.add(data.getMethodInstance(loader));
            switches = fixProviderSwitches(hooks, loader, bridge);
        } catch (Throwable error) {
            hooks.error("PASSKEY: Settings provider list search failed; the pickers are still adjusted", error);
        }
        for (Method method : global) {
            hooks.deoptimize(method);
            hooks.install(method, chain -> {
                if (!FeatureConfig.enabled(FeaturePolicy.PASSKEY)) return chain.proceed();
                return asGlobal(international, chain);
            });
        }
        hooks.info("PASSKEY: Settings pickers as on the global ROM (" + global.size() + " methods), " + switches + " switch fix(es)");
    }

    /** Runs the call with IS_INTERNATIONAL_BUILD true; nested and parallel calls share one change. */
    private static Object asGlobal(Field international, io.github.libxposed.api.XposedInterface.Chain chain) throws Throwable {
        GLOBAL.lock();
        try {
            boolean outer = GLOBAL.getHoldCount() == 1, changed = false;
            // If the flag cannot be written the picker still runs, only as on the China ROM.
            if (outer && !international.getBoolean(null)) {
                try { FinalFields.set(international, null, true); changed = true; }
                catch (ReflectiveOperationException | RuntimeException refused) { }
            }
            try {
                return chain.proceed();
            } finally {
                if (changed) FinalFields.set(international, null, false);
            }
        } finally {
            GLOBAL.unlock();
        }
    }

    /**
     * Android 17 Settings: HyperOS's layout leaves CombiPreference without its switch view, so the
     * provider switches neither show their state nor react. Bind the switch and send its clicks to
     * the controller's onCheckChanged.
     */
    private static int fixProviderSwitches(HookInstaller hooks, ClassLoader loader, DexKitBridge bridge) throws Exception {
        Class<?> combi;
        try {
            combi = Class.forName(CREDENTIALS + ".CredentialManagerPreferenceController$CombiPreference", false, loader);
        } catch (ClassNotFoundException olderSettings) {
            return 0;
        }
        String controller = "L" + CREDENTIALS.replace('.', '/') + "/CredentialManagerPreferenceController;->";
        List<MethodData> found = bridge.findMethod(FindMethod.create().searchPackages(CREDENTIALS).matcher(MethodMatcher.create()
                .name("onCheckChanged").paramTypes(combi.getName(), "boolean")
                .anyOf(MethodMatcher.create().addInvoke(controller + "togglePackageNameEnabled(Ljava/lang/String;)Z"),
                        MethodMatcher.create().addInvoke(controller + "togglePackageNameDisabled(Ljava/lang/String;)V"))));
        if (found.isEmpty()) return 0;
        Method onCheckChanged = found.get(0).getMethodInstance(loader);
        onCheckChanged.setAccessible(true);
        Field checked = field(combi, "mChecked"), listener = field(combi, "mOnClickListener"), switchView = field(combi, "mSwitch");
        Method describe = combi.getDeclaredMethod("maybeUpdateContentDescription");
        describe.setAccessible(true);
        Field itemView = field(Class.forName("androidx.recyclerview.widget.RecyclerView$ViewHolder", false, loader), "itemView");
        int widgetId = Class.forName("com.android.settingslib.R$id", false, loader).getDeclaredField("switchWidget").getInt(null);
        Method bind = combi.getDeclaredMethod("onBindViewHolder", Class.forName("androidx.preference.PreferenceViewHolder", false, loader));
        hooks.install(bind, chain -> {
            Object result = chain.proceed();
            Object preference = chain.getThisObject();
            if (!FeatureConfig.enabled(FeaturePolicy.PASSKEY) || switchView.get(preference) != null
                    || !(itemView.get(chain.getArg(0)) instanceof View row)
                    || !(row.findViewById(widgetId) instanceof CompoundButton button)) return result;
            button.setChecked(checked.getBoolean(preference));
            button.setOnClickListener(view -> {
                try {
                    Object click = listener.get(preference);
                    if (click == null) return;
                    if (!(boolean) onCheckChanged.invoke(click, preference, button.isChecked())) {
                        checked.setBoolean(preference, false);
                        button.setChecked(false);
                    }
                } catch (ReflectiveOperationException error) {
                    hooks.error("PASSKEY: provider switch failed", error);
                }
            });
            switchView.set(preference, button);
            describe.invoke(preference);
            return result;
        });
        return 1;
    }

    /**
     * Security sets autofill_service, credential_service and credential_service_primary to
     * Xiaomi's defaults at start; skip those two setters so the user's choice stays.
     */
    private static void hookSecurity(HookInstaller hooks, ClassLoader loader, String apkPath) throws Exception {
        try (DexKitBridge bridge = dexKit(apkPath)) {
            List<ClassData> classes = new ArrayList<>();
            for (String name : new String[]{"Lcom/miui/securitycenter/Application;", "Lcom/miui/securitycenter/service/CacheService;"}) {
                ClassData data = bridge.getClassData(name);
                if (data != null) classes.add(data);
            }
            int skipped = 0;
            String put = "Landroid/provider/Settings$Secure;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z";
            String[][] setters = {
                    {"Landroid/content/res/Resources;->getString(I)Ljava/lang/String;", "autofill_service"},
                    {"Landroid/content/res/Resources;->getStringArray(I)[Ljava/lang/String;", "credential_service", "credential_service_primary"}};
            for (String[] setter : setters) {
                List<MethodData> helper = bridge.findMethod(FindMethod.create().searchInClass(classes).matcher(MethodMatcher.create()
                        .anyOf(MethodMatcher.create().paramTypes(Context.class, String.class, int.class),
                                MethodMatcher.create().paramTypes(String.class, int.class))
                        .addInvoke(setter[0]).addInvoke(put)));
                if (helper.size() != 1) continue;
                hooks.deoptimize(helper.get(0).getMethodInstance(loader));
                MethodMatcher caller = MethodMatcher.create().addInvoke(helper.get(0).toString());
                if (setter.length == 2) caller.addEqString(setter[1]); else caller.usingEqStrings(setter[1], setter[2]);
                for (MethodData data : bridge.findMethod(FindMethod.create().searchInClass(classes).matcher(caller))) {
                    hooks.install(data.getMethodInstance(loader), chain ->
                            FeatureConfig.enabled(FeaturePolicy.PASSKEY) ? null : chain.proceed());
                    skipped++;
                }
            }
            hooks.info("PASSKEY: Security keeps the chosen password manager (" + skipped + " setter(s) skipped)");
        }
    }

    private static DexKitBridge dexKit(String apkPath) {
        AppDexTargets.loadNative();
        return DexKitBridge.create(apkPath);
    }

    private static void addMethod(List<Method> into, ClassLoader loader, String type, String name, Class<?>... parameters) {
        try { into.add(Class.forName(type, false, loader).getDeclaredMethod(name, parameters)); }
        catch (ReflectiveOperationException absent) { }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private PasskeyHooks() { }
}
