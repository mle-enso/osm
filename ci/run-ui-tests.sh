#!/usr/bin/env bash
set -euo pipefail

APP_ID="de.example.osmnoteswatch"
TEST_ID="de.example.osmnoteswatch.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"

mkdir -p artifacts

# Berlin. `geo fix` expects longitude first, then latitude.
adb emu geo fix 13.4050 52.5200
sleep 2

echo "Building and running the full UI suite at the Berlin location..."
./gradlew --no-daemon connectedDebugAndroidTest -PuiTestFakeNotes=true \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true

# leaveApksInstalledAfterRun above keeps the app and test APK installed. Re-run
# only the location test after changing the emulator position to prove that
# the app picks up a new GPS location without being rebuilt.
echo "Switching emulator GPS to Hamburg..."
adb emu geo fix 9.9937 53.5511
sleep 3

set +e
# Tests use JUnit 5 (@org.junit.jupiter.api.Test), which the plain
# AndroidJUnitRunner can't discover on its own. The android-junit5 Gradle
# plugin bridges this via a custom RunnerBuilder that connectedDebugAndroidTest
# wires in automatically; a manual `am instrument` call needs it spelled out.
adb shell am instrument -w \
  -e class de.example.osmnoteswatch.LocationUiTest \
  -e runnerBuilder de.mannodermaus.junit5.AndroidJUnitFrameworkBuilder \
  -e expectedLat 53.5511 \
  -e expectedLon 9.9937 \
  "$TEST_ID/$RUNNER" | tee artifacts/location-hamburg.txt
INSTRUMENT_STATUS=${PIPESTATUS[0]}
set -e

if [[ $INSTRUMENT_STATUS -ne 0 ]] || grep -Eq "FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=Process crashed" artifacts/location-hamburg.txt; then
  echo "Hamburg location test failed"
  exit 1
fi
