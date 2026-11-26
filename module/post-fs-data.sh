#!/system/bin/sh
MODDIR=${0%/*}
# Every boot: NoMount may have been installed, enabled or removed since the companion was installed.
. "$MODDIR/mount-google.sh"
apply_google_mount_choice "$MODDIR" || exit 1
if [ -d "$MODDIR/system/priv-app/GoogleQuickSearchBox" ]; then
    prepare_google_mount "$MODDIR"
fi
