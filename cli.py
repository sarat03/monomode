"""Usage:
  python3 cli.py disable LIST [--apply] [--max-risk caution|risky] [--allow-protected PKG ...]
  python3 cli.py restore
  python3 cli.py drift
Dry-run unless --apply. Record lives in records/<serial>.json.
"""
import argparse, functools, subprocess, sys
from pathlib import Path
import core

ap = argparse.ArgumentParser()
ap.add_argument("cmd", choices=["disable", "restore", "drift"])
ap.add_argument("list", nargs="?")
ap.add_argument("--apply", action="store_true")
ap.add_argument("--max-risk", default="safe", choices=core.RISKS)
ap.add_argument("--allow-protected", nargs="*", default=[])
a = ap.parse_args()

serial = subprocess.run(["adb", "get-serialno"], capture_output=True, text=True, check=True).stdout.strip()
adb = functools.partial(core.real_adb, serial=serial)
record = Path(__file__).parent / "records" / f"{serial}.json"

if a.cmd == "disable":
    p = core.plan(adb, core.parse_list(Path(a.list).read_text()), a.max_risk, set(a.allow_protected))
    for k, v in vars(p).items():
        if v: print(f"{k}: {' '.join(v)}")
    core.apply(adb, p, record, dry_run=not a.apply)
    print("applied" if a.apply else "dry run; add --apply to disable")
elif a.cmd == "restore":
    core.restore(adb, record); print("restored")
else:
    d = core.drift(adb, record)
    print("\n".join(d) or "no drift"); sys.exit(1 if d else 0)
