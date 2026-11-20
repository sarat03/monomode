# 10. An opt-in update check, the one internet connection

Narrows ADR 0002 (its decision stands for everything else). MonoMode is installed from a GitHub Release, so nothing tells a user a fixed or security release exists. The app may now ask GitHub whether a newer release exists (issue #46, option 1).

- **One host, one request:** a single HTTPS GET of `https://api.github.com/repos/sarat03/monomode/releases/latest`. No account, token, identifier or cookie; redirects are not followed, so the host cannot change. GitHub sees the phone's IP address and that MonoMode asked.
- **Opt-in, off by default.** Settings > Updates: "Check for updates automatically" (at most once a day, counted from the last attempt, so a failed check does not retry on every resume), "Check now" (always runs), last checked (the last attempt), and the result. The found tag is kept, so an update found in the background is still shown after the app restarts.
- **The release page link is built, not taken.** The response's `html_url` is ignored. The tag must match `^v?\d+(\.\d+)*$` (`releaseOf`); only then does the app build `https://github.com/sarat03/monomode/releases/tag/<tag>` and open it in the browser. Any other tag (slashes, `..`, `%2e`, whitespace) gives no release and no link.
- **CI guard:** besides the path allow-list, `test.yml` asserts that `UpdateCheck.kt` holds exactly one `https://` literal and sets `instanceFollowRedirects = false`.
- **Not while a mode is on.** The automatic check does not run when a mode is on, and the check never touches Wireless debugging (ADR 0005): it needs no adb link.
- **No download, no install.** The app fetches only the release's JSON (size capped) and never an APK. Version compare and feed parsing are in the engine (`Update.kt`, `UpdateTest.kt`); the app only does the call and draws the result.
- **Where network code may live:** `app/src/main/kotlin/io/github/sarat03/monomode/update/UpdateCheck.kt`, besides `Session.kt` and `tls/`. The CI step "No network code outside the adb connection" allows exactly that path.

Out of scope, with the reason: option 2 (download and install) adds `REQUEST_INSTALL_PACKAGES`, a signing-certificate pin and a download path, a larger trust surface than a check. Option 3 (silent `pm install` over adb) has the app replace itself mid-session over the Wireless debugging link, which ADR 0005 says must go off. Users who want automatic updates today can point Obtainium at the GitHub Releases page.
