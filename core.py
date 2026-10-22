"""cleanyourphone engine: plan, disable, verify, restore, drift. Stdlib only.

Security model: list files are data, never commands. Every package name is
validated against PKG_RE, and adb is invoked with an argv list (no shell).
"""
import json, re, subprocess
from dataclasses import dataclass, field
from pathlib import Path

PKG_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
RISKS = ["safe", "caution", "risky"]
PROTECTED = set((Path(__file__).parent / "lists" / "protected.txt").read_text().split()) - {"#"}


class HealthError(RuntimeError):
    pass


def parse_list(text):
    """Lines: `package[|risk]  # comment`. Risk defaults to safe."""
    out = []
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        pkg, _, risk = line.partition("|")
        pkg, risk = pkg.strip(), (risk.strip() or "safe")
        if not PKG_RE.match(pkg):
            raise ValueError(f"invalid package name: {line!r}")
        if risk not in RISKS:
            raise ValueError(f"unknown risk {risk!r} for {pkg}")
        out.append((pkg, risk))
    return out


def real_adb(args, serial=None):
    cmd = ["adb"] + (["-s", serial] if serial else []) + ["shell", *args]
    return subprocess.run(cmd, capture_output=True, text=True, check=True, timeout=30).stdout


def _pkgs(adb, *flags):
    return {l[8:].strip() for l in adb(["pm", "list", "packages", *flags]).splitlines() if l.startswith("package:")}


@dataclass
class Plan:
    todo: list = field(default_factory=list)
    already: list = field(default_factory=list)
    missing: list = field(default_factory=list)
    refused: list = field(default_factory=list)
    too_risky: list = field(default_factory=list)


def plan(adb, entries, max_risk="safe", allow_protected=frozenset()):
    installed, disabled = _pkgs(adb), _pkgs(adb, "-d")
    p = Plan()
    for pkg, risk in entries:
        if pkg in PROTECTED and pkg not in allow_protected: p.refused.append(pkg)
        elif pkg not in installed: p.missing.append(pkg)
        elif pkg in disabled: p.already.append(pkg)
        elif RISKS.index(risk) > RISKS.index(max_risk): p.too_risky.append(pkg)
        else: p.todo.append(pkg)
    return p


def healthy(adb):
    home = adb(["cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.HOME"])
    dial = adb(["cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.DIAL"])
    ime = adb(["settings", "get", "secure", "default_input_method"]).strip()
    return "/" in home and "/" in dial and ime not in ("", "null")


def _load(record):
    return json.loads(record.read_text()) if record.exists() else {"disabled": []}


def _save(record, data):
    record.parent.mkdir(parents=True, exist_ok=True)
    record.write_text(json.dumps(data, indent=2))


def apply(adb, p, record, dry_run=True, batch=5):
    """Disable in small batches; after each, read state back and check health.
    A batch that breaks Home/dialer/keyboard is re-enabled and apply stops."""
    if dry_run:
        return p
    data = _load(record)
    for i in range(0, len(p.todo), batch):
        chunk = p.todo[i:i + batch]
        for pkg in chunk:
            adb(["pm", "disable-user", "--user", "0", pkg])
        now_disabled = _pkgs(adb, "-d")
        if not healthy(adb) or not set(chunk) <= now_disabled:
            for pkg in chunk:
                adb(["pm", "enable", pkg])
            raise HealthError(f"batch {chunk} failed verification; rolled back")
        data["disabled"] = sorted(set(data["disabled"]) | set(chunk))
        _save(record, data)  # write after every good batch so a crash never loses the record
    return p


def restore(adb, record):
    for pkg in _load(record)["disabled"]:
        if PKG_RE.match(pkg):
            adb(["pm", "enable", pkg])


def drift(adb, record):
    """Packages we disabled that are enabled again (usually after an OTA)."""
    installed, disabled = _pkgs(adb), _pkgs(adb, "-d")
    return [p for p in _load(record)["disabled"] if p in installed and p not in disabled]
