# CLXY Android source

This repository contains the source code for CLXY Android: the Flutter app, Android integration, Go networking core, assets, tests and build scripts.

Downloadable APKs and release notes are published separately in the [CLXY Android releases repository](https://github.com/ClxybyCloner/CLXY-Android/releases).

## Build and test

Read [ANDROID_REBUILD.md](ANDROID_REBUILD.md) for the required toolchain, signing setup and build steps. For routine checks, run these commands from the `app/` directory:

```sh
flutter pub get
flutter analyze
flutter test
```

Run the Go checks from `core/`:

```sh
go test ./...
go vet ./...
```

## Source layout

The Flutter app and shared UI are under `app/lib/`. Android platform code is under `app/android/`. The Go core used by Android is under `core/`; build scripts are in `scripts/`, and app tests are in `app/test/`.

## Release boundary

This repository contains source code, not release APKs. The app checks for updates in `ClxybyCloner/CLXY-Android`; publish APKs, checksums and app release metadata there. Do not create app releases in this source repository.

Signing keys, credentials, user data, generated APKs and build caches do not belong in this repository. Release signing requires the owner's existing signing key, kept outside the source tree.

## License

The combined source release is distributed under GNU GPL version 3.0. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [LICENSE-MIT-CLXY](LICENSE-MIT-CLXY) for the applicable notices.
