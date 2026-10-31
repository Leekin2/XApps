#!/system/bin/sh
MODDIR=${0%/*}
# BusyBox ships in a different place per root manager (KernelSU, Magisk, APatch).
for BB in /data/adb/ksu/bin/busybox /data/adb/magisk/busybox /data/adb/ap/bin/busybox; do
    [ -x "$BB" ] && break
done
SOURCE=/odm/etc/resourcemanager.xml
# The installed copy of this module, else the xgoogle_companion module it replaced.
for OLD in /data/adb/modules/xapps /data/adb/modules/xgoogle_companion; do
    [ -r "$OLD/audio-original.xml" ] && break
done
TARGET="$MODDIR/system/odm/etc/resourcemanager.xml"
skip_audio() {
    rm -f "$TARGET.tmp" "$TARGET"
    rmdir "$MODDIR/system/odm/etc" "$MODDIR/system/odm" 2>/dev/null
    echo "$1" > "$MODDIR/audio-state"
    exit 0
}
mkdir -p "$MODDIR/system/odm/etc" || skip_audio io-failed
# Reuse the original snapshot only when the mounted file matches our previous output.
if [ -r "$OLD/audio-original.xml" ] && [ -r "$OLD/audio-patched.sha256" ]; then
    current=$(sha256sum "$SOURCE"); current=${current%% *}
    previous=$(cat "$OLD/audio-patched.sha256")
    [ "$current" != "$previous" ] || SOURCE="$OLD/audio-original.xml"
fi
if [ "$SOURCE" = /odm/etc/resourcemanager.xml ] && grep -q ' /odm/etc/resourcemanager.xml ' /proc/self/mountinfo; then
    skip_audio inherited-overlay
fi
if [ ! -r "$SOURCE" ]; then
    skip_audio missing
fi
"$BB" awk -f "$MODDIR/patch-audio.awk" "$SOURCE" > "$TARGET.tmp"
result=$?
if [ "$result" != 0 ] || [ ! -s "$TARGET.tmp" ]; then
    skip_audio unsupported
fi
cp "$SOURCE" "$MODDIR/audio-original.xml" || skip_audio io-failed
mv "$TARGET.tmp" "$TARGET" || skip_audio io-failed
chmod -R u=rwX,go=rX "$MODDIR/system" || skip_audio io-failed
for relative in odm odm/etc odm/etc/resourcemanager.xml; do
    "$BB" chcon --reference="/$relative" "$MODDIR/system/$relative" || {
        skip_audio label-failed
    }
done
digest=$(sha256sum "$TARGET") || skip_audio io-failed
echo "${digest%% *}" > "$MODDIR/audio-patched.sha256" || skip_audio io-failed
echo 'prepared' > "$MODDIR/audio-state"
