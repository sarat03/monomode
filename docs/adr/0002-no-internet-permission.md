# 2. No internet permission

The app makes no connections except to its own Wireless debugging port on the device. Debloat lists ship inside the app and change only through releases, so they cannot be altered remotely. The AI helper is deferred to version 2 for this reason.

Note: Android requires the INTERNET permission even for loopback sockets. M1 must confirm whether the app can declare it and still prove it only talks to localhost (for example, a network security config that blocks all cleartext and every non-loopback host).

Update 2026-11-14: the manifest declares INTERNET because the adb sockets need it. Sockets are opened only in `Session.kt` and `tls/` (the pairing and adb transports), to the phone's own adbd. There is no network security config yet, so this rule is kept by review: a new socket, URL or HTTP client anywhere else is a change to this ADR.

Update 2026-11-14 (later): CI now enforces this. The `test.yml` step "No network code outside the adb connection" fails on any socket, URL or HTTP client outside `Session.kt` and `app/tls/`. A network security config would not help: it governs HTTP stacks, not the raw sockets adb uses.
