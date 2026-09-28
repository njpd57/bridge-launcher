# Bridge Launcher (fork)

This is a fork of [Bridge Launcher](https://github.com/bridgelauncher/launcher) that adds JS API capabilities the original doesn't have, for the [Gingerbread Launcher](https://github.com/njpd57/android-gingerbread-launcher) web project. Projects written for the original Bridge keep working; the additions are only extra methods and events:

- Real notifications (list, icons, open, dismiss) and media controls (what's playing, album art, play/pause/next/previous).
- Quick settings: flashlight, brightness, auto-rotate, sync, the state of Wi-Fi / Bluetooth / location, and Android's panels for what apps can't toggle.
- Real connectivity: network in use, Wi-Fi and mobile signal levels, data activity.
- Calendar events, app shortcuts, default apps (dialer, browser…), opening URLs, `<input type="file">`, locking the home screen to portrait.
- Fixes: intermittent `ERR_NAME_NOT_RESOLVED` on startup, and apps now open in their own task.

Build with `./gradlew assembleDebug` (JDK 17 and the Android SDK; `scripts/setup-dev-env.sh` sets them up on Ubuntu) and install with `adb install`. It's signed differently from the released Bridge, so uninstall that first. `scripts/grant-permissions.sh` grants the adb-only permission for night mode. The full list of added methods is in [CLAUDE.md](CLAUDE.md).

---

# Bridge Launcher

An Android launcher that is a middleman between your HTML, JS and CSS and the Android system.  

## Getting started

Download and install from the [releases tab](https://github.com/bridgelauncher/launcher/releases).  
Read more about the Bridge system from the [project home](https://github.com/bridgelauncher).

## Links

- [Bridge Launcher project home](https://github.com/bridgelauncher)
- [API types](https://github.com/bridgelauncher/api)
- [API mock for development](https://github.com/bridgelauncher/api)
- [Discord server](https://discord.gg/Tv23aZrVb8) - I blogpost as I go
- [theothertored@gmail.com](mailto:theothertored@gmail.com) - Contact email
