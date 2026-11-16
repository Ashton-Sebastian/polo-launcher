# Polo Launcher: a stock-style VW home screen for the Desay RCD360 Android unit

Built for a white 2011 VW Polo 6R. It copies the look of the 2026 Polo Vivo factory unit: a blue
gradient background, a side bar with buttons you choose, an app grid, and a Vehicle screen with
**Since start / Long-term** trip data. It adds a **fuel level gauge** and a fuel chip that stays in
the status bar on every screen. The trip computer has three tabs like the real VW: **Since start,
Since refuelling and Long-term**. The car on the Vehicle screen is an AI-generated studio render of a
white 2011 Polo 6R (made with Figma's image generator, original in `car-tools/`), cut out and given a
soft floor shadow so it sits in the design.

## What's in here

| Path | What it is |
|---|---|
| Releases page | The launcher APK (`PoloLauncher.apk`) and update files |
| `dist/1280x720/`, `dist/1024x600/` | The VW start-up logo as `.png`, `.bmp` and `.jpg`, plus `bootanimation.zip` |
| `android/` | Android Studio / Gradle project (Java plus a WebView UI) |
| `android/app/src/main/assets/www/` | The UI itself (HTML/CSS/JS). Edit these files to change the look. |
| `android/app/src/main/assets/vehicle_sources.json` | Where the launcher reads fuel and trip data from (see below) |
| `tools/publish.sh` | Builds a release into `dist/update/` (app + screens + `update.json`) |
| `tools/release.sh` | Builds and publishes an update to GitHub Releases (the unit installs it from there) |
| `tools/serve.sh` | Alternative: share `dist/update/` from this Mac over Wi-Fi |
| `boot/make_boot.py` | Regenerates the logo files: `python3 boot/make_boot.py 1280 720` |
| `screenshots/` | How every screen looks (taken on an Android emulator) |

## 1. Install the launcher

1. Copy `PoloLauncher.apk` to a USB stick, plug it into the unit, and open it with the unit's file
   manager. Allow "install unknown apps" if the unit asks.
2. Open Polo Launcher and tap the **"Make Polo Launcher your default home screen"** banner (or
   Launcher → Default home screen → **Make default**). Android asks you to confirm: pick Polo
   Launcher and tap **Set as default**. On units without that dialog, the button opens the Home
   app settings instead. Some head units lock their own launcher; if Home still opens the stock
   screen, look for a "Default launcher" option in the unit's factory settings.
3. **Apple CarPlay** and **Android Auto** open ZLink automatically. The first time you tap any other tile, such as Radio, choose which of the unit's
   apps it should open. Long-press a tile later to change it.
4. Swipe left for page 2: Navigation, Settings, Pictures, Files, Sound, Browser, All apps and
   **Launcher** (settings).
5. **Side bar:** long-press any side-bar button (or Launcher → Side bar buttons) to choose what it
   does: Vehicle, Phone, CarPlay, Android Auto, Navigation, Bluetooth Music, Radio, USB, Sound,
   Settings, All apps, Back, Recent apps, Volume +/–, Mute, or Empty. Default: Phone, Navigation,
   CarPlay, Vehicle.

### Home button opens the old screen?

Many head units send their Home button straight to their own launcher and ignore the default-home
setting. Switch on **Launcher → Home button fix → Turn on**, then find **Polo Launcher Home button**
in the Accessibility list and switch it on. (If the unit hides Accessibility settings, run this once
over USB/ADB: `adb shell settings put secure enabled_accessibility_services com.polo6r.launcher/com.polo6r.launcher.HomeRedirectService`
and `adb shell settings put secure accessibility_enabled 1`.)

- If the Home button sends a normal Android Home key, the fix catches it before the old launcher
  starts, so you only ever see Polo Launcher.
- If the unit's firmware launches its own home screen directly, the fix swaps it for Polo Launcher
  straight away (on the emulator, about 0.06 s), so a very brief flash is possible. If you see the
  flash, the only way to remove it completely is to switch the old launcher off over ADB
  (`adb shell pm disable-user --user 0 <old.launcher.package>`; undo with `pm enable`). Check first
  that nothing you use lives inside that app. Launcher → Home button fix shows its package name.

To remove it, set the unit's original home app back as the default and uninstall Polo Launcher.

## 2. Fuel level and trip data

Every head unit passes CAN-bus data (fuel, range, consumption) to Android in its own private way.
**Until it's connected, the Vehicle screen shows simulated values under an orange "Demo data"
label.**

To find out how your unit does it: **Launcher → Find car data**. With the ignition on (engine
running is best), tap **Start scan**, leave the screen open for about 2 minutes (drive if you can),
then tap **Save report**. Send the `polo-car-report-….txt` file from the unit's **Download** folder,
or photos of the screen. The report lists the unit's car apps and their data services, and logs
the live car messages it hears, which is what's needed to show your real fuel and trip numbers.

## Updates without reinstalling

From version 1.2 the launcher updates itself from this repo's **GitHub Releases** (it checks at
start-up and every 6 hours whenever the unit has internet, e.g. your phone's hotspot):

- **Screen changes** (design, layout, car image, most fixes) download and apply automatically, with
  nothing to install.
- **App changes** show an "Update available" banner. Tap it and confirm Android's install prompt.
  The first time, Android asks to allow Polo Launcher to install apps (**Settings → Allow**). If
  Google Play Protect asks, choose **More details → Install without scanning**.
- **Launcher → Updates → Check now** checks straight away.

To publish an update: `tools/release.sh "what changed"`. For a screen-only change, bump
`android/app/src/main/assets/www/version.txt` first; for an app change, also bump `versionCode` /
`versionName` in `android/app/build.gradle`.

The app is signed with this Mac's Android debug key (`~/.android/debug.keystore`, copied to
`signing/`, which is never committed). Updates only install over an app signed with the same key,
so keep that file safe.

## 3. VW logo at power-on

There are two separate logos at start-up:

1. **The first logo** (the one you see now, set by the bootloader). Desay and most Chinese Android
   units let you change it without root: Settings → Factory settings (or Car settings) → **Boot logo
   / Car logo**. Pick VW from the list, or choose "custom / from USB" and select
   `logo_1280x720.bmp` (or the `.png`/`.jpg` if it won't take the BMP). The factory menu usually
   asks for a code; common ones are 8888, 1234, 1617, 3368, 0000 and 126.
   Don't flash a logo partition with third-party tools. If it goes wrong, the unit won't start.
2. **The Android boot animation** (shown while Android loads). `bootanimation.zip` shows the VW logo
   fading in with a soft blue glow. Installing it needs root:
   `adb root && adb remount && adb push bootanimation.zip /system/media/` (some units use `/oem/media/`).
   If the unit isn't rooted, skip this step. The launcher shows the same VW logo as soon as it starts,
   so the hand-off still looks seamless.

## Building from source

```bash
cd android
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug   # or: tools/publish.sh
```

Preview the UI in a browser: `python3 -m http.server 8765 --directory android/app/src/main/assets/www`,
then open `http://localhost:8765/?screen=vehicle`.

The car image is `assets/www/car.png`; the scripts that made it are in `car-tools/`.
