# Changelog

## 1.0

First release: XGoogle 1.4.0, XSecurity 1.8 and XNotify 4.0 in one app and one root module.

- One LSPosed module, `io.github.leekin2.xapps`. Each part (XGoogle, XSecurity, XNotify) has its own switch on the home screen; a part that is off is never loaded and hooks nothing. Switching takes effect after a reboot.
- Each part keeps its own settings screen, opened from the home screen.
- One root module, `xapps` (KernelSU, Magisk, APatch). Flashing it:
  - installs the XApps app,
  - enables XApps in LSPosed and ticks its scope (only the apps that are installed),
  - copies the settings of the separate XGoogle, XSecurity and XNotify modules, then uninstalls them (and XReply),
  - puts XGoogle's system parts in place (Google as a system app, Hey Google components, Android Auto, calendar sync, Google permissions). This replaces the separate `xgoogle_companion` module, which is removed on the next reboot; your power key and navigation handle choices are kept.
- Without the Google app the root module still installs; only the Google parts are skipped.
- Removing the root module removes the app and puts the power key and navigation handle choices back.
- Home screen in Turkish, English, Spanish, Russian and Chinese.
