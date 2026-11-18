# Architecture plan

Where MonoMode's code can get deeper: more behaviour behind smaller interfaces, at seams where something
really varies, tested through those interfaces. Vocabulary (module, interface, seam, adapter, depth) as in
Matt Pocock's codebase-design skill.

Today the `adb` and `engine` modules are deep and well tested. Most of the risk sits in `app/`:
`Reader.kt` (≈1,100 lines), `MainActivity.kt` (≈800) and `HomeActivity.kt` (≈580) mix drawing with
decisions that have no tests.

## 1. What a mode needs, in one engine module (done)

What each option needs (the one-time setup, Do Not Disturb access) and what **Turn on** asks for first
were decided in `ModePage`'s `if`s. They now live in `engine/Needs.kt` behind two functions,
`Profile.need(option, allowed)` and `Profile.firstAsk(off, allowed)`, tested in `NeedsTest`. The screen
only draws the answer.

## 2. A settings port instead of shell text (done)

`Profile` used to turn each `Change` into shell text and `Local.kt` parsed it back to run it in the app.
Now `engine/Profile.kt` has the port, `PhoneSettings` (`read(change)`, `write(change, value)`, each saying
"not here" with null/false), and `orElse` to fall back from one adapter to the next. Adapters:
`ShellSettings` (over Wireless debugging, every change), `AppSettings` in the app (settings and Do Not
Disturb through Android's own APIs, where it may), and `ProfileTest.FakeSettings` behind `ShellSettings`
in tests. `PhoneSettingsTest` covers the fallback; Kiosk's unpin is a step `Modes.turnOff` takes, run
only while something is pinned. Restore records keep their `ns key value` lines.

## 3. MainActivity's pages, one file each (done, first half)

`MainActivity.kt` was one 800-line file. Each page is now its own file, as an extension of `MainActivity`:
`MainScreen.kt` (navigation, rail, two-pane), `HomeTab.kt`, `ModePage.kt`, `CheckTab.kt`, `CleanupTab.kt`,
`SettingsPage.kt`, `PairScreen.kt`, and `Widgets.kt` (Hero, Note, ListRow, ListDialog, AppIcon). The
activity keeps its lifecycle, the `Phone`, and the shared screen state in its companion object; members the
pages use are `internal` (app module only). Nothing a user sees changed: the 18 `androidTest` UI tests
pass on the emulator, and every page was checked on the phone and tablet emulators.

**Second half (when a page gets its own Compose test):** move that page from "extension of MainActivity"
to plain parameters (state in, callbacks out), so it can be previewed and tested without the activity.
Do it page by page, starting with `ModePage`, whose decisions already live in `engine/Needs.kt`. The
companion object's state then becomes one `MainScreenState` passed down. Done all at once, it would
rewrite every page for no visible gain. Recorded as ADR 0007.

## 4. Reader.kt

Split along what already varies: book formats behind `engine/BookFile.kt` (done), the page view, the
library, and the reading options. Do it with the reader's Phase 5 (see `docs/agents/handoff-reader.md`),
not before, so the pending work does not land on moved code.

## Habits that keep it this way

- New rules go in `engine` with a test first; `app` draws and calls.
- A seam needs two adapters (production + test); otherwise it is indirection.
- Test at the module's interface; when a deepening lands, delete the tests of the shallow parts.
- After code changes: `./gradlew :engine:test :adb:test :app:assembleDebug :app:compileDebugAndroidTestKotlin`,
  then `graphify update .`.
