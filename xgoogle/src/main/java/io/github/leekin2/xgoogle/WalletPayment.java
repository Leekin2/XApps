package io.github.leekin2.xgoogle;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Process;
import android.provider.Settings;
import java.io.IOException;

/**
 * Makes Google the default NFC payment app when the Wallet switch is turned on, through the
 * system's own wallet role (or the older NFC setting); the double press opens the default wallet
 * only.
 */
final class WalletPayment {
    enum Problem { NO_WALLET, NO_NFC, FAILED }
    static final class Failure extends IOException {
        final Problem problem;
        Failure(Problem problem) { super("Wallet payment: " + problem); this.problem = problem; }
    }

    private static final String GMS = "com.google.android.gms";
    private static final String DEFAULT = "nfc_payment_default_component";
    private static final String ROLE_WALLET = "android.app.role.WALLET";
    private final Context context;
    private final int user = Process.myUid() / 100000;
    WalletPayment(Context context) { this.context = context; }

    /** Returns true when the default had to be changed. */
    boolean ensureGoogle() throws Exception {
        PackageManager pm = context.getPackageManager();
        try {
            if (!pm.getApplicationInfo(WalletPowerPolicy.WALLET_PACKAGE, 0).enabled) throw new Failure(Problem.NO_WALLET);
        } catch (PackageManager.NameNotFoundException missing) { throw new Failure(Problem.NO_WALLET); }
        if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) throw new Failure(Problem.NO_NFC);
        RoleManager roles = context.getSystemService(RoleManager.class);
        boolean useRole = roles != null && roles.isRoleAvailable(ROLE_WALLET);
        boolean roleIsGoogle = !useRole || GMS.equals(RootShell.run("cmd role get-role-holders --user " + user
                + " " + ROLE_WALLET, 20).trim());
        if (roleIsGoogle && isGoogle(current())) return false;
        // HyperOS does not copy the wallet role into the NFC setting (or the other way round),
        // so set both: the role for the double press, the setting for the tap at the reader.
        String setting = "case \"$(settings --user " + user + " get secure " + DEFAULT + ")\" in " + GMS + "/*) ;; "
                + "*) settings --user " + user + " put secure " + DEFAULT + " '" + paymentService(context.getPackageManager()) + "' || exit 1;; esac";
        RootShell.run((roleIsGoogle ? "" : "cmd role add-role-holder --user " + user + " " + ROLE_WALLET + " " + GMS + " 0 || exit 1; "
                // The role callback may still update the NFC setting itself; give it a moment.
                + "sleep 0.5; ") + setting, 20);
        if (!isGoogle(current())) throw new Failure(Problem.FAILED);
        return true;
    }

    private String current() throws Exception {
        try { return Settings.Secure.getString(context.getContentResolver(), DEFAULT); }
        catch (SecurityException hidden) {
            // Android 12+ hides this key from ordinary apps.
            String value = RootShell.run("settings --user " + user + " get secure " + DEFAULT, 20).trim();
            return value.isEmpty() || "null".equals(value) ? null : value;
        }
    }

    private boolean isGoogle(String component) {
        if (!WalletPowerPolicy.isGooglePayment(component)) return false;
        ComponentName selected = ComponentName.unflattenFromString(component);
        if (selected == null) return false;
        try {
            ServiceInfo service = context.getPackageManager().getServiceInfo(selected,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
            return service.enabled && service.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException missing) { return false; }
    }

    private static String paymentService(PackageManager pm) throws Failure {
        var found = pm.queryIntentServices(new android.content.Intent(android.nfc.cardemulation.HostApduService.SERVICE_INTERFACE)
                .setPackage(GMS), PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        for (var result : found) {
            ServiceInfo service = result.serviceInfo;
            if (service != null && service.enabled && service.exported
                    && "android.permission.BIND_NFC_SERVICE".equals(service.permission)
                    && service.name.endsWith("TpHceService"))
                return new ComponentName(service.packageName, service.name).flattenToString();
        }
        throw new Failure(Problem.FAILED);
    }
}
