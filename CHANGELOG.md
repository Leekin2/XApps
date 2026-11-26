# Changelog

## Unreleased

- XGoogle: a separate switch controls whether XApps mounts its Google app copy as a system app. It takes effect after reboot and keeps the saved APK and splits available for re-enabling.

- XGoogle: with the Google Wallet switch on, a double press of the power key also opens Google Wallet when HyperOS would open Smart Cards (com.miui.tsmclient), as on HyperOS 3 (issue #2).
- XNotify: new "Particle clear animation" switch (off by default). Clear all dissolves the notification cards into drifting particles, then the shade closes as before.
- XNotify: adds "Notification extras" inside HyperOS Settings → Notifications & Control center, adds "Always-on display extras (XAod)" and "Lock screen kits" inside HyperOS Settings → Always-on display & Lock screen, and groups XNotify settings into Notification shade, Super Island, and Other Xiaomi devices.
- XNotify: new "Live charging power on the island" switch (off by default). The charging island shows the power going into the battery right now, updated every second, and a wired island stays up for 10 seconds.
- XNotify: new "Music island follows the song" switch (off by default). The music island bars move with the song playing, in the album art colours.
- XNotify: settings texts translated into Russian, Spanish and Chinese.
- XSecurity: "Keep maximum refresh rate" now also stops PowerKeeper lowering the rate when you switch apps, not only for games.
- XSecurity: new switches, all off by default: no refresh rate drop when the phone is hot (a 60 Hz cap stored earlier is ignored too), no Joyose frame rate cap in games, no CPU cap in Battery Saver, key task boost (devices with SchedAssi), and the PowerKeeper *#*#76937#*#* menu on stable builds. From kashi's FPS Limitations patcher.
- XSecurity: closes a PowerKeeper test hook that let any app point PowerKeeper's cloud settings at another server. Always on while PowerKeeper is in the scope.
- XSecurity: new switches from kashi's Security Center mod, all off by default:
  - Keep the security score at 100.
  - Charge limit: battery protection and smart charging stop at a level you choose (60-100%) instead of 80%.
  - No heads-up notifications over a full-screen video while the video toolbox is on.
  - Game toolbox: a Block shade button, playback controls for the song playing, and a summary after each game (play time, battery used, peak temperature).
  - App info: install and update dates, split APKs, signature, granted permissions, activities (tap to open) and an APK backup to /sdcard/Security.
  - App manager: batch force stop, clear cache, disable or enable.
  - App info always offers the MIUI permission editor.
  - Battery protection page: live current, voltage, power and time to full, and a daily health history.
  - Security settings: crash logs (user apps, system apps, system and boot), readable and copyable.
  - Sidebar: clipboard history (last 50 texts, pin and delete; sensitive copies are never kept) and a two-column sidebar with screenshot, screen recording, clipboard and Circle to Search on top. Each tool closes the sidebar first, so it is never in the screenshot, recording or Circle to Search; the clipboard list opens above it with one short line per entry; the panel is always wide enough for two columns, also after the Security app restarts.
  - Every hidden feature kashi's mod turns on, in six groups: Game Turbo tools, video toolbox, call toolbox, battery and performance modes, system features, and suggestions and ads off.
  - Game & video toolbox frosted glass blur (`KashiPanelGlass`) and per-app daily mobile data limit (MB quota with notify / restrict until midnight) plus enhanced daily data calculation.
- XSecurity: new "Enhanced keyboard toolbar" switch (off by default) in Permissions & system. Unlocks Xiaomi's bottom keyboard bar (clipboard, phrases, keyboard switcher) for Gboard and other third-party keyboards with the Android 15+ bottom-inset fix, and natively unlocks HyperOS's own full-screen keyboard & theme options inside HyperOS Settings → Languages & input.
- XSecurity: the FPS switches are together in the Games section.
- XSecurity: two new optional Core Patch switches (off by default, System Framework scope + reboot): "Remove APK protection (ApkProtectionRemove)" (skips full `verifySignaturesInternal` and scheme/compat restrictions) and "Disable APK signature verification (HyperOS)" (adds HyperOS `PackageManagerServiceImpl.verifyIsolationViolation` / `canBeUpdate` bypass and install-flow signature verification bypass).
- XAod: new separate Always-on display panel on the XApps home screen (com.miui.aod / System UI plugin), from kashi's HyperOS AOD mod, all off by default: grayscale AOD, lunar date in every language, all lock screen editor templates, full screen AOD, flagship effects, auto off timer, battery cutoff, off in darkness or a pocket, burn-in shift interval, notification edge glow with a colour, charging info with its position, now playing, screen state while the AOD shows, smart AOD with the light and proximity sensors, and the AOD features kashi's mod turns on (step counter, AOD wallpaper, video depth wallpapers and more).
- XAod: lock screen kits, as in kashi's HyperOS AOD mod: glass gauges under the lock screen clock (date, temperature, alarm, battery, sunrise and sunset, UV, air quality, steps, and wide device battery and earbuds kits). Long-press the lock screen and open Customize: the kits show in the editor preview; (−) removes a kit, a tap opens the "Add kit" sheet, a long press then a drag up or down places them and a drag sideways reorders them. They can also be edited from Settings → Lock screen → Lock screen kits. Notifications move below the kits, the kits hide on the PIN screen and when the phone is unlocked, and they can also show on the AOD. They replace the old text row at the bottom of the AOD and lock screen.

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
