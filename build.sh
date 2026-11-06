#!/bin/bash
# Build, sign and (optionally) install: ./build.sh [install]
set -e
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
JAR=$(ls -d "$SDK"/platforms/android-3*/android.jar | sort -V | tail -1)
rm -rf build/classes build/dex build/res.zip build/base.apk build/aligned.apk
mkdir -p build/classes build/dex
javac -nowarn -source 8 -target 8 -cp "$JAR" -d build/classes $(find src stubs -name '*.java') > build/javac.log 2>&1 || { grep -v "^warning" build/javac.log; echo "COMPILE FAILED"; exit 1; }
# stubs must not ship in the dex (Vector provides the real API)
rm -rf build/classes/de
"$BT/d8" --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml build/res.zip
(cd build/dex && zip -q ../base.apk classes.dex)
zip -q build/base.apk assets/xposed_init
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk
"$BT/apksigner" sign --ks build/debug.jks --ks-pass pass:android --out BatteryBuddy.apk build/aligned.apk
echo built BatteryBuddy.apk
[ "$1" = install ] && adb install -r BatteryBuddy.apk
