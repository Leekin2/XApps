# Changelog

## 1.0.3

- Find Device fix: now really on by default. In 1.0.2 it stayed off until you turned it on in XSecurity. If you turned it off in 1.0.2, turn it off once more.
- Find Device: the refresh button now asks the other device for its location directly, so it updates without playing the alarm.

## 1.0.2

- **Find Device fix (XSecurity, on by default):** your device's street address reaches Xiaomi's server, so [i.mi.com/find](https://i.mi.com/find) and your other Xiaomi devices show it, usually in 5-10 seconds, even while the app is closed. Works worldwide. Also gives the right location on devices without GPS, keeps device icons in the right place on the map, makes the remote alarm work in every system language, and lets Route pick a map app.
- Camera: the Google Lens button is now found by scanning the app, so it survives camera updates.
- XApps can hide its home screen icon.
- XSecurity: optional switches to remove the high volume warning and to shorten the default notification vibration (both off by default).

## 1.0.1

- Fixed: if the module's `module.prop` was cut short (for example by a crash while it was being updated), the mount module skipped XApps, so Google stopped being a system app, Hey Google stopped working and Google kept asking for the microphone. `module.prop` is now always rebuilt from a clean copy.
- Version code raised to 141, so LSPosed shows XApps as an update to XGoogle, XSecurity and XNotify.

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
