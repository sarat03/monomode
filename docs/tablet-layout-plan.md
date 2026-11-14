# Tablet layout plan

Source guidance: claude-android-ninja `references/android-navigation.md` → "Adaptive Quality and Large Screens" and "Scenes & Custom Layouts".
Target: tier 3 (adaptive ready) everywhere, tier 2 (responsive at all widths, state survives resize) for `MainActivity`.

## State stays shared

There are no `ViewModel` classes today. All UI state for `MainActivity` lives in its companion object
(`paired`, `ps: PhoneState`, `tab`, `openMode`, `selected`, `query`, `restorePick`, `cleanFilter`) and the
`Phone` engine pushes `PhoneState` through `onChange`. That holder is process-wide, so it already survives
rotation, fold/unfold and multi-window resize.

Rule for this work: **one state holder, many layouts.** The phone and tablet layouts read and write the same
fields. No layout gets its own copy of `tab`/`openMode`/`selected`; the window size class only picks how the
same state is drawn. Moving this into a real `ViewModel` + `SavedStateHandle` (the skill's recommendation for
process death) is a separate change, not part of the tablet layout.

## Window size → chrome

| Width | `MainActivity` | `HomeActivity` (launcher) |
|---|---|---|
| Compact < 600dp | As today: Home tiles are the navigation, `Paged` top bar + back | As today |
| Medium 600–840dp | Navigation rail | Wider grid, no rail |
| Expanded > 840dp | Navigation rail + list-detail on Home | Wider grid, no rail |

Read the size with `currentWindowAdaptiveInfo().windowSizeClass`. Use `NavigationSuiteScaffold` with
`NavigationSuiteType.None` on compact so the phone layout is unchanged (the app has no bottom bar and should
not gain one).

New dependencies: `androidx.compose.material3:material3-adaptive-navigation-suite` and
`androidx.compose.material3.adaptive:adaptive-layout` (BOM-managed). Navigation3 is **not** needed: the app
navigates with `tab`/`openMode`, and swapping that for a back stack is a separate migration.

## Screens

### Navigation rail changes (medium and expanded, only while `ps.connected`)

Rail items map 1:1 to existing `Page` values, so the rail just sets `tab`:

| Rail item | Sets | Screen |
|---|---|---|
| Home | `tab = HOME; openMode = null` | `HomeScroll` |
| Network & access | `tab = CHECK` | `CheckTab` |
| App cleaner | `tab = CLEAN` | `CleanupTab` (+ `cleanupBar` stays at the bottom of the content pane) |
| Settings | `tab = SETTINGS` | `SettingsPage` |

- `MainActivity.Screen` / `SessionScreen`: wrap in the suite scaffold.
- `Paged`: on medium+ hide the back arrow for top-level pages (rail replaces it); keep `BackHandler` so Back
  still returns to Home as today.
- `HomeScroll`: on medium+ drop the "Network & access" and "App cleaner" `HomeRow`s (they duplicate rail
  items); keep the mode tiles.
- `PairScreen`, `StartScreen` (not connected): no rail; single pane centred at max ~600dp width.

### List-detail changes (expanded only)

| Screen | List pane | Detail pane |
|---|---|---|
| Home → Mode page | `HomeScroll` mode tiles | `ModePage(openMode)` incl. `HomePicker` |

Use `ListDetailPaneScaffold` driven by `openMode`: null shows the list with an empty-detail hint, a tile tap
sets `openMode` and fills the detail pane without leaving Home. Back clears `openMode` (same as the current
`Paged` back lambda). On compact/medium it collapses to today's behaviour (tile → full-screen `ModePage`).

No other screen has a detail page today, so none gets list-detail:
- `CheckTab`, `CleanupTab`/`RestoreList`: single pane, cap content width (~840dp) so rows don't stretch;
  `ListDialog` stays a dialog.
- `SettingsPage`: single pane, capped width.

### Launcher (`HomeActivity`)

A home screen, so no rail and no list-detail.
- `Kiosk`: app grid columns from width (`GridCells.Adaptive`) instead of a fixed count.
- `Reader`: cap the text column at ~640dp, centred on the paper background.
- Keep the clock/header; check the full-screen insets code in landscape.

## Required checks (tier 3)

- Manifest has no `screenOrientation`/`resizeableActivity=false` today: keep it that way.
- Rotate, split-screen, and resize on each of: Home, Mode page (expanded list-detail ↔ compact full screen
  with `openMode` set), App cleaner with a selection in `selected`, Pair dialog.
- Keyboard: rail items and tiles focusable in visual order; Enter activates.
- Foldable: no primary action on the hinge in the expanded split (the scaffold handles the hinge for panes).

## Order of work

1. Add the two adaptive deps; wrap `Screen` in `NavigationSuiteScaffold` (rail on medium+, none on compact).
2. List-detail for Home → Mode page on expanded.
3. Width caps for the single-pane screens; adaptive grid + reader width in `HomeActivity`.
4. Run the rotation/resize matrix above on a tablet emulator (not the user's phone; connected tests wipe app data).
