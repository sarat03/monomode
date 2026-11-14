#!/bin/sh
# Helpers for scripted runs of the reader on an emulator. Source it: . tools/reader-ui.sh
# They read the screen with uiautomator, so they see text and content descriptions, not pixels.
A=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}

# every text on screen with its bounds; a content description is shown in (brackets)
dump() { $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A shell cat /sdcard/u.xml | python3 -c "
import re,sys,html
for m in re.finditer(r'<node[^>]*?text=(\"[^\"]*\"|\'[^\']*\')[^>]*?content-desc=(\"[^\"]*\"|\'[^\']*\')[^>]*?bounds=\"([^\"]*)\"',sys.stdin.read()):
    t=html.unescape(m.group(1)[1:-1]) or ('('+html.unescape(m.group(2)[1:-1])+')' if len(m.group(2))>2 else '')
    if t: print(' ',t[:70],m.group(3))"; }

# the same, without the test books' own paragraphs ("C2P77. …")
ui() { dump | grep -vE "^  C[0-9]+P[0-9]+\."; }

# tap the first thing whose line contains the text: tap 'A Long Voyage', tap '(Contents)'
tap() { xy=$(dump | grep -F -- "$1" | head -1 | python3 -c "
import re,sys
m=re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]\s*$',sys.stdin.read()); print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2)"); $A shell input tap $xy; }

# which paragraphs of the test book start on the visible page (screen width 2560)
vis() { $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A shell cat /sdcard/u.xml | python3 -c "
import re,sys
v=[m.group(1) for m in re.finditer(r'text=\"(C\d+P\d+)\.[^\"]*\"[^>]*?bounds=\"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]\"',sys.stdin.read()) if 0<=int(m.group(2))<2560 and int(m.group(4))>int(m.group(2))]
print('visible:',(v[0]+' .. '+v[-1]+' (%d)'%len(v)) if v else 'none')"; }

# the activity in front
top() { $A shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //'; }
