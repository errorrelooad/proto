# Wave Home

Wave Home is an Android home-screen launcher. Choose it as the device's default Home app to use its app list whenever you press Home. It does not connect to Wallpaper Engine.

## Features

- Search installed apps and launch them directly from the home screen
- Make, rename, and delete your own app categories
- Add several apps to a category at once, and place one app in multiple categories
- Keep favorite and recently opened apps near the top
- Assign categories or toggle favorites directly from an app row; long-press for more actions
- Set a photo, GIF, or video from your phone as the live wallpaper; adjust photo/GIF dimming and photo parallax

## First use

1. Install the debug APK and open Wave Home. On first launch, select it in Android's system prompt to make it the default Home app. If you dismiss the prompt, tap **기본 홈으로 설정**.
2. Tap **배경** to open Wallpaper Studio, choose media, and tap **배경화면 적용**. Confirm Wave Motion in Android's wallpaper preview.
3. Tap **＋** beside the category chips to create a category. Use **관리** to rename or delete categories, and **＋ 앱 추가** to assign multiple apps at once. The **⊞** control on an app row assigns that app to any number of categories.
4. Tap an app to launch it. Tap **☆** to favorite it.

Wave Home leaves Android's notification shade and system settings to Android. Wallpaper files, favorites, recent apps, and categories are stored on the phone.

## Build

The **Build Wave Home APK** GitHub Actions workflow builds a debug APK when changes are pushed to the `wave-home` branch. The output is intended for sideload testing, not a Play Store release.
