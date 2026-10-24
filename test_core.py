"""Engine tests against a fake ADB. Run: python3 -m unittest -v"""
import json, subprocess, tempfile, unittest
from pathlib import Path

import core


class FakeAdb:
    """Records every argv; simulates package state like a real device."""

    def __init__(self, enabled, disabled=(), home_ok=True):
        self.enabled, self.disabled, self.home_ok, self.calls = set(enabled), set(disabled), home_ok, []

    def __call__(self, args):
        self.calls.append(args)
        if args[:3] == ["pm", "list", "packages"]:
            pkgs = self.disabled if "-d" in args else self.enabled | self.disabled
            return "".join(f"package:{p}\n" for p in sorted(pkgs))
        if args[:2] == ["pm", "disable-user"]:
            self.enabled.discard(args[-1]); self.disabled.add(args[-1])
            if args[-1] == "com.breaks.home": self.home_ok = False
            return f"Package {args[-1]} new state: disabled-user"
        if args[:2] == ["pm", "enable"]:
            self.disabled.discard(args[-1]); self.enabled.add(args[-1])
            if args[-1] == "com.breaks.home": self.home_ok = True
            return f"Package {args[-1]} new state: enabled"
        if args[:3] == ["cmd", "package", "resolve-activity"]:
            return "com.launcher/.Home" if self.home_ok else "No activity found"
        if args[:3] == ["settings", "get", "secure"]:
            return "com.kbd/.Ime"
        raise AssertionError(f"unexpected command {args}")


class ParseList(unittest.TestCase):
    def test_comments_blanks_and_risk(self):
        txt = "# header\n\ncom.a.b  # safe\ncom.c.d|caution\n"
        self.assertEqual(core.parse_list(txt), [("com.a.b", "safe"), ("com.c.d", "caution")])

    def test_rejects_shell_injection(self):
        for bad in ["com.a; reboot", "$(id)", "com.a b", "-f", "com..a", "a"]:
            with self.assertRaises(ValueError, msg=bad):
                core.parse_list(bad)

    def test_rejects_unknown_risk(self):
        with self.assertRaises(ValueError):
            core.parse_list("com.a.b|yolo")


class Plan(unittest.TestCase):
    def test_protected_packages_refused(self):
        adb = FakeAdb(["com.android.systemui", "com.bloat.x"])
        p = core.plan(adb, [("com.android.systemui", "safe"), ("com.bloat.x", "safe")])
        self.assertEqual(p.todo, ["com.bloat.x"])
        self.assertEqual(p.refused, ["com.android.systemui"])

    def test_override_allows_protected(self):
        adb = FakeAdb(["com.android.systemui"])
        p = core.plan(adb, [("com.android.systemui", "safe")], allow_protected={"com.android.systemui"})
        self.assertEqual(p.todo, ["com.android.systemui"])

    def test_skips_missing_and_already_disabled(self):
        adb = FakeAdb(["com.a.b"], disabled=["com.c.d"])
        p = core.plan(adb, [("com.a.b", "safe"), ("com.c.d", "safe"), ("com.not.here", "safe")])
        self.assertEqual(p.todo, ["com.a.b"])
        self.assertEqual(p.already, ["com.c.d"])
        self.assertEqual(p.missing, ["com.not.here"])

    def test_risky_needs_flag(self):
        adb = FakeAdb(["com.a.b"])
        self.assertEqual(core.plan(adb, [("com.a.b", "risky")]).todo, [])
        self.assertEqual(core.plan(adb, [("com.a.b", "risky")], max_risk="risky").todo, ["com.a.b"])


class Apply(unittest.TestCase):
    def setUp(self):
        self.rec = Path(tempfile.mkdtemp()) / "record.json"

    def test_dry_run_touches_nothing(self):
        adb = FakeAdb(["com.a.b"])
        core.apply(adb, core.plan(adb, [("com.a.b", "safe")]), self.rec, dry_run=True)
        self.assertNotIn("com.a.b", adb.disabled)
        self.assertFalse(self.rec.exists())

    def test_apply_writes_record_then_restore_matches_original(self):
        adb = FakeAdb(["com.a.b", "com.c.d", "com.keep.me"])
        before = set(adb.enabled)
        core.apply(adb, core.plan(adb, [("com.a.b", "safe"), ("com.c.d", "safe")]), self.rec, dry_run=False)
        self.assertEqual(json.loads(self.rec.read_text())["disabled"], ["com.a.b", "com.c.d"])
        core.restore(adb, self.rec)
        self.assertEqual(adb.enabled, before)  # release gate

    def test_record_merges_across_runs(self):
        adb = FakeAdb(["com.a.b", "com.c.d"])
        core.apply(adb, core.plan(adb, [("com.a.b", "safe")]), self.rec, dry_run=False)
        core.apply(adb, core.plan(adb, [("com.c.d", "safe")]), self.rec, dry_run=False)
        self.assertEqual(json.loads(self.rec.read_text())["disabled"], ["com.a.b", "com.c.d"])

    def test_auto_rollback_when_home_breaks(self):
        adb = FakeAdb(["com.breaks.home", "com.a.b"])
        with self.assertRaises(core.HealthError):
            core.apply(adb, core.plan(adb, [("com.a.b", "safe"), ("com.breaks.home", "safe")]),
                       self.rec, dry_run=False, batch=1)
        self.assertIn("com.breaks.home", adb.enabled)  # bad batch rolled back
        self.assertIn("com.a.b", adb.disabled)          # good batch kept and recorded
        self.assertEqual(json.loads(self.rec.read_text())["disabled"], ["com.a.b"])

    def test_only_allowlisted_commands_reach_adb(self):
        adb = FakeAdb(["com.a.b"])
        core.apply(adb, core.plan(adb, [("com.a.b", "safe")]), self.rec, dry_run=False)
        core.restore(adb, self.rec)
        for c in adb.calls:
            self.assertIn(c[0], {"pm", "cmd", "settings"})

    def test_a_command_failing_mid_batch_rolls_the_batch_back(self):
        adb = LockedAdb(["com.a.b", "com.vendor.locked"])
        with self.assertRaises(subprocess.CalledProcessError):
            core.apply(adb, core.plan(adb, [("com.a.b", "safe"), ("com.vendor.locked", "safe")]), self.rec, dry_run=False)
        self.assertIn("com.a.b", adb.enabled)
        self.assertEqual(json.loads(self.rec.read_text())["disabled"], [])


class LockedAdb(FakeAdb):
    """Refuses to disable com.vendor.locked, like adb's check=True does for a vendor-locked app."""
    def __call__(self, args):
        if args[:2] == ["pm", "disable-user"] and args[-1] == "com.vendor.locked":
            raise subprocess.CalledProcessError(255, args)
        return super().__call__(args)


class Drift(unittest.TestCase):
    def test_reports_reenabled_after_update(self):
        rec = Path(tempfile.mkdtemp()) / "r.json"
        adb = FakeAdb(["com.a.b", "com.c.d"])
        core.apply(adb, core.plan(adb, [("com.a.b", "safe"), ("com.c.d", "safe")]), rec, dry_run=False)
        adb.disabled.discard("com.c.d"); adb.enabled.add("com.c.d")  # OTA re-enabled it
        self.assertEqual(core.drift(adb, rec), ["com.c.d"])


if __name__ == "__main__":
    unittest.main()
