# Lamp (upgraded-lamp)

A home screen replacement for Xiaomi / HyperOS tablets, in one APK:

- **Home screen**: one page of small icons, Windows-desktop style, plus a small framed dock with 5 slots at the bottom.
- **Wallpaper**: system or live wallpaper, your own picture, or a looping **video with sound**.
- **Control panel**: pull down from the top in any app to open Lamp's panel: Wi-Fi, Bluetooth, flashlight, rotation, Do Not Disturb, fake GPS, screenshot, lock, power menu, brightness, volume, media controls and notifications.
- **Lock screen**: shown whenever the screen turns off. Clock, notifications, swipe up to unlock, optional PIN.
- **Fake GPS**: pick a spot on an OpenStreetMap map. It's pushed to the GPS, network and fused providers every second, 24/7, and comes back after a reboot.

No root needed. No Google services needed.

## Install

Every push builds a signed APK with GitHub Actions and publishes it on the
[**Releases** page](../../releases) (not as a workflow artifact). Open the newest release on the
tablet, download `LampOS-x.y.z.apk` and install it. Newer releases install over older ones.
Only the newest 5 releases are kept.

## Setup on the tablet

Open **Lamp Settings**. The *Setup* list has a button for each step:

1. Make Lamp the home screen.
2. Turn on the **Lamp accessibility service**. This runs the control panel and lock screen.
3. Allow **notification access** (notifications, media, Do Not Disturb in the panel).
4. Allow **display over other apps** and **modify system settings** (brightness and rotation).
5. Battery → **No restrictions**. On Xiaomi, also turn on **Autostart** and lock Lamp in Recents.
6. Developer options → **Select mock location app** → Lamp.
7. Security app → **Game Turbo** → add Lamp and every app you use as a game. This keeps HyperOS's own control panel out of the way.
8. Set the system **Screen lock** to *None*. Lamp's lock screen replaces it.

## How to use

- **Tap** an icon to open it. **Long-press and drag** to move it anywhere on the grid. **Long-press** without moving for options (add to dock, hide, app info, uninstall).
- **Empty dock slot**: tap to choose an app. Long-press a dock icon to change or remove it.
- **Long-press empty desktop** for wallpaper and settings, fake GPS, or *Tidy up icons*. **Double-tap** empty desktop to lock.
- In the panel, long-press the **Fake GPS** tile to open the map.

## Limits

- Android doesn't let normal apps switch Wi-Fi, Bluetooth, mobile data or airplane mode directly. Those tiles open the system panel for that setting.
- Game Turbo apps have to be added by hand. There's no API for it.
- The lock screen is a convenience lock, not a security boundary.
- If more apps are installed than fit on one page, the rest are skipped. Make icons smaller in Lamp Settings → Desktop.

## Building

GitHub Actions only (`.github/workflows/build.yml`): JDK 17, `./gradlew assembleRelease`, then `gh release create`.

**Signing:** updates only install over each other if every APK is signed with the same key. The workflow
creates a key on its first run and keeps it in the GitHub Actions cache. That cache can be cleared
(it's dropped after 7 days without a build, and each branch has its own). If that happens the next APK
won't install as an update: uninstall Lamp once and install the new one. To make the key permanent,
add two repository secrets (Settings → Secrets and variables → Actions): `SIGNING_KEY_B64` (a base64
keystore with alias `lamp`) and `SIGNING_PASSWORD`. The workflow uses those whenever they exist.
