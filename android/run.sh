#!/usr/bin/env bash
set -e

PACKAGE="com.soundboard"
ACTIVITY="$PACKAGE/.ui.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"

cd "$(dirname "$0")"

SERIAL=$(adb get-serialno 2>/dev/null || true)
if [ -z "$SERIAL" ] || [ "$SERIAL" = "unknown" ]; then
  echo "⚠  No device found. Connect via USB or: adb connect <ip>"
  exit 1
fi
echo "▶  Device: $SERIAL"

_adb() { adb -s "$SERIAL" "$@"; }

_reconnect() {
  [[ "$SERIAL" != *:* ]] && return 0
  for i in $(seq 1 10); do
    if adb connect "$SERIAL" 2>&1 | grep -q "connected"; then return 0; fi
    sleep 1
  done
  echo "⚠  Could not reconnect to $SERIAL after 10s" && exit 1
}

echo "▶  Building..."
./gradlew assembleDebug -q

echo "▶  Installing..."
_reconnect
_adb push "$APK" /data/local/tmp/app.apk
_reconnect
_adb shell pm install -r /data/local/tmp/app.apk

echo "▶  Launching..."
sleep 2 && _reconnect
_adb logcat -c
_reconnect
_adb shell am force-stop "$PACKAGE"
_reconnect
_adb shell am start -n "$ACTIVITY"

echo "▶  Waiting for app to start..."
PID=""
for i in $(seq 1 10); do
  sleep 1 && _reconnect
  PID=$(_adb shell pidof -s "$PACKAGE" 2>/dev/null || true)
  [ -n "$PID" ] && break
done

if [ -z "$PID" ]; then
  echo "⚠  App did not start after 10s — showing crash output and exiting"
  _adb logcat -d -v time "*:E"
  exit 1
fi

echo "▶  PID $PID — streaming logs (Ctrl+C to stop)"
exec adb -s "$SERIAL" logcat -v time --pid="$PID"
