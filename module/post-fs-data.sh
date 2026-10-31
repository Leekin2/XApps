#!/system/bin/sh
MODDIR=${0%/*}
# Every boot: NoMount may have been installed, enabled or removed since the companion was installed.
. "$MODDIR/mount-google.sh"
prepare_google_mount "$MODDIR"
