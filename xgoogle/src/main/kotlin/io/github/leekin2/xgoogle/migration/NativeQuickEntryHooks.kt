package io.github.leekin2.xgoogle.migration

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect

/** Offer the native Wallet tile on CN ROMs; its action and setup requirements remain native. */
internal object NativeQuickEntryHooks {
    fun install(loader: ClassLoader) {
        installPart("wallet tile") { hookWalletTile(loader) }
    }

    private inline fun installPart(name: String, block: () -> Unit) {
        runCatching(block).onSuccess { HookLog.i("NativeQuickEntries", "Restored $name") }
            .onFailure { HookLog.w("NativeQuickEntries", "Could not restore $name; original behavior retained", it) }
    }

    private fun hookWalletTile(loader: ClassLoader) {
        val cls = ModernReflect.findClass("com.android.systemui.qs.tiles.QuickAccessWalletTile", loader)
        val contextField = ModernReflect.findField(cls, "mContext")
        val hostField = ModernReflect.findField(cls, "mHost")
        val getUserId = ModernReflect.findMethodExact(hostField.type, "getUserId", emptyArray())
        ModernReflect.findAndHookMethod(cls, "isAvailable", object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tile = param.thisObject ?: return
                val context = contextField.get(tile) as Context
                val pm = context.packageManager
                val walletRoleAvailable = getUserId.invoke(hostField.get(tile)) == 0 &&
                    context.getSystemService(RoleManager::class.java).isRoleAvailable("android.app.role.WALLET")
                // Same native conditions, excluding only the export-region test.
                param.result = (walletRoleAvailable || pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) &&
                    !pm.hasSystemFeature("org.chromium.arc")
            }
        })
    }

}
