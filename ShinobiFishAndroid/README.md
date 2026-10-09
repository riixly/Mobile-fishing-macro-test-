# Shinobi Fish — experimental Android macro

An Android 10+ (API 29+) Roblox fishing-timing prototype. **This is source code, not a precompiled APK.** It has **not** yet been tested on a physical Android phone or against Roblox. Building and testing are still necessary.

## What it does

- Uses the Android MediaProjection screen-capture API (requires consent for every new capture session).
- Uses the Android Accessibility gesture API to tap HOOK when a tall red timing needle enters the green area.
- Provides a floating **FISH** button near the upper left that can be minimized to avoid blocking the game.
- Has 3 in-game calibration taps: green timing zone, HOOK button, and water/cast position.
- Has an **experimental** optional auto-cast loop: taps a calibrated water location after the bar disappears, at a safe interval. Keep the fishing rod equipped.
- Requires **no root**, login, network access, or PC connection once installed. Roblox must remain visible for reliable pixel detection and Android accessibility taps.

## Build an APK without installing Android tools locally

1. Make a **new GitHub repository** (not your existing wiki repository).
2. Upload the **contents of this folder** to the repo root (`app/`, `.github/`, `settings.gradle`, etc.). If GitHub's web upload hides `.github`, make sure the folder is included; Actions won't exist otherwise.
3. Open the repo **Actions** tab, select **Build Android APK**, then **Run workflow**. A push to the repo also triggers a build.
4. When the build succeeds, download the workflow artifact **ShinobiFish-debug-apk**. Extract the ZIP and you'll find `app-debug.apk`.
5. Install that APK on your Android phone (allow installation from this source if Android asks).

Or open this folder in Android Studio with JDK 17, Gradle 8.9, and Android SDK 35 installed, and build `app:assembleDebug`.

## Use it

1. Open the app and choose **ENABLE TOUCH AUTOMATION**. In Accessibility settings, enable **Shinobi Fish**. If Android blocks the switch as a restricted setting for a sideloaded app, open Android Settings → Apps → Shinobi Fish → the upper-right menu → **Allow restricted settings** (if your device offers it), then return to Accessibility.
2. Back in the app, tap **ALLOW SCREEN CAPTURE** and grant **entire screen** recording permission. (Android 14+ may ask if you want a single app vs. the full display; choose full display.)
3. Switch to Roblox in **landscape** and equip the fishing rod.
4. Open the floating **FISH** controls. During a fishing timing round, tap **Set GREEN zone** then tap the middle of the bright-green target area. (This must be on the strip, not on the HOOK button.)
5. Tap **Set HOOK button** then tap the center of the HOOK button. Tap **Set WATER cast** and then tap a water spot for casting if using auto-cast.
6. Press **START**. The controls minimize automatically. Tap FISH again to **STOP** or recalibrate.
7. To turn off screen recording itself, use the notification's **Stop capture** action or the app's **STOP SCREEN CAPTURE** button.

**Important:** Permissions are sensitive. Enable Accessibility only for apps you trust. This app never sends your screen or any data over the network (it has no Internet permission). Android may terminate screen capture on lock, rotation, battery restrictions, or when you revoke permission; reauthorize and recalibrate as needed. The overlay is fixed and may sit over the game's UI. Some games or Android versions may ignore simulated touches.

## How detection works

- Reads a single row of the current screen at your calibrated green-zone position.
- Detects contiguous bright green pixels and a tall red vertical line.
- Taps HOOK on a red-outside -> red-inside transition with a ~950 ms debounce, avoiding rapid repeats.
- After the timing strip disappears, optional auto-cast sends periodic water taps.
- If the game changes the mini-game colors, layout, bar speed or position, the detector may need changes.

## Testing

The Java-only detector can be tested without the Android SDK, against a screenshot supplied as an argument:

```bash
javac -d tests/build app/src/main/java/fun/riix/shinobifish/FishingDetector.java tests/DetectorSelfTest.java
java -Djava.awt.headless=true -cp tests/build fun.riix.shinobifish.DetectorSelfTest /path/to/reference-screenshot.png
```

The included test expects the 2048x912 reference screenshot with the target centered near `(570,816)`. It verifies the original red needle is outside the target and then simulates moving it inside. **This test does not prove Android input or Roblox integration works.**
