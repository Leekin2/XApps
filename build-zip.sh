#!/usr/bin/env bash
# Builds XApps end to end:
#   1. module/ -> xgoogle/src/main/assets/xapps-module.zip (the module without the app, which the
#      XGoogle screen installs or updates),
#   2. the release APK,
#   3. XApps-<version>.zip = module/ + the APK (KernelSU / Magisk / APatch).
set -e
cd "$(dirname "$0")"
version=$(grep '^version=' module/module.prop | cut -d= -f2)
python -I - pack module xgoogle/src/main/assets/xapps-module.zip <<'PY'
import os, sys, zipfile
with zipfile.ZipFile(sys.argv[3], "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(sys.argv[2]):
        for f in files:
            path = os.path.join(root, f)
            z.write(path, os.path.relpath(path, sys.argv[2]).replace(os.sep, "/"))
PY
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
./gradlew.bat -q assembleRelease
cp app/build/outputs/apk/release/app-release.apk "XApps-$version.apk"
python -I - "$version" <<'PY'
import shutil, sys, zipfile, os
v = sys.argv[1]
out = f"XApps-{v}.zip"
shutil.copy("xgoogle/src/main/assets/xapps-module.zip", out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(f"XApps-{v}.apk", "xapps.apk")
print(out, os.path.getsize(out), f"XApps-{v}.apk", os.path.getsize(f"XApps-{v}.apk"))
PY
