# BatteryBuddy

A port of [BatteryBuddy](https://github.com/kissalexandra/BatteryBuddy) — the cute battery-indicator jailbreak tweak for iOS — to Android, specifically ColorOS's SystemUI. An Xposed-style module for [Vector](https://github.com/JingMatrix/Vector) / LSPosed.

- Replaces the stock battery icon with a rounded body, a face, and a terminal nub — happy, neutral, sad, or a dead "X eyes" look below a configurable low-battery threshold. Always happy while charging.
- Optionally replaces the charging bolt next to it with a plug/cord glyph.
- Four color themes — **System** (follows ColorOS's own theme), **Light**, **Dark**, or **Custom** (your own hex colors) — for the battery and the charging icon independently.
- Outline thickness and the three mood-band thresholds (happy / neutral / sad) are all adjustable from the companion app, with a live preview that reflects your actual battery state before you save.

## Requirements

- Rooted device, ColorOS SystemUI (built against `HorizontalBatteryContentDrawable`, ColorOS's own battery icon drawable class — other OEM skins or AOSP SystemUI will not match and the module will simply decline to hook, leaving the stock icon alone)
- Vector (or another LSPosed-compatible framework) with module scope for **System UI**
- Android 8.1 or newer

## Install

1. Install the APK.
2. In Vector, enable **BatteryBuddy** with scope set to **System UI** only.
3. Restart System UI (or reboot) so the hook loads.
4. Open the BatteryBuddy app to set the theme, charging icon, thresholds, and outline thickness. Save, then restart System UI (the app can do this for you, given root) to see changes immediately — or just wait a few seconds for the next natural redraw.

## How it works

The battery icon is a single custom `Drawable` ColorOS assigns to an `ImageView` in SystemUI. The hook intercepts that drawable's `draw(Canvas)` call and paints the entire icon itself — outline, fill, nub, and face — instead of letting ColorOS draw its own. The charging bolt lives in a separate `ImageView`; the hook intercepts its `setImageDrawable` call and, depending on the selected style, either swaps in a custom plug/cord `Drawable` or tints ColorOS's own bolt directly via `setColorFilter`.

The companion app and the hook don't share a process, so settings cross between them through a `ContentProvider` rather than a shared-preferences file: this device's SELinux policy blocks a cross-app raw file read even with otherwise-open permissions, so the classic Xposed `XSharedPreferences` approach doesn't work here — the provider approach sidesteps that by keeping the raw read inside the owning process and only passing the already-read values over Binder.

## Build

Needs a JDK and the Android SDK (build-tools and a platform). No Gradle.

```sh
./build.sh            # produces BatteryBuddy.apk
./build.sh install    # also installs it with adb
```

The APK is signed with a throwaway debug key at `build/debug.jks`, generated on first build if it doesn't already exist.

## Layout

```
src/io/github/edualexxis/batterybuddy/
  Hook.java               entry point; hooks the battery drawable and charging-icon view
  BatteryPaint.java        the actual icon drawing code (no Xposed dependency — shared with the preview)
  PrefsProvider.java       ContentProvider bridging the companion app's settings to the hook
  SettingsActivity.java    companion app
  BatteryPreviewView.java  the live preview shown in the companion app
  MoodRangeSlider.java     the banded range slider for the mood thresholds
stubs/                     compile-time stubs of the Xposed API (not shipped)
```

## Credits

Ported from [kissalexandra/BatteryBuddy](https://github.com/kissalexandra/BatteryBuddy) (archived), the original iOS jailbreak tweak. The battery body, face, and the charging plug/cord glyph are all redrawn here to match that design — including the plug traced directly from its `StatusBarCharger.png` asset — but every line of Java/Android code in this repo was written independently for this port.

## License

GPL-3.0, same as the original project this is a port of. See [LICENSE](LICENSE).
