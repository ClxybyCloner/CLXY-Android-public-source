# CLXY Android app

This Flutter app keeps CLXY’s screens, dark theme, name, package ID (`com.clxy.android`) and update channel. It bundles the Go core as an AAR and connects the Android VPN service to it through a native TUN bridge.

## Build and test

See [`../ANDROID_REBUILD.md`](../ANDROID_REBUILD.md) for the pinned tool versions and build steps. The Android build includes `armeabi-v7a`, `arm64-v8a` and `x86_64`. The package version comes from `pubspec.yaml` and is passed to both the app and the core.

Run the Flutter checks from this directory:

```sh
flutter analyze
flutter test
```

Run Android unit tests from `android/`:

```sh
./gradlew :app:testDebugUnitTest
```

Sign-in and VPN traffic have not been tested on a real device for v1.0.26. The request-header change does not confirm that every HTTP 406 response is fixed.
