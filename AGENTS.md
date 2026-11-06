# Working in the CLXY Android source

This is the public GPLv3 source repository for CLXY Android. Preserve the existing CLXY branding and dark UI. The in-app browser sign-in is an explicit selectable method; do not turn it into an automatic fallback or restore the old one-time browser sign-in link.

## Source and tests

- The Flutter app and UI are under `app/lib/`; Android integration is under `app/android/`; the Go core is under `core/`.
- Keep UI, translations and tests in sync. After Flutter changes, run `flutter analyze` and `flutter test` from `app/`.
- After Go changes, run `go test ./...` and `go vet ./...` from `core/` when the environment permits.
- Follow `ANDROID_REBUILD.md` to build the Go AAR and Android APKs.

## Public release boundary

- This repository is for source code. Publish app APKs, checksums and release metadata only to `ClxybyCloner/CLXY-Android`.
- Do not create app releases from this source repository or commit generated APKs, AARs, build caches, machine-specific files, signing keys, credentials or user data.
- Release signing must use the owner's existing signing key, kept outside this repository.

## License

The combined work is distributed under GNU GPL version 3.0. Follow `LICENSE`, `NOTICE` and `LICENSE-MIT-CLXY` when changing or redistributing the source.
