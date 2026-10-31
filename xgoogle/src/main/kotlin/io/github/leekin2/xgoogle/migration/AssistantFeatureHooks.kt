// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SYSTEM_PACKAGE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.ANDROID_PACKAGE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.GOOGLE_ASSISTANT_APP_FEATURE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.CONTEXTUAL_SEARCH_SYSTEM_FEATURE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.BREENO_SPEECH_FEATURE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks

internal object AssistantFeatureHooks {
    fun hookNativeAssistantFeatureRoute(packageName: String, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.oplus.content.OplusFeatureConfigManager",
            "com.android.server.content.OplusFeatureConfigManagerService",
        )
        classNames.forEach { className ->
            val cls = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
            cls.declaredMethods
                .filter { method ->
                    method.name in setOf("hasFeature", "hasFeatureMap") &&
                        method.returnType == java.lang.Boolean.TYPE &&
                        method.parameterTypes.firstOrNull() == String::class.java
                }
                .forEach { method ->
                    val key = "$packageName:native-assistant-feature:${method.toGenericString()}"
                    if (!installedHooks.add(key)) return@forEach
                    runCatching {
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    val featureName = param.args.firstOrNull() as? String ?: return
                                    if (featureName == BREENO_SPEECH_FEATURE) {
                                        param.result = false
                                    }
                                }
                            },
                        )
                    }.onFailure {
                        installedHooks.remove(key)
                        log("Failed to override $BREENO_SPEECH_FEATURE in $className", it)
                    }
                }
        }
    }
    fun hookGoogleAssistantAppFeature(classLoader: ClassLoader) {
        val providerClass = XposedHelpers.findClassIfExists(
            "com.oplus.coreapp.appfeature.AppFeatureProviderUtils",
            classLoader,
        )
        providerClass?.declaredMethods
            ?.filter { method ->
                method.returnType == java.lang.Boolean.TYPE &&
                    method.name in setOf("getBoolean", "isFeatureSupport") &&
                    method.parameterTypes.any { it == String::class.java }
            }
            ?.forEach { method ->
                val key = "systemui:native-assistant-app-feature:${method.toGenericString()}"
                if (!installedHooks.add(key)) return@forEach
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (param.args.any { it == GOOGLE_ASSISTANT_APP_FEATURE }) {
                                    param.result = true
                                }
                            }
                        },
                    )
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to override Google Assistant app feature", it)
                }
            }

        val optionClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.QSFeatureOption",
            classLoader,
        ) ?: return
        forceStaticBoolean(optionClass, "isGoogleAssistantSupport", true)
        val key = "systemui:native-assistant-option"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                optionClass,
                "loadAppFeature",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        forceStaticBoolean(optionClass, "isGoogleAssistantSupport", true)
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to keep Google Assistant SystemUI option enabled", it)
        }
    }
    fun hookContextualSearchSystemFeature(
        packageName: String,
        classLoader: ClassLoader,
    ) {
        val classNames = buildList {
            add("android.app.ApplicationPackageManager")
            if (packageName == SYSTEM_PACKAGE || packageName == ANDROID_PACKAGE) {
                // The service-side hook makes the synthetic XML feature visible to unscoped
                // clients such as the Google app too, matching a real permissions XML entry.
                add("com.android.server.pm.PackageManagerService")
                add("com.android.server.pm.PackageManagerService\$IPackageManagerImpl")
                add("com.android.server.pm.ComputerEngine")
            }
        }
        classNames.forEach { className ->
            val cls = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
            cls.declaredMethods
                .filter { method ->
                    method.name == "hasSystemFeature" &&
                        method.returnType == java.lang.Boolean.TYPE &&
                        method.parameterTypes.firstOrNull() == String::class.java
                }
                .forEach { method ->
                    val key =
                        "$packageName:contextual-search-system-feature:${method.toGenericString()}"
                    if (!installedHooks.add(key)) return@forEach
                    runCatching {
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (param.args.firstOrNull() == CONTEXTUAL_SEARCH_SYSTEM_FEATURE &&
                                        LspConfig.isAssistantNativeCircleEnabledXposed()) {
                                        param.result = true
                                    }
                                }
                            },
                        )
                    }.onFailure {
                        installedHooks.remove(key)
                        log("Failed to expose contextual-search system feature in $className", it)
                    }
                }
        }
    }
    fun forceStaticBoolean(cls: Class<*>, fieldName: String, value: Boolean) {
        runCatching {
            cls.getDeclaredField(fieldName).apply { isAccessible = true }.setBoolean(null, value)
        }.onFailure {
            log("Failed to set ${cls.name}#$fieldName", it)
        }
    }
}
