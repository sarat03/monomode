# Releasing

## One-time setup

1. Make a key, and keep it backed up: losing it means users must uninstall to update.
   `keytool -genkeypair -v -keystore monomode.jks -alias monomode -keyalg RSA -keysize 4096 -validity 10000`
2. Add repo secrets: `MONOMODE_KEYSTORE_B64` (`base64 -i monomode.jks`), `MONOMODE_KEYSTORE_PASSWORD`, `MONOMODE_KEY_ALIAS`, `MONOMODE_KEY_PASSWORD`.

## Each release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Manual checklist on the Realme XT, with a release build:
   - [ ] Pair from scratch (Wireless debugging code)
   - [ ] Each mode turns on and off; apps come back after off
   - [ ] Own home screen: kiosk grid, e-reader, and the all-apps launcher with no mode on
   - [ ] Debloat a list, then restore it
   - [ ] Leaving MonoMode turns Wireless debugging off
3. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`. The `release` workflow builds the signed APK and creates the GitHub Release.

F-Droid comes after: it builds from source and signs with its own key, so it needs a metadata merge request to fdroiddata, not this workflow.
