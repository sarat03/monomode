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

## 2. A settings port instead of shell text (next)

`Profile` turns each `Change` into shell text (`settings put secure …`, `cmd notification set_dnd …`),
and `Local.kt` parses that text back to run it in the app. Two adapters already exist (adb shell, in-app)
plus the test fake, so the seam is real, but it sits at the wrong level.

- Port in `engine`: `interface PhoneSettings { fun read(c: Change): String; fun write(c: Change, value: String) }`
  — or one `apply`/`restore` pair if that proves deeper.
- Adapters: over a `Shell` (today's `Shell.read/write` in `Profile.kt`), in-app (today's `Local.kt`,
  without its argv parsing), and a map for tests (replacing `FakeSettings`' argv parsing).
- `Phone`'s routed shell becomes "in-app adapter, falling back to the shell adapter per change".
- Tests: keep `ProfileTest`/`ModesTest` at the `applyProfile`/`Modes` interface; delete the
  argv-shaped assertions that only test the translation.
- Risk: restore of records written by older versions (same `ns key value` lines; keep the format).

## 3. Screen state out of MainActivity's companion object

Twelve `mutableStateOf` values in a `companion object` are shared by every page, and each page is a
private composable in one file. Move each page into its own file with an explicit state holder:

- `HomeTab`, `ModePage`, `CheckTab`, `CleanupTab`, `SettingsPage`, `PairScreen`.
- One `MainScreenState` (tab, open mode, cleaner filter, selections) passed down, so pages are
  previewable and Compose-testable (`KioskHomeTest`/`AllAppsTest` show the pattern).
- No behaviour change; check with the existing `androidTest` suite on the emulator only
  (it wipes app data — never on the user's phone).

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
