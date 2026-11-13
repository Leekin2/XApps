# Changelog

## 1.0.6

- XSecurity: optional Video toolbox music recognition shortcut for compatible Mi Music builds.
- XSecurity: optional display refresh rate lock based on HyperCeiler LockMaxFps.
- XSecurity: optional Game Center promotion and booster offer removal.
- XSecurity: theatre mode, Dolby, MEMC, super resolution, contour enhancement and video temperature control have separate switches. Existing video settings still apply until individually changed.
- XSecurity: screen-off video sound is controlled by its own switch. Game Turbo extras no longer override its device flag.
- XNotify: the notification receive screen finds the device manager on MiLink 18.2.4 as well as older builds.
- XGoogle: the scope status explains why Google Play services and Google Services Framework are optional Timeline scopes.
- XSecurity: optional one-time approval for Xiaomi app-to-app launch prompts, off by default. File-sharing and other permission prompts are excluded.
- New XSecurity section: Privacy, four switches, all off by default: stop carrier data collection, stop China Telecom device registration, stop MiSight uploads, and give the ad service no app usage data. Each needs its app approved in the LSPosed scope. Idea from Hyper Privacy Guard.
- XGoogle: the Talk and Create entries in Google Lens are back on newer Google app versions (17.70 to 17.73). Fix from Coogle 1.3.6-1.3.9 by zopulus.
- New XSecurity switch (experimental, off by default): Call noise reduction. Shows the Noise reduction button in the call toolbox; it only has an effect on devices whose audio hardware supports it.
- New XSecurity fix, on by default: floating windows work while Google's motion cues are on. Their bar (move, close, menu) ignored every touch because HyperOS counted the motion cues overlay as a full-screen touch-blocking area. Analysis and fix design by xrh0905.

## 1.0.5

- New XSecurity switch: Video sound with screen off. Shows this video toolbox shortcut on Global ROMs and on devices where Xiaomi hid it, when the system supports it. Off by default.
- Extend Unlock: the lock screen now takes the trusted state straight from Android instead of working it out itself. Before, a short moment after the lock screen appeared could count as unlocked.
- Clock seconds: the time no longer shows a wrong minute for a moment when the minute changes, a clock that also shows the date gets the seconds after the time, and the clock is not refreshed while the screen is off.
- Google map: when the Google layer can't be added, Baidu's map comes back with its logo and the chosen map type instead of an empty map.
- Moving from the old XGoogle, XSecurity or XNotify apps: an old app that is kept because its settings could not be copied is now switched off in LSPosed, so it doesn't run next to XApps.
- Removing the module now also resets the power key double press when Google Wallet was chosen from the power key screen.
- A clearer message when the Extend Unlock page can't open.

## 1.0.4

- New XSecurity switch: seconds on the status bar clock (HH:MM:SS). Off by default.
- New XSecurity switch: Extend Unlock (Smart Lock). Brings back Google's feature that keeps the phone unlocked with a device or place you trust. Off by default. Based on HyperTrust by StevenWin818.
- Moving from the old XGoogle, XSecurity or XNotify apps: an old app is no longer uninstalled when its settings could not be copied, so they are not lost. XApps tells you to remove it by hand.
- Removing the XApps module now puts the double press of the power key back if it opened Google Wallet.
- Google Wallet is no longer added to the LSPosed scope automatically. You are asked when you turn its switch on. If it is already ticked and you don't use it, untick it in LSPosed.
- Find Device: refreshing one device no longer delays refreshing another, and a failed refresh can be retried right away.
- Find Device: Route now opens Google Maps directly. Without Google Maps, it asks which map app to open.
- New XSecurity switch (experimental): Google map. The map inside Find Device shows Google's map, without Baidu's labels and logo. Off by default.

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
