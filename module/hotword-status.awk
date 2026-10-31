# Keep initialization, destruction and detector type within the same session.
function flush() {
    if (google && initialized && !destroyed) {
        if (type == "trusted_hotword_dsp") dsp = 1
        if (type == "trusted_hotword_software") software = 1
    }
    initialized = 0
    destroyed = 1
    type = ""
}
/VOICE INTERACTION/ { available = 1 }
/mComponent=/ {
    flush()
    google = ($0 ~ /mComponent=com\.google\.android\.googlequicksearchbox\//)
}
/mCallback=/ { flush() }
/Active session:/ { flush(); google = 0 }
/mInitialized=/ { initialized = ($0 ~ /mInitialized=true/) }
/mDestroyed=/ { destroyed = !($0 ~ /mDestroyed=false/) }
/DetectorType=/ { type = $0; sub(/^.*DetectorType=/, "", type); sub(/[[:space:]]+$/, "", type) }
END {
    flush()
    print (!available ? "unknown" : dsp ? "dsp" : software ? "software" : "inactive")
}
