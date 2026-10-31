#!/system/bin/sh
# XApps: one module for XGoogle, XSecurity and XNotify. Installs the app, sets LSPosed up and
# puts XGoogle's system parts in place (they used to be the separate xgoogle_companion module).
PACKAGE=com.google.android.googlequicksearchbox
APP_DIR=$MODPATH/system/priv-app/GoogleQuickSearchBox

if [ "$API" -lt 35 ]; then
    abort "! Android 15 or newer is required (this device: API $API)"
fi

. "$MODPATH/common.sh"

# The app and LSPosed. The zip from the XApps app itself carries no APK.
ui_print "- XApps app"
app_ok=true
out=$(xapps_setup "$MODPATH/xapps.apk") || app_ok=false
echo "$out" | while read -r line; do ui_print "  $line"; done
[ "$app_ok" = true ] && rm -f "$MODPATH/xapps.apk"

# Saved choices of the old companion ids move to the current names.
for name in keys_backup nav_off double_click_power_key; do
    [ -e "/data/adb/coogle_$name" ] && [ ! -e "/data/adb/xgoogle_$name" ] && mv "/data/adb/coogle_$name" "/data/adb/xgoogle_$name"
done
# Retire the separate companion modules; without their uninstall script the saved key choices
# stay for this module, and the root manager removes them on the next boot.
for old in coogle_companion xgoogle_companion; do
    for dir in /data/adb/modules/$old /data/adb/modules_update/$old; do
        [ -d "$dir" ] || continue
        rm -f "$dir/uninstall.sh"
        touch "$dir/remove"
    done
    [ -d /data/adb/modules/$old ] || [ -d /data/adb/modules_update/$old ] && ui_print "- Old $old module will be removed on reboot (XApps contains it)"
done

# XGoogle: the Google app as a system app.
ui_print "- XGoogle system parts"
mkdir -p "$APP_DIR"
find "$APP_DIR" -maxdepth 1 -type f -name '*.apk' -delete 2>/dev/null
paths=$(pm path "$PACKAGE" 2>/dev/null | sed -n 's/^package://p')
google=true
IFS='
'
for apk in $paths; do
    cp -f "$apk" "$APP_DIR/${apk##*/}" || google=false
done
unset IFS
[ -f "$APP_DIR/base.apk" ] || google=false
if [ "$google" = true ]; then
    # NoMount merges instead of replacing; the same check runs again on every boot (post-fs-data.sh).
    . "$MODPATH/mount-google.sh"
    prepare_google_mount "$MODPATH" || google=false
fi
if [ "$google" = true ]; then
    if [ -f "$APP_DIR/.replace" ]; then ui_print "  Google is a system app (replace mode)"; else ui_print "  Google is a system app (NoMount merge mode)"; fi
else
    rm -rf "$APP_DIR"
    ui_print "  Google app not found: install it from the Play Store, then flash XApps again"
fi

# Android Auto: a system stub; the Play Store version installs over it at any time.
case "$(/system/bin/sh "$MODPATH/systemize-aa.sh" "$MODPATH")" in
    missing) ui_print "  Android Auto stub added" ;;
    ok*) ui_print "  Android Auto runs as a system app" ;;
    *) ui_print "  Android Auto stub added; its permission list is refreshed at boot" ;;
esac

# Newer KernelSU builds mount module files only through a mount metamodule.
if [ "$KSU" = true ] && [ ! -e /data/adb/metamodule ]; then
    ui_print "! No mount module found: if Google is not a system app after the reboot,"
    ui_print "  install Hybrid Mount and reboot again"
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644
for script in boot-completed.sh service.sh setup.sh uninstall.sh prepare-audio.sh status.sh systemize-aa.sh grant-google.sh; do
    set_perm "$MODPATH/$script" 0 0 0755
done
/system/bin/sh "$MODPATH/prepare-audio.sh"

if [ "$app_ok" = true ]; then
    ui_print "- Done. Reboot, XApps is ready."
else
    ui_print "- The XApps app is installed on the next boot instead (then reboot once more)."
fi
