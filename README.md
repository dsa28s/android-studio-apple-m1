# Android Studio Arctic Fox for Apple Silicon

This is a unofficial canary version of Android Studio Arctic Fox on the Apple Silicon. (e.g M1 Chip)

<img src="https://raw.githubusercontent.com/dsa28s/android-studio-apple-m1/main/screenshot.png">

## Download

You can download at [project release page (Click Here!)](https://github.com/dsa28s/android-studio-apple-m1/releases)

## Troubleshooting

If it doesn't launch and macOS claims that the application is damaged then do not allow it to be moved to the bin, instead open up a Terminal and run the following:

`sudo xattr -r -d com.apple.quarantine /Applications/Android\ Studio\ Preview\ ARM64.app`

Then reopen and it should work fine.

## Current version

Android Studio Arctic Fox Canary 14 (Updated at 2021-04-08)

## Tested Project

- Android Project (Native)
- Flutter Project
- Kotlin Native Multiplatform

## Not working (still may issue)

- Zoom with Trackpad
- Kotlin Native Multiplatform iOS Build (But, Android build is ok)
  - You can build manualy open `*.xcodeproject` with Xcode.
- Android Studio splash screen not closing.
  - But, you can using editor normally. When the editor is closed, the splash screen also closed automatically.
- More.. (If an issue occurs while using it, please submit the issue. Thanks!)


<br>

## Disclaimer

This project is not official. I started this project because Android Studio (Intel) was too slow in my Apple Silicon environment. It will be updated weekly until Google officially supports Apple Silicon.
