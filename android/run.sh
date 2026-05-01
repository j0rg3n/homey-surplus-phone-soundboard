#!/usr/bin/env bash
set -e

PACKAGE="com.soundboard"
ACTIVITY="$PACKAGE/.ui.MainActivity"

cd "$(dirname "$0")"

echo "▶  Building and installing..."
./gradlew installDebug -q

echo "▶  Clearing logcat buffer..."
adb logcat -c

echo "▶  Launching $PACKAGE..."
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$ACTIVITY"

sleep 1

PID=$(adb shell pidof -s "$PACKAGE" 2>/dev/null || true)
if [ -z "$PID" ]; then
  echo "⚠  App did not start — showing crash output and exiting"
  adb logcat -d -v time "*:E"
  exit 1
fi

echo "▶  PID $PID — streaming logs (Ctrl+C to stop)"
exec adb logcat -v time --pid="$PID"
