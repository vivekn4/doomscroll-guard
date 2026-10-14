#!/usr/bin/env bash
# Manual Android build for doomscroll-guard (Gradle daemon cannot run in
# this sandbox: client<->daemon localhost comm fails). Mirrors what
# CI does with `gradle :app:assembleDebug`:
#   aapt2 compile+link -> kotlinc -> d8 -> zipalign -> apksigner
#
# First-run prerequisites (download once into teardown/manual/):
#   SDK: cmdline-tools -> platform-34 + build-tools 34.0.0 into ~/android-sdk
#     (sdkmanager needs proxy flags in this sandbox; or fetch the zips
#      with curl from dl.google.com/android/repository/ and unzip by hand)
#   kotlinc: https://github.com/JetBrains/kotlin/releases/download/v1.9.24/kotlin-compiler-1.9.24.zip
#     -> unzip as teardown/manual/kotlinc/
#   deps/ (from https://maven.google.com): androidx core 1.13.1 AAR
#     (extract classes.jar to deps/xj/), versionedparcelable 1.1.1 AAR
#     (classes.jar to deps/xj/vp/), tracing 1.0.0 AAR (classes.jar to
#     deps/xj/tr/), annotation-jvm 1.9.1 jar, lifecycle-common 2.8.4 jar.
#     Note: plain `annotation` jars are Kotlin-multiplatform metadata only;
#     the JVM classes live in the `annotation-jvm` artifact.
set -euo pipefail

REPO=~/workspace/repos/doomscroll-guard
MANUAL=~/workspace/repos/doomscroll-guard/teardown/manual
SDK=~/android-sdk
BT=$SDK/build-tools/34.0.0
OUT=$MANUAL/out
SRC=$REPO/android/app/src/main
KOTLINC=$MANUAL/kotlinc/bin/kotlinc

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

CP="$SDK/platforms/android-34/android.jar:$MANUAL/deps/xj/classes.jar:$MANUAL/deps/annotation-jvm-1.9.1.jar:$MANUAL/deps/xj/vp/classes.jar:$MANUAL/deps/xj/tr/classes.jar:$MANUAL/deps/lifecycle-common-2.8.4.jar"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/compiled_res.zip"

echo "== manifest (inject package, as AGP does from namespace) =="
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.vn.doomscrollguard">|' \
  "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

echo "== aapt2 link =="
"$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$SDK/platforms/android-34/android.jar" \
  --manifest "$OUT/AndroidManifest.xml" \
  -R "$OUT/compiled_res.zip" \
  --auto-add-overlay \
  --java "$OUT/gen" \
  -A "$SRC/assets" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0.0

echo "== kotlinc =="
R_JAVA=$(find "$OUT/gen" -name "R.java")
# shellcheck disable=SC2086
"$KOTLINC" -jvm-target 17 -nowarn -cp "$CP" -d "$OUT/classes" \
  $(find "$SRC/java" -name "*.kt") "$R_JAVA"

echo "== d8 =="
"$BT/d8" --lib "$SDK/platforms/android-34/android.jar" \
  --output "$OUT/dex" \
  $(find "$OUT/classes" -name "*.class") \
  "$MANUAL/deps/xj/classes.jar" \
  "$MANUAL/deps/xj/vp/classes.jar" \
  "$MANUAL/deps/xj/tr/classes.jar" \
  "$MANUAL/kotlinc/lib/kotlin-stdlib.jar"

echo "== package dex =="
cd "$OUT/dex" && zip -q -r "$OUT/base.apk" classes*.dex && cd "$MANUAL"

echo "== zipalign =="
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/app-debug-aligned.apk"

echo "== keystore (fresh debug key, local only) =="
if [ ! -f "$OUT/debug.keystore" ]; then
  keytool -genkeypair -v -keystore "$OUT/debug.keystore" -storepass android \
    -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" > /dev/null 2>&1
fi

echo "== apksigner =="
"$BT/apksigner" sign --ks "$OUT/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --out "$OUT/app-debug.apk" "$OUT/app-debug-aligned.apk"

echo "== verify =="
"$BT/apksigner" verify --verbose "$OUT/app-debug.apk"
"$BT/aapt2" dump badging "$OUT/app-debug.apk" | head -12
ls -la "$OUT/app-debug.apk"
