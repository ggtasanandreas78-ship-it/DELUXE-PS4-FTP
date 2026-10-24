# DELUXE PS4 FTP — Android Studio project

## What it does
- Connects to a real FTP server by IP and port (default port 2121).
- Lists remote PS4 folders/files returned by the server.
- Tap a folder to enter it; tap a file to save/download it to Android.
- Use “رفع ملف” to choose a local Android file and upload it to the currently open remote directory.
- Shows transfer progress, approximate speed, and ETA while copying.

## Build
1. Open this folder in Android Studio (a current version with Android SDK 35).
2. Allow Gradle to sync and download `commons-net:commons-net:3.11.1`.
3. Select Build > Build Bundle(s) / APK(s) > Build APK(s).
4. The debug APK will be under `app/build/outputs/apk/debug/app-debug.apk`.

## PS4 setup
1. Connect the Android device and PS4 to the same local network.
2. On the PS4, launch GoldHEN and enable/start its FTP server.
3. Enter the PS4's current LAN IP and port (often 2121, but use the value shown by your server).
4. Press Connect. A successful connection is confirmed only when the server accepts login and returns a directory listing.

## Notes
- This is source code, not a prebuilt APK; it must be built in Android Studio.
- FTP is unencrypted and should only be used on a trusted local network.
- The app lists only files/folders accessible through the PS4 FTP server's permissions.
- Upload/download works in the current remote directory. Some GoldHEN FTP builds may not accept anonymous login or may restrict certain paths; if so, the app reports the server error.
