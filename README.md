# Wave Home

Wave Home is an Android home launcher with a clean, single-page app list and its own wallpaper studio. It does not connect to Wallpaper Engine.

## Home screen

- Prominent clock and date, optional weather and calendar glance
- Vertical favorites followed by an alphabetized app list
- Right-edge alphabet index that jumps on tap and follows a drag
- Search that opens on demand, keeping the home screen uncluttered
- Tap an app to launch; swipe right or long-press for its app pop-up and Android shortcuts
- User-created app groups, selected from the app-list heading; long-press a group selector to open its pop-up
- Remove favorites with the star and reorder them from an app pop-up; an app can belong to multiple groups

## Niagara Pro-inspired features

- Five built-in color themes; import or export a theme file
- Fonts, clock size and format, app icon styles, individual icon overrides, and optional status-bar hiding
- Android app-widget stack
- Calendar agenda and city weather, both optional
- Gentle per-app usage reminders
- Double-tap clock or alphabet index to lock the screen
- Music-app suggestion when a wired headset connects
- Custom photo, GIF or video live wallpaper from the phone

Wave Home leaves Android's notification shade and Quick Settings panel under Android control. Calendar, Usage Access and Device Admin permissions are requested only when the related feature is enabled. Weather uses the city selected by the user and does not request location permission.

## First use

1. Install and open the APK, then select Wave Home as the default Home app.
2. Tap an app to open it. Swipe right or long-press to open its app pop-up.
3. Use the app-list heading to switch between all apps, favorites and custom groups. Long-press an app to organize it.
4. Open the top-right menu for themes, type, widgets, wallpaper and other launcher options.

## Build

The Build Wave Home APK GitHub Actions workflow builds a debug APK when changes are pushed to the wave-home branch. The output is intended for sideload testing, not a Play Store release.
