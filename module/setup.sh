#!/system/bin/sh
MODDIR=${0%/*}
# Needed by the original enrollment APKs on this inspected CN firmware.
# This per-package override leaves foreground-service enforcement for other apps intact.
for package in com.android.hotwordenrollment.xgoogle com.android.hotwordenrollment.okgoogle; do
    am compat disable 255038118 "$package" || exit 1
done
# BOOT_COMPLETED updates existing models but does not create the initial model.
# Initialize only the verified Chinese locale, and never replace an existing id 101.
state=$(dumpsys -t 10 voiceinteraction) || exit 1
if ! printf '%s\n' "$state" | grep -q 'keyphrase_id: 101'; then
    am start-foreground-service --user 0 \
        -n com.android.hotwordenrollment.xgoogle/.EnrollmentForegroundIntentService \
        -a com.android.intent.action.MANAGE_VOICE_KEYPHRASES \
        --ei com.android.intent.extra.VOICE_KEYPHRASE_ACTION 0 \
        --es com.android.intent.extra.VOICE_KEYPHRASE_HINT_TEXT 'X Google' \
        --es com.android.intent.extra.VOICE_KEYPHRASE_LOCALE zh-CN || exit 1
else
    echo 'Existing model 101 retained; no re-enrollment requested.'
fi
