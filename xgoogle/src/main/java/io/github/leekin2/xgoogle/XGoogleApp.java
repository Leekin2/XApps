package io.github.leekin2.xgoogle;

import android.app.Application;

/** Connects to LSPosed as soon as XApps starts, so new scopes are offered without the UI. */
public final class XGoogleApp {
    public static void start(Application app) {
        SettingsConnection.start(app);
    }

    private XGoogleApp() { }
}
