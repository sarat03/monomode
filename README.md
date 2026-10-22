# cleanyourphone

Debloat and repurpose Android phones over ADB. Disables (never uninstalls), keeps a restore record, refuses protected packages, verifies the phone still works after every batch, and flags packages an update re-enabled.

## Use
```
python3 cli.py disable examples/generic.txt            # dry run
python3 cli.py disable examples/generic.txt --apply
python3 cli.py drift                                   # after an OTA
python3 cli.py restore
```
Needs `adb` and Python 3.10+. No other dependencies.

## List format
`com.vendor.app|caution   # comment` (risk: safe, caution, risky; default safe). Only package names are accepted; anything else fails to parse.

## What this is not
Not a security update. Old phones stop getting patches; the real fix is a maintained custom ROM for your model. Disabling apps reduces attack surface, nothing more.

## Test
`python3 -m unittest -v`
