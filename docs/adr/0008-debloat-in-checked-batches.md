# 8. Debloat in small checked batches, always restorable

App cleaner never changes a whole list at once. It changes five packages, then checks that each change took and that Home, the dialer and the keyboard still work, and undoes the batch if not (`engine/Engine.kt` `apply`). It only works for user 0, never a real uninstall: Disable is `pm disable-user`, Remove is `pm uninstall -k --user 0` and is allowed only for apps built into the phone, because a Play Store app's apk is deleted and could not come back. Every change is restored with `pm enable` or `cmd package install-existing`.

Each change is written to the restore record as soon as it is applied, so an app the undo cannot reach (connection lost mid-batch) stays on the Restore list. A copy of the record goes to `Downloads/MonoMode`. Only `safe` entries are pre-selected; `lists/protected.txt` is never offered.
