# Contributing to MonoMode

How work is done here, for people and for AI assistants alike. Words are defined in `CONTEXT.md`,
decisions in `docs/adr/`, features in `docs/features.md`.

## Workflow

1. **Issue first.** Every change starts as a GitHub issue. A new feature gets a short PRD in the issue:
   the problem, user stories, acceptance criteria and what is out of scope. Label it with the triage
   vocabulary in `docs/agents/triage-labels.md`.
2. **Plan.** For anything over a day, write the plan in `docs/<name>-plan.md` or the issue: the order of
   work, the seams it touches, what will be checked and how. `docs/PLAN.md` holds the product and milestones.
3. **Branch.** One branch per issue off `master`. Never commit to `master` directly.
4. **Test first.** Write the failing test, run it, write the code, run it. New rules go in `engine` with a
   unit test; `app` only draws and calls (ADR 0006).
5. **Check** before every commit:
   ```
   ./gradlew :adb:test :engine:test :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:lintDebug
   python3 -m unittest -v
   ```
   Changes to the connection, modes or debloat also run the real-adbd tests on an emulator
   (`./gradlew :app:connectedDebugAndroidTest`). Never on your own phone: they wipe app data.
6. **Close the loop** in the same PR (below).
7. **PR** to `master`, filled in from the template. CI (`test.yml`) must pass. Merge after review.

## Close the loop

Every PR leaves the project knowing what it now knows, so the next person or AI session starts from it
instead of rediscovering it:

- **`docs/features.md`**: add or update the feature's section (what, where, decisions, tests, open).
- **`docs/adr/`**: a new ADR for each decision that was hard to make, would surprise a reader, or rules
  something out. Number it next; never edit an old one's decision, add an update or a new ADR that replaces it.
- **`CONTEXT.md`**: any new or renamed term.
- **Plans and handoffs**: mark what is done; delete a handoff once its work is merged.
- Run `graphify update .` after code changes.

## Coding standards

- **Kotlin, 4-space indents**, as in `.editorconfig`. Keep the surrounding file's style; short one-line
  functions are normal here.
- **Smallest change that fully works.** No speculative options, wrappers or "for later" code. A known
  shortcut gets a `shortcut: <the limit>, <when to upgrade>` comment.
- **Comments say why**, in one line, not what the code does.
- **Layers** (ADR 0006): `adb` knows nothing of Android settings; `engine` knows nothing of Android;
  `app` holds no rules. A seam needs two adapters, production and test.
- **Names** come from `CONTEXT.md`. Members shared across `app` files are `internal`.
- **All screen text in `strings.xml`.** Plain language; never "hacked" or "safe" about the phone.
- **No network** beyond the phone's own adbd (ADR 0002). **No root, no factory reset** (ADR 0004).
- **Every change to the phone is restorable** and recorded as soon as it applies (ADR 0008).
- **Wireless debugging ends switched off** (ADR 0005).
- **Accessibility:** tap targets at least 48dp, TalkBack labels on icons, headings marked.
- **Lint** passes with no errors; suppress a check only where it occurs, with the reason (ADR 0009).
- **Commits:** one logical change each, present tense, saying what the user gets or what changed and why.

## Releasing

See `docs/release.md`.
