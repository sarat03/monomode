# 6. Rules live in the engine; the app draws

Every decision (what to check, what to remove, what a mode changes, what it needs first, when to retry) lives in the pure-Kotlin `engine` module behind small interfaces (`Shell`, `Link`, `PhoneSettings`, `Store`) and is tested there without a phone. The `app` module only draws `PhoneState` and calls `Phone`. This is the project's layering:

| Layer | Module |
|---|---|
| Infrastructure | `adb` (protocol, pairing, TLS), `app/tls`, `Session`, `AppSettings`, `Records` |
| Domain | `engine`: `Debloat`, `Profile`, `Modes`, `Needs`, `Findings`, `Shelf`, `BookFile` |
| Application | `engine/Phone.kt`: connection, actions, errors, Retry rule |
| Presentation | `app`: Compose pages, `MainActivity`, `HomeActivity`, `Reader` |

A seam needs two adapters (production and test); otherwise it is indirection. See `docs/architecture-plan.md`. (2026-10-23)
