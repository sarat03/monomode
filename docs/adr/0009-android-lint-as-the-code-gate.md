# 9. Android Lint as the code gate, no formatter

CI runs Android Lint (`:app:lintDebug`), which ships with the Android Gradle plugin, and fails on any error. It catches real faults (wrong format arguments, missing permissions, API levels) with no new dependency. Deliberate exceptions are suppressed where they occur, with the reason in a comment (for example `QUERY_ALL_PACKAGES` in the manifest).

We rejected ktlint and detekt for now: the codebase uses a dense style (short one-line functions, several statements on a line) that their defaults would rewrite across every file, a large diff that changes nothing a user sees and buries history. `.editorconfig` sets only what the code already does. Revisit when a second regular contributor joins. (2026-11-14)
