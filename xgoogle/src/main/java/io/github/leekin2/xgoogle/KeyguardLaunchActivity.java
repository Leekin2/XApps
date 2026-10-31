package io.github.leekin2.xgoogle;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * HyperOS lock screen shortcuts expect the opened screen to show over the lock screen, and say
 * "Couldn't launch app" when it does not. Google Assistant and Lens never show over it. This
 * invisible screen does; Lens, the Assistant (Gemini), Google Home's controls and Wallet's
 * tap-to-pay screen then open over the lock screen.
 */
public final class KeyguardLaunchActivity extends Activity {
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    private static final String SHOW_ASSISTANT = "io.github.leekin2.xgoogle.SHOW_ASSISTANT";
    private static final String SHOW_HOME_CONTROLS = "io.github.leekin2.xgoogle.SHOW_HOME_CONTROLS";
    private static final String HOME = "com.google.android.apps.chromecast.app";
    private static final String SHOW_WALLET = "io.github.leekin2.xgoogle.SHOW_WALLET";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent target = target(getIntent().getData());
        if (target == null) { finish(); return; }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard == null || !keyguard.isKeyguardLocked()) { open(target); return; }
        // Gemini's panel shows over the lock screen, as after "Hey Google"; so do the Home controls.
        if (SHOW_ASSISTANT.equals(target.getAction())) { showAssistant(); return; }
        if (SHOW_HOME_CONTROLS.equals(target.getAction())) { showHomeControls(keyguard); return; }
        // Lens shows over the lock screen (XGoogle marks it in the Google app), like a camera.
        if (Intent.ACTION_VIEW.equals(target.getAction())) {
            open(target.putExtra(LensHooks.KEYGUARD_EXTRA, true));
            return;
        }
        // Wallet's tap-to-pay screen shows over the lock screen, as from a double press of the
        // power key; Wallet asks for the person's fingerprint/PIN itself when it needs it.
        if (WalletPowerPolicy.QUICKDRAW_ACTION.equals(target.getAction())) { showWallet(keyguard, target); return; }
        keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() { open(target); }
            @Override public void onDismissCancelled() { finish(); }
            @Override public void onDismissError() { finish(); }
        });
    }

    /**
     * Gemini's floating panel, as a long press of the power key opens it: only SystemUI can start
     * that session, so ask XGoogle's SystemUI hook. Without it, open the Gemini app (or the Google
     * app's assistant screen when Gemini is not installed).
     */
    private void showAssistant() {
        Intent ask = new Intent(SHOW_ASSISTANT).setPackage("com.android.systemui");
        sendOrderedBroadcast(ask, null, new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                if (getResultCode() != 1) {
                    Intent gemini = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.bard");
                    Intent app = gemini != null ? gemini : new Intent(Intent.ACTION_ASSIST).setPackage(GOOGLE);
                    try { startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                    catch (Exception e) { Log.w("XGoogle", "Assistant unavailable", e); }
                }
                finish();
            }
        }, null, 0, null, null);
    }

    /**
     * Google Home's device controls, as Android's lock screen controls show them: only SystemUI can
     * open its controls screen, so ask XGoogle's SystemUI hook. Without it, open Google Home after
     * the lock.
     */
    private void showHomeControls(KeyguardManager keyguard) {
        sendOrderedBroadcast(new Intent(SHOW_HOME_CONTROLS).setPackage("com.android.systemui"), null,
                new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                if (getResultCode() == 1) { finish(); return; }
                Intent app = getPackageManager().getLaunchIntentForPackage(HOME);
                if (app == null) { finish(); return; }
                keyguard.requestDismissKeyguard(KeyguardLaunchActivity.this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override public void onDismissSucceeded() { open(app); }
                    @Override public void onDismissCancelled() { finish(); }
                    @Override public void onDismissError() { finish(); }
                });
            }
        }, null, 0, null, null);
    }

    /**
     * SystemUI opens Wallet, as for a double press of the power key: HyperOS would ask before
     * XGoogle opens Wallet, and that question stays hidden behind the lock screen. Without
     * XGoogle's SystemUI hook (or with the Wallet switch off), open Wallet after the lock.
     */
    private void showWallet(KeyguardManager keyguard, Intent wallet) {
        sendOrderedBroadcast(new Intent(SHOW_WALLET).setPackage("com.android.systemui"), null,
                new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                if (getResultCode() == 1) { finish(); return; }
                keyguard.requestDismissKeyguard(KeyguardLaunchActivity.this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override public void onDismissSucceeded() { open(wallet); }
                    @Override public void onDismissCancelled() { finish(); }
                    @Override public void onDismissError() { finish(); }
                });
            }
        }, null, 0, null, null);
    }

    private Intent target(Uri data) {
        String which = data == null ? null : data.getLastPathSegment();
        if ("home".equals(which)) return new Intent(SHOW_HOME_CONTROLS);
        if ("assistant".equals(which)) return new Intent(SHOW_ASSISTANT);
        // Wallet's tap-to-pay screen, as a double press of the power key opens it.
        if ("wallet".equals(which)) return new Intent(WalletPowerPolicy.QUICKDRAW_ACTION)
                .setClassName(WalletPowerPolicy.WALLET_PACKAGE, WalletPowerPolicy.QUICKDRAW_ACTIVITY);
        if ("lens".equals(which)) return new Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(GOOGLE);
        return null;
    }

    private void open(Intent target) {
        if (SHOW_ASSISTANT.equals(target.getAction())) { showAssistant(); return; }
        if (SHOW_HOME_CONTROLS.equals(target.getAction())) {
            Intent app = getPackageManager().getLaunchIntentForPackage(HOME);
            if (app == null) { finish(); return; }
            target = app;
        }
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w("XGoogle", "Lock screen shortcut target unavailable", e);
        }
        finish();
    }
}
