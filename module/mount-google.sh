#!/system/bin/sh
# Picks how the systemized Google app is mounted, before the metamodule mounts it.
# NoMount turns .replace into a whiteout and drops every file below it, so Google would vanish from
# /system/priv-app: merge while NoMount is the active metamodule, replace otherwise.
prepare_google_mount() {
    google_dir="$1/system/priv-app/GoogleQuickSearchBox"
    [ -d "$google_dir" ] || return 1
    backend=$(readlink -f /data/adb/metamodule 2>/dev/null)
    # NoMount may load its driver later (metamount.sh), so check the module, not the driver.
    if [ -f "$backend/module.prop" ] && grep -q '^id=nomount$' "$backend/module.prop" &&
       [ ! -f "$backend/disable" ] && [ ! -f "$backend/remove" ] &&
       [ ! -f /data/adb/nomount/disable ]; then
        rm -f "$google_dir/.replace"
    else
        : > "$google_dir/.replace"
    fi
}

# Stage only XApps' own Google APK copy outside the module's mount tree when switched off.
# Same-filesystem rename keeps the APK and split files together across interrupted boots.
apply_google_mount_choice() {
    mount_root="$1"
    mount_live="$mount_root/system/priv-app/GoogleQuickSearchBox"
    mount_parked="$mount_root/google-app-off"
    if [ -e /data/adb/xapps_google_mount_off ]; then
        if [ -e "$mount_live" ] && [ ! -e "$mount_parked" ]; then
            mv "$mount_live" "$mount_parked" || return 1
        fi
    elif [ -e "$mount_parked" ] && [ ! -e "$mount_live" ]; then
        mkdir -p "${mount_live%/*}" && mv "$mount_parked" "$mount_live" || return 1
    fi
}
