# 3. Android 11 minimum for the app

Wireless debugging only exists on Android 11+. Phones on 7 to 10 use the Python CLI over USB. We rejected enabling `adb tcpip 5555` for them because it leaves an open port on the network.
