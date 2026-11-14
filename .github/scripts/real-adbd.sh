#!/bin/sh
# Runs the app's device tests against the emulator's real adbd (emulator.yml). Its own file because
# android-emulator-runner runs each line of `script:` as a separate command, so loops cannot go there.
set -u

# adbd can refuse `adb root` for a few seconds after boot ("unable to connect for root: closed")
for i in 1 2 3 4 5 6; do adb root && break; sleep 5; done
adb wait-for-device

# Emulator images run adbd with ro.adb.secure=0, so this covers the protocol, not key auth. The TLS listener is
# started with the prop instead of Wireless debugging, which needs a dialog; wait until adbd publishes its port.
adb shell setprop persist.adb.tls_server.enable 1
PORT=0
for i in $(seq 1 30); do
  PORT=$(adb shell getprop service.adb.tls.port | tr -d '\r')
  [ "${PORT:-0}" -gt 0 ] 2>/dev/null && break
  PORT=0; sleep 1
done
[ "$PORT" -gt 0 ] || { echo "adbd never published its TLS port"; exit 1; }
echo "tls port: $PORT"
sleep 3

# RealAdbdWirelessOffTest is left out: adb_wifi_enabled stays 0 when the listener is started by the prop
run() {
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.connectPort="$PORT" \
    -Pandroid.testInstrumentationRunnerArguments.notClass=io.github.sarat03.monomode.tls.RealAdbdWirelessOffTest
}
# one more run when it fails: the shared CI emulator sometimes resets an adb connection mid-handshake;
# a real regression fails twice and still turns the job red
run || { echo "device tests failed; running them once more"; run; }
