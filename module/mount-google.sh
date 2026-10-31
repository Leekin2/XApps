#!/system/bin/sh
# Picks how the systemized Google app is mounted, before the metamodule mounts it (from Coogle 1.3.3).
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
