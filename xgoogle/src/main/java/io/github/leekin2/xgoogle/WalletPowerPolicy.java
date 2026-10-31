package io.github.leekin2.xgoogle;

/** Boundaries for native wallet routing and locked card preview. */
public final class WalletPowerPolicy {
    public static final String WALLET_PACKAGE = "com.google.android.apps.walletnfcrel";
    public static final String QUICKDRAW_ACTION = "com.google.android.apps.wallet.main.QUICKDRAW";
    public static final String QUICKDRAW_ACTIVITY = "com.google.android.apps.wallet.pmode.PresentationModeActivity";

    private WalletPowerPolicy() { }

    public static boolean isQuickDrawPreview(String activityName, String action) {
        return "com.google.android.apps.wallet.pmode.PresentationModeActivity".equals(activityName)
                && "com.google.android.apps.wallet.main.QUICKDRAW".equals(action);
    }

    public static boolean isGooglePayment(String paymentComponent) {
        return paymentComponent != null
                && paymentComponent.startsWith("com.google.android.gms/");
    }
}
