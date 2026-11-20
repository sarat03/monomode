# 10. An opt-in update check, the one internet connection

Narrows ADR 0002 (its decision stands for everything else). MonoMode is installed from a GitHub Release, so nothing tells a user a fixed or security release exists. The app may now ask GitHub whether a newer release exists, and nothing more (issue #46, option 1).

- **One host, one request:** a single HTTPS GET of `https://api.github.com/repos/sarat03/monomode/releases/latest`. No account, token, identifier or cookie; redirects are not followed, so the host cannot change.
- **Opt-in, off by default.** Settings > Updates: "Check for updates automatically" (at most once a day), "Check now", last checked, and the result. A found release opens its GitHub release page in the browser, only if the URL is `https://github.com/sarat03/monomode/releases/...`.
- **Not while a mode is on.** The automatic check does not run when a mode is on, and the check never touches Wireless debugging (ADR 0005): it needs no adb link.
- **No download, no install.** The app fetches only the release's JSON (size capped) and never an APK. Version compare and feed parsing are in the engine (`Update.kt`, `UpdateTest.kt`); the app only does the call and draws the result.
- **Where network code may live:** `app/src/main/kotlin/io/github/sarat03/monomode/update/UpdateCheck.kt`, besides `Session.kt` and `tls/`. The CI step "No network code outside the adb connection" allows exactly that path.

Out of scope, with the reason: option 2 (download and install) adds `REQUEST_INSTALL_PACKAGES`, a signing-certificate pin and a download path, a larger trust surface than a check. Option 3 (silent `pm install` over adb) has the app replace itself mid-session over the Wireless debugging link, which ADR 0005 says must go off. Users who want automatic updates today can point Obtainium at the GitHub Releases page.
