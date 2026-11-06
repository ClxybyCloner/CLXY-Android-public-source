# Rebuild CLXY for Android

This guide covers rebuilding the full CLXY Android app, including the four-screen Flutter UI and Go VPN core. The project source, assets, tests, Gradle wrapper, and build scripts are kept together in this repository.

## Source map

| Part | Location | What it contains |
|---|---|---|
| Phone navigation | `app/lib/ui/mobile_shell.dart` | Home, Servers, Statistics, More tabs and More routes |
| Screen layouts | `app/lib/ui/overview_page.dart`, `servers_page.dart`, `stats_page.dart` | Main Home, server, and statistics screens |
| Shared UI | `app/lib/ui/` | Mobile visuals, settings, accounts, dialogs, widgets, and supporting pages |
| Assets and language | `app/assets/`, `app/lib/l10n/` | UI artwork, fonts, flags, and localization sources |
| Flutter tests | `app/test/` | UI and behavior tests |
| Android integration | `app/android/` | Gradle project, native service, tile, manifest, and resources |
| Go VPN core | `core/` | Android mobile binding and shared Go implementation |
| Build scripts | `scripts/build-android.ps1`, `.github/workflows/` | Local build and CI/build workflows |

The Go Android library `app/android/app/libs/core.aar` and `core-sources.jar` are generated outputs, so they are not checked in. The full Go source needed to regenerate the AAR is included. The Windows build script creates the ignored `libs/` output directory on a clean clone before running gomobile.

## Requirements

Use Flutter with Dart compatible with `app/pubspec.yaml` (currently Dart `^3.13.5`), the Go version declared by `core/go.mod`, Java 17, Android SDK, and Android NDK `30.0.16248370` (pinned in `app/android/app/build.gradle.kts`). Install `gomobile` and `gobind` from the x/mobile commit documented in [`core/doc/DEVELOPMENT.md`](core/doc/DEVELOPMENT.md), currently `8b95e45f8d3e`. Set `ANDROID_HOME` and `ANDROID_NDK_HOME` for the installed SDK and NDK.

On Windows, the included script needs PowerShell 7. Unless `ANDROID_HOME` is set, it looks for the Android SDK in `%LOCALAPPDATA%\Android\Sdk`.

## Quick UI checks

From `app/`, run:

```sh
flutter pub get
flutter analyze
flutter test
```

These checks do not require a release signing key. To run on an Android emulator or device, first generate the Go AAR, then run `flutter run -d <device-id>` from `app/`.

## One-command Windows build

From the repository root:

```powershell
pwsh -File scripts/build-android.ps1
```

The script reads the version from `app/pubspec.yaml`, builds the Go AAR with gomobile, resolves Flutter packages, and builds split-per-ABI release APKs. It writes local outputs under `dist/CLXY-<version>-android/`; generated output is ignored by Git.

## Linux/macOS or manual build

Install the required Go version, Android SDK/NDK, and Java first. Install the pinned gomobile tools and initialize gomobile (see `core/doc/DEVELOPMENT.md` for the full toolchain details):

```sh
go install golang.org/x/mobile/cmd/gomobile@8b95e45f8d3e
go install golang.org/x/mobile/cmd/gobind@8b95e45f8d3e
gomobile init
```

Then build the AAR and Flutter app. The app version is read from `app/pubspec.yaml`:

```sh
VERSION="$(sed -nE 's/^version:[[:space:]]*([0-9]+\.[0-9]+\.[0-9]+).*/\1/p' app/pubspec.yaml | head -n 1)"
mkdir -p app/android/app/libs
(
  cd core
  VERSION_PACKAGE="$(go list -f '{{.ImportPath}}' ./internal/version)"
  gomobile bind -target=android -androidapi 24 -trimpath \
    -ldflags "-s -w -X ${VERSION_PACKAGE}.Current=$VERSION" \
    -o ../app/android/app/libs/core.aar ./mobile
)
(
  cd app
  flutter pub get
  flutter analyze
  flutter test
  flutter build apk --release --split-per-abi \
    --dart-define=APP_VERSION="$VERSION" \
    --dart-define=GITHUB_REPO=ClxybyCloner/CLXY-Android
)
```

Flutter or Gradle may create `app/android/local.properties` for the machine's SDK path. That file is intentionally excluded and must not be committed.

## Signing and release boundary

No signing key, `key.properties`, credential, or release keystore is included. Gradle reads a release key from `app/android/key.properties` when one is supplied; otherwise it uses the configured local signing key or the machine's default debug key. An APK can update an existing install only when its signing certificate matches. Keep all signing material outside version control.

The app checks `ClxybyCloner/CLXY-Android` for updates. Publish APKs, checksums and app release metadata only in that separate distribution repository. This public source repository does not publish app releases.

## GitHub Actions workflows

The workflows in `.github/workflows/` are included. `build.yml` runs Go and Flutter checks on pushes to `main` and pull requests. A manual workflow run can build platform artifacts for review, but does not create a release. The Windows dashboard capture and installer smoke-test workflows are manual and consume artifacts from a build run.

The source repository starts from a snapshot without the original Git history, so CI does not compare historical commits. It still runs the current Go and Flutter test suites.

## v1.0.26 verification

The release APK is ARM64 only. It reports package `com.clxy.android`, version name `1.0.26`, version code `14148`, and minimum Android API 24. Its native libraries contain only `arm64-v8a`. `apksigner verify` passed, and the certificate matches the existing v1.0.25 release signer. APK SHA-256: `6a49bc2594ab3f622374dae6c7e25344764ecfa3b1aab89971b0123592a28177`.

The full Go test suite, `go vet ./...`, Flutter analysis, and all Flutter tests passed. Sign-in and VPN use have not been tested on a real device. The request-header change does not confirm that all HTTP 406 responses are fixed.
