package io.github.leekin2.xgoogle.migration

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import io.github.leekin2.xgoogle.FeatureConfig
import io.github.leekin2.xgoogle.FeaturePolicy
import io.github.leekin2.xgoogle.WalletPowerPolicy
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect

/** Native wallet route; member bindings are resolved once per process. */
internal object WalletPowerHooks {
    fun hookPackage(packageName: String, loader: ClassLoader) {
        if (packageName != "com.google.android.apps.walletnfcrel") return
        showOverLockScreen()
    }

    /**
     * On HyperOS the tap-to-pay screen opened by the power key waits behind the lock screen until
     * it is unlocked. Show it over the lock screen like on Pixel; paying still needs Google's own
     * verification.
     */
    private fun showOverLockScreen() {
        runCatching {
            ModernReflect.findAndHookMethod(android.app.Instrumentation::class.java, "callActivityOnCreate",
                Activity::class.java, android.os.Bundle::class.java,
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val activity = param.args.firstOrNull() as? Activity ?: return
                        if (!FeatureConfig.enabled(FeaturePolicy.WALLET)) return
                        if (!WalletPowerPolicy.isQuickDrawPreview(activity.componentName.className, activity.intent?.action)) return
                        activity.setShowWhenLocked(true)
                        activity.setTurnScreenOn(true)
                        // Not still over the lock screen the next time the phone wakes.
                        activity.registerReceiver(object : android.content.BroadcastReceiver() {
                            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                                runCatching { activity.unregisterReceiver(this) }
                                activity.finish()
                            }
                        }, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF), android.content.Context.RECEIVER_EXPORTED)
                    }
                },
            )
            HookLog.i("WalletPowerHooks", "Tap-to-pay screen shows over the lock screen")
        }.onFailure { HookLog.w("WalletPowerHooks", "Tap-to-pay screen stays behind the lock screen", it) }
    }

    fun hook(loader: ClassLoader) {
        hookHyperOs(loader)
    }

    /** Google Wallet's tap-to-pay screen; it shows over the lock screen and asks for the lock itself. */
    private fun openGoogleWallet(context: Context, user: Int): Boolean {
        runCatching { context.packageManager.getPackageInfo(WalletPowerPolicy.WALLET_PACKAGE, 0) }.getOrNull() ?: return false
        val intent = android.content.Intent(WalletPowerPolicy.QUICKDRAW_ACTION)
            .setClassName(WalletPowerPolicy.WALLET_PACKAGE, WalletPowerPolicy.QUICKDRAW_ACTIVITY)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                // It shows over the lock screen: close it once it is left or the screen goes off.
                or android.content.Intent.FLAG_ACTIVITY_NO_HISTORY or android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        return try {
            val asUser = Context::class.java.getMethod("startActivityAsUser",
                android.content.Intent::class.java, android.os.UserHandle::class.java)
            asUser.invoke(context, intent, android.os.UserHandle.getUserHandleForUid(user * 100000))
            true
        } catch (t: Throwable) {
            HookLog.w("WalletPowerHooks", "Google Wallet not opened; HyperOS handles the power key", t)
            false
        }
    }

    private fun currentUser(): Int =
        android.app.ActivityManager::class.java.getMethod("getCurrentUser").invoke(null) as Int

    /**
     * HyperOS 4 opens the wallet role holder's quick access wallet on a double press of the power
     * key. Google's quick access wallet lives in Play services, not in Google Wallet, so HyperOS
     * finds no wallet and only shows "Can't open the default wallet". Open Google Wallet instead.
     */
    /**
     * HyperOS has its own "Show on lock screen" app permission, and a reinstalled Google Wallet
     * starts without it: the tap-to-pay screen then stays behind the lock screen. Let that one
     * screen show over the lock screen while the Wallet switch is on.
     */
    private fun showQuickDrawWhenLocked(loader: ClassLoader) {
        runCatching {
            val record = ModernReflect.findClass("com.android.server.wm.ActivityRecord", loader)
            val component = ModernReflect.findField(record, "mActivityComponent")
            val intentField = ModernReflect.findField(record, "intent")
            ModernReflect.findAndHookMethod(record, "canShowWhenLocked", object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result == true) return
                    val name = component.get(param.thisObject) as? android.content.ComponentName ?: return
                    if (name.packageName != WalletPowerPolicy.WALLET_PACKAGE) return
                    val action = (intentField.get(param.thisObject) as? android.content.Intent)?.action
                    if (!WalletPowerPolicy.isQuickDrawPreview(name.className, action)) return
                    // Only while the switch is on. Not gated on mShowWhenLocked: with the permission
                    // off HyperOS never records the screen's own request (XGoogle's Wallet hook makes it).
                    if (FeatureConfig.enabled(FeaturePolicy.WALLET)) param.result = true
                }
            })
            HookLog.i("WalletPowerHooks", "Tap-to-pay screen may show over the HyperOS lock screen")
        }.onFailure { HookLog.w("WalletPowerHooks", "Tap-to-pay screen left to HyperOS's lock screen permission", it) }
    }

    private fun hookHyperOs(loader: ClassLoader) {
        showQuickDrawWhenLocked(loader)
        runCatching {
            val target = ModernReflect.findClass("com.miui.server.input.util.ShortCutActionsUtils", loader)
            val contextField = ModernReflect.findField(target, "mContext")
            require(Context::class.java.isAssignableFrom(contextField.type))
            ModernReflect.findAndHookMethod(target, "launchDefaultWallet",
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!FeatureConfig.enabled(FeaturePolicy.WALLET)) return
                        val context = contextField.get(param.thisObject ?: return) as? Context ?: return
                        if (openGoogleWallet(context, currentUser())) param.result = true
                    }
                },
            )
            HookLog.i("WalletPowerHooks", "Installed HyperOS power key route to Google Wallet")
        }.onFailure { HookLog.w("WalletPowerHooks", "HyperOS wallet binding unavailable; original behavior retained", it) }
    }

    /**
     * With the wallet picked for the power key, Android's own gesture service hands the double
     * press to SystemUI, which looks for the same missing quick access wallet. Open Google Wallet.
     */
    /** XGoogle's lock screen Wallet shortcut asks SystemUI to open Wallet. */
    const val SHOW_WALLET = "io.github.leekin2.xgoogle.SHOW_WALLET"

    /**
     * HyperOS asks before one app opens another ("chain start"), and over the lock screen that
     * question stays hidden, so the shortcut seemed to do nothing. SystemUI opens Wallet as for a
     * double press of the power key.
     */
    private fun listenForShortcut() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                val app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Context
                app.registerReceiver(object : android.content.BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: android.content.Intent) {
                        resultCode = if (FeatureConfig.enabled(FeaturePolicy.WALLET) && openGoogleWallet(c, currentUser())) 1 else 0
                    }
                }, android.content.IntentFilter(SHOW_WALLET), AssistantShortcutReceiver.PERMISSION, null, Context.RECEIVER_EXPORTED)
            }.onFailure { HookLog.w("WalletPowerHooks", "Wallet shortcut receiver unavailable", it) }
        }
    }

    fun hookSystemUi(loader: ClassLoader) {
        listenForShortcut()
        runCatching {
            val target = ModernReflect.findClass("com.android.systemui.statusbar.phone.CentralSurfacesCommandQueueCallbacks", loader)
            ModernReflect.findAndHookMethod(target, "onWalletLaunchGestureDetected",
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!FeatureConfig.enabled(FeaturePolicy.WALLET)) return
                        val context = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context ?: return
                        if (openGoogleWallet(context, currentUser())) param.result = null
                    }
                },
            )
            HookLog.i("WalletPowerHooks", "Installed SystemUI wallet gesture route to Google Wallet")
        }.onFailure { HookLog.w("WalletPowerHooks", "SystemUI wallet binding unavailable; original behavior retained", it) }
    }
}
