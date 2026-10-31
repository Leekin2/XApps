package io.github.leekin2.xapps;

import android.app.Application;

/** Starts every part's LSPosed connection once, through the shared ServiceHub. */
public final class XAppsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        io.github.leekin2.xgoogle.XGoogleApp.start(this);
        io.github.leekin2.xsecurity.XSecurityApp.start(this);
        io.github.leekin2.xnotify.ScopeApp.start(this);
    }
}
