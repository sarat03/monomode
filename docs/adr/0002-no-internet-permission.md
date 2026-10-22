# 2. No internet permission

The app makes no connections except to its own Wireless debugging port on the device. Debloat lists ship inside the app and change only through releases, so they cannot be altered remotely. The AI helper is deferred to version 2 for this reason.

Note: Android requires the INTERNET permission even for loopback sockets. M1 must confirm whether the app can declare it and still prove it only talks to localhost (for example, a network security config that blocks all cleartext and every non-loopback host).
