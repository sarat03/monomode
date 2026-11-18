# 5. Wireless debugging is off whenever MonoMode leaves the screen

Wireless debugging left on is an open adb port on the Wi-Fi network, so a session ends, and Wireless debugging is switched off and verified off, as soon as MonoMode stops being on screen (`onStop`), not in `onDestroy`. realme kills the app from Recents without calling `onDestroy`, which left it on. A change in progress finishes first; rotation does not count as leaving. Actions that need it switch it on for themselves (`Phone.withLink`). Tested in `PhoneTest`. (2026-10-24)
