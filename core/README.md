# CLXY core

This Go module provides the network core used by CLXY. The app talks to it through a local API for accounts, settings, locations, routing, connection state, and logs.

## Android

The Android build binds `core/mobile` into `app/android/app/libs/core.aar`. `CoreService.kt` runs the core in the app process and establishes Android's VPN interface. The native `Tun2Socks` bridge passes packets from that interface to the core's local SOCKS listener; its NDK source is in `app/android/hev-socks5-tunnel`.

The Flutter UI and Android service keep the CLXY application ID, dark interface, and update channel. Hotspot sharing is not part of this backend integration.

## Data and checks

Account and configuration files stay in the app's private data directory. Treat that directory as private and do not include it in logs or test artifacts.

From `core/`, run the local test suite and static checks:

```sh
go test -count=1 -timeout=180s ./...
go vet ./...
```

Live account and service tests are opt-in and are not part of the ordinary local suite.
