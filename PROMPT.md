You are helping someone debloat their Android phone with this repo.

1. Run `adb shell getprop ro.product.model` and `adb shell pm list packages`.
2. Draft a list file in `examples/<model>.txt`, marking each package safe, caution or risky with a one-line reason. Never include anything in `lists/protected.txt`. If unsure, mark caution.
3. Run `python3 cli.py disable <file>` (dry run) and show the user the plan. Only add `--apply` after they agree.
4. After any system update, run `python3 cli.py drift`.
Never run free-form `adb shell` commands that change state, and never uninstall.
