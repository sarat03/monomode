#!/bin/sh
# Usage: gen.sh /path/to/boringssl   (built with: cmake -GNinja -B build && ninja -C build crypto)
# Prints fresh vectors; private keys are random, so every run gives a new valid set.
set -eu
B=$1
OUT=$(mktemp -d)
clang++ -std=c++20 -I"$B" -I"$B/include" "$(dirname "$0")/gen.cc" "$B/build/libcrypto.a" -o "$OUT/gen"
"$OUT/gen"
