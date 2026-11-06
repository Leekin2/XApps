#!/system/bin/sh
for package in com.android.hotwordenrollment.xgoogle com.android.hotwordenrollment.okgoogle; do
    am compat reset 255038118 "$package"
done
# HyperOS: restore the key choices saved by boot-completed.sh. The root manager may run
# this before the settings service is up, so wait for boot in the background.
backup=/data/adb/xgoogle_keys_backup
wallet_backup=/data/adb/xgoogle_double_click_power_key
# Always started: without a backup the Wallet double press is only seen once settings is up.
(
    until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
    sleep 5
    # Double press: only if it still opens Wallet, so a later choice of the user stays.
    if [ -f "$wallet_backup" ]; then
        if [ "$(settings get system double_click_power_key)" = launch_QAW_wallet ]; then
            value=$(cat "$wallet_backup")
            if [ -z "$value" ] || [ "$value" = null ]; then value=none; fi
            settings put system double_click_power_key "$value"
        fi
        rm -f "$wallet_backup"
    elif [ "$(settings get system double_click_power_key)" = launch_QAW_wallet ]; then
        settings put system double_click_power_key none
    fi
    [ -f "$backup" ] || exit 0
    for entry in power:long_press_power_key home:long_press_home_key; do
        value=$(sed -n "s/^${entry%%:*}=//p" "$backup")
        if [ -z "$value" ] || [ "$value" = null ]; then
            settings delete system "${entry#*:}"
        else
            settings put system "${entry#*:}" "$value"
        fi
    done
    rm -f "$backup" /data/adb/xgoogle_nav_off /data/adb/xgoogle_power_off /data/adb/xgoogle_granted
) > /dev/null 2>&1 &
# User enrollment/model databases are intentionally not erased during uninstall.
# Removing the module removes the XApps app too.
pm uninstall io.github.leekin2.xapps > /dev/null 2>&1
