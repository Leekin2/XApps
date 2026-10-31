#!/system/bin/sh
MODDIR=${0%/*}
# The XApps app, when installing it while flashing was not possible (e.g. flashed from recovery).
if [ -f "$MODDIR/xapps.apk" ]; then
    (
        until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
        sleep 10
        . "$MODDIR/common.sh"
        xapps_setup "$MODDIR/xapps.apk" > "$MODDIR/setup.log" 2>&1 && rm -f "$MODDIR/xapps.apk"
    ) &
fi
# KernelSU and APatch run boot-completed.sh themselves; Magisk only runs service.sh.
if [ "$KSU" = true ] || [ "$APATCH" = true ]; then
    exit 0
fi
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
exec /system/bin/sh "$MODDIR/boot-completed.sh"
