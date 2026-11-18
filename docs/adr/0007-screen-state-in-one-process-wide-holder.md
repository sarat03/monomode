# 7. Screen state in one process-wide holder, not a ViewModel yet

`MainActivity`'s screen state (`paired`, `ps`, `tab`, `openMode`, `selected`, `query`, `restorePick`, `cleanFilter`) and the `Phone` live in its companion object. That holder is process-wide, so an open session survives the activity being recreated (rotation, fold, battery saver switching to dark theme), and phone and tablet layouts read the same fields.

We accept that this state is lost on process death; the session it guards ends on `onStop` anyway (ADR 0005). We are not moving it into a `ViewModel` in one change: that rewrites every page for no visible gain. Instead, when a page gets its own Compose test, it moves to plain parameters (state in, callbacks out), starting with `ModePage`; the companion's fields then become one `MainScreenState` passed down, which a `ViewModel` + `SavedStateHandle` can own. (2026-11-14)
