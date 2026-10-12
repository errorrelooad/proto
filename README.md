# Wave Home

Wave Home is an Android launcher prototype with a custom home screen, an in-app notification center, user-defined app groups, and its own live wallpaper studio. It does not connect to or require Wallpaper Engine.

## Features

- HOME launcher with installed-app discovery, search, Korean/English alphabet filters, and favorites
- Custom app groups: create, rename, and delete categories; assign each app to multiple groups
- Wallpaper Studio for images, GIFs, and videos selected from the phone
- Live wallpaper controls for dimming photos/GIFs and adding parallax to photos; videos play muted and loop
- Full in-app notification center with Frost, Midnight, and Warm Light themes
- View, open, dismiss, or clear notifications after the user grants notification access
- Quick settings tiles that open Android's supported Internet, Wi-Fi, volume, and display panels
- Optional system brightness slider after the user grants Modify system settings access
- GitHub Actions build for an installable debug APK

## Set up on Android

1. Download and install the `app-debug.apk` from a successful GitHub Actions run.
2. Set Wave Home as the device's default Home app.
3. Open **Settings → Wallpaper Studio**, select an image, GIF, or video, adjust the options, and tap **Apply wallpaper**. Confirm Wave Motion in Android's wallpaper preview.
4. Open the app's **Settings → Notification access** action to grant access if you want to see notifications in Wave Home.
5. On the home screen, use **＋ 만들기** to make categories. Tap the group icon on an app row to assign it to one or more categories. Long-press a category chip to rename or delete it.

Wallpaper media and category assignments stay on the device. Wallpaper dimming applies to photos and GIFs; parallax applies to photos. Videos use their source brightness and play without audio. GIF playback depends on the phone's file provider returning a decodable GIF stream.

## Notification center scope

The Frost, Midnight, and Warm Light themes apply to Wave Home's own notification center, opened by swiping down from the top of the launcher or tapping **제어**. Android does not let an ordinary third-party launcher restyle or replace the system-wide notification shade; that requires privileged system software or a custom Android build. Wave Home uses Android's supported Settings Panels for system controls.

Notification access and Modify system settings are optional. Android asks the user to grant each permission in its own settings screen; Wave Home cannot grant either permission silently.

## Build with GitHub Actions

The **Build Wave Home APK** workflow runs on pushes to `wave-home` and can be started manually from the Actions tab. Download the `wave-home-debug-apk` artifact from a successful run. The debug APK is for sideload testing and is not a Play Store release.

## Local build

Requirements: JDK 17, Gradle 8.13, Android SDK Platform 36, and Build Tools 36.0.0.

```sh
gradle --no-daemon assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
