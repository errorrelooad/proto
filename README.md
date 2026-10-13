# Wave Home

Wave Home is a standalone Android home launcher with an original interface and its own wallpaper studio. It does not connect to Wallpaper Engine.

## Launcher features

- Single-column app list, live clock, app search and a touchable alphabet index
- Search apps, Android app shortcuts, the web and (when tapped) contacts
- Favorites, recent apps and user-created app groups; an app can belong to several groups
- Long-press an app for its Android shortcuts, favorites, group assignment, icon override and app info
- Open a group as a compact pop-up, and assign an app or group to the bottom action button; swipe up to use its second action
- Five built-in palettes; import or export a palette file for sharing
- System-font choices, 12/24-hour clock, clock size, dot icons and optional status-bar hiding
- Local per-app icon overrides, stored on the device
- Android app-widget host with an ordered, removable widget stack
- Optional calendar agenda access and a weather tile for a city entered by the user
- Optional usage-time reminder, daily per app; requires Android Usage Access
- Optional double-tap clock or alphabet-index screen lock; requires enabling Wave Home as a device administrator
- Music-app suggestion while a wired headset is connected
- Photo, GIF or video live wallpapers from the phone, with photo/GIF dimming and photo parallax

Wave Home leaves Android's notification shade and Quick Settings panel under Android control. Calendar, Usage Access and Device Admin permissions are requested only when the related feature is enabled. Weather looks up the city selected by the user and does not request location permission.

## First use

1. Install and open the APK, then select Wave Home as the default Home app when Android asks.
2. Tap an app to open it. Long-press an app to see shortcuts and organization actions.
3. Use **앱 모음** to create custom groups and assign apps. The right-edge index jumps through the app list.
4. Use **위젯** to add Android widgets to a stack. Use **배경** to open Wallpaper Studio and apply a selected photo, GIF or video as the live wallpaper.
5. Open **···** for theme, type, calendar and usage features.

## Build

The **Build Wave Home APK** GitHub Actions workflow builds a debug APK on pushes to `wave-home`. The artifact is for direct Android installation and testing, not a Play Store release.

## Build

The **Build Wave Home APK** GitHub Actions workflow builds a debug APK when changes are pushed to the `wave-home` branch. The output is intended for sideload testing, not a Play Store release.
