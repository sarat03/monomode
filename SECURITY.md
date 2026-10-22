# Security

- List files are data. Package names must match `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$`; adb runs with an argv list, never a shell string.
- Only `pm list|disable-user|enable`, `cmd package resolve-activity` and `settings get` are sent to the device (enforced by test).
- Protected packages (`lists/protected.txt`) need an explicit per-package override.
- No dependencies. Turn off USB/Wireless debugging when done.

Report issues privately via GitHub security advisories.
