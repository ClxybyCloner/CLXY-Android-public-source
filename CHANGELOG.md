# Changelog

## 1.0.27 (Android ARM64)

- Improved sign-in request handling and challenge-cookie propagation.
- Live sign-in has not been verified; HTTP 406 behavior remains unconfirmed.

## 1.0.26 (Android)

- Added a selectable in-app browser sign-in method alongside the existing email and password form.
- Adjusted several sign-in challenge requests. This does not confirm that every 406 response is fixed.
- Added account backup import and export, HTTP/1.1 transport, IP version selection, and a configurable TUN MTU.
- Removed the old one-time browser sign-in link. The browser method is separate from the email and password form, not an automatic fallback.
- Packaged the ARM64 APK. Sign-in and VPN use have not been tested on a real device.

## 1.0.25 (Android ARM64 release)

- Removed browser/WebView sign-in and external account-page links. Sign-in failures and retries stay inside the app.
- Retained direct sign-in with the existing session-based PKCE OAuth retry and token-refresh recovery.
- Compatible in-place update for existing `com.clxy.android` ARM64 installs; versionCode 14147 and the same signing certificate.
- This keeps the existing recovery path; it does not claim to resolve every possible HTTP 406 response.

## 1.0.24 (Android release)

- Added alternate direct OAuth sign-in and token-refresh recovery for rejected requests.
- A clean-install sign-in and VPN traffic were verified on a phone; this does not prove that every HTTP 406 case is resolved.

## 1.0.23 (Android test build)

- Fixed VPN startup: the release shrinker had removed a native method needed by the TUN bridge.
- This test build had not been published. A real-device Connect test was still needed at the time.

## 1.0.22 (Android test build)

- Restored safe handling for JNI/native-library loading errors so a tunnel startup error is reported and cleaned up instead of escaping the VPN service.
- This ARM64-only test build had not been published. A real-device Connect test was still needed at the time.

## 1.0.21 (Android test build)

- Replaced the Android connection engine with the new Go core while keeping the CLXY screens, app identity and update channel.
- Connected Android’s VPN service to the core through a native TUN bridge.
- Hotspot proxy sharing and the separate sidecar path were unavailable in this build.
- This test build had not been published. Sign-in had not yet been verified on a device; the earlier HTTP 406 remained unconfirmed.
