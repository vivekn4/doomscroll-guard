# Android setup & build

## Build the APK

Requires: JDK 17, Android SDK (API 34), Android Gradle Plugin 8.5.2.

```bash
cd android
./gradlew assembleRelease   # add a gradle wrapper (gradle wrapper --gradle-version 8.7) or use your own
# APK lands in app/build/outputs/apk/release/app-release.apk
```

Sign it with your own key (`apksigner`) before distributing. The repo ships no
keystore and no signing config — never commit one.

## Install

1. Copy the APK to the phone, tap it, allow "Install unknown apps" for your
   file manager/browser. (Or grab it from GitHub Releases / F-Droid.)
2. Open **Doomscroll Guard** → the 3-step wizard starts automatically:
   - **Step 1 — How it works:** read the disclosure, tap *I understand*.
     (This is the Play-policy consent; the app does nothing until you agree.)
   - **Step 2 — Enable the Guard:** tap *Open Accessibility Settings*, find
     **Doomscroll Guard** in the list, turn it on. Plain-language explanation
     of *why* is on that screen.
   - **Step 3 — Done:** the master switch turns on. Try the Shorts tab.
3. Add the widget: long-press home screen → **Widgets → Doomscroll Guard**.
   One tap toggles the Guard; tapping the status line opens the app.
4. Optional: add the **Quick Settings tile** — swipe down twice, tap the pencil,
   drag "Doomscroll Guard" into your tiles.

## Re-verifying detection (when YouTube/Instagram update their UI)

View IDs are undocumented internals and rot. When blocking stops working:

```bash
adb shell uiautomator dump /sdcard/view.xml
adb pull /sdcard/view.xml
# open a Shorts/Reels player, dump again, diff the resource-id attributes
```

Update `shared/rules/rules.json`, run `python3 tools/validate_rules.py`, bump
`bundle_version`, and ship the bundle as a GitHub Release asset — the app picks
it up without an app update. Copy the new file to
`android/app/src/main/assets/rules.json` for the next APK build.

## OEM / OS quirks

- **Xiaomi / Oppo / Vivo:** enable *Autostart* for Doomscroll Guard and turn off
  battery optimization, or the system may kill the service.
- **Android 17 Advanced Protection Mode:** non-accessibility-aid services lose API
  access when APM is on. The app detects this and explains it on the main screen.
- **Overlay permission:** only requested if you enable Shield mode; without it the
  app uses the silent back-action (no overlay).

## Measuring battery impact

```bash
adb shell dumpsys batterystats --reset
# use the phone normally for a few hours with the Guard on
adb bugreport bugreport.zip
# open in Battery Historian, compare with Guard off
```

The service is package-filtered (the OS drops other apps' events before they reach
us) and throttled (`notificationTimeout` 100ms + 600ms debounce). If you measure a
real delta, file an issue with the historian output.
