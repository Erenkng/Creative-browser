#!/usr/bin/env bash
# Boots a headless Android 17 emulator on a CI runner and runs the smoke test on it.
# Verbose tool output goes to *.log files; only the outcome of each step is printed.
set -u

SDK="${ANDROID_HOME:?ANDROID_HOME is not set}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
export PATH="$SDK/platform-tools:$SDK/emulator:$PATH"
# avdmanager and the emulator disagree about the default location on CI runners; name it.
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"

echo "== system image"
AVAILABLE=$("$SDKMANAGER" --list 2>/dev/null | grep -oE "system-images;android-3[7-9][^ |]*;x86_64" | sort -u)
# Prefer the plain Google APIs image, then the bare AOSP one, then whatever exists.
IMAGE=$(echo "$AVAILABLE" | grep -E ";google_apis;x86_64$" | sort -V | tail -n 1)
[ -z "$IMAGE" ] && IMAGE=$(echo "$AVAILABLE" | grep -E ";default;x86_64$" | sort -V | tail -n 1)
[ -z "$IMAGE" ] && IMAGE=$(echo "$AVAILABLE" | grep -vE "playstore" | sort -V | tail -n 1)
if [ -z "$IMAGE" ]; then
  echo "No Android 17 x86_64 system image is available. Images offered:"
  "$SDKMANAGER" --list 2>/dev/null | grep -oE "system-images;android-3[0-9][^ |]*" | sort -u | tail -n 30
  exit 1
fi
echo "using $IMAGE (of $(echo "$AVAILABLE" | wc -l) candidates)"

if ! yes | "$SDKMANAGER" --install "$IMAGE" "emulator" "platform-tools" > sdk-install.log 2>&1; then
  echo "Installing the system image failed:"; tail -n 20 sdk-install.log; exit 1
fi

echo "== create device"
DEVICE=$("$AVDMANAGER" list device -c 2>/dev/null | grep -m 1 -E "^pixel_(8|7|6|5)$" || true)
if ! echo no | "$AVDMANAGER" create avd --force -n oavi -k "$IMAGE" ${DEVICE:+--device "$DEVICE"} > avd-create.log 2>&1; then
  echo "Creating the virtual device failed:"; tail -n 20 avd-create.log; exit 1
fi
echo "device profile: ${DEVICE:-default}"

echo "== boot"
nohup emulator -avd oavi -no-window -no-snapshot -noaudio -no-boot-anim \
  -gpu swiftshader_indirect -memory 4096 > emulator.log 2>&1 &
EMULATOR_PID=$!
BOOTED=0
for _ in $(seq 1 90); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then BOOTED=1; break; fi
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "The emulator exited before booting:"; tail -n 25 emulator.log; exit 1
  fi
  sleep 5
done
if [ "$BOOTED" != "1" ]; then
  echo "The emulator did not finish booting:"; tail -n 25 emulator.log; exit 1
fi
echo "booted: Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')), $(adb shell wm size | tr -d '\r')"
adb shell input keyevent 82 >/dev/null 2>&1
sleep 5

# Test pages, reachable from the emulator as http://localhost:8000/.
python3 -m http.server 8000 --bind 127.0.0.1 --directory tools/fixtures > fixture-server.log 2>&1 &
SERVER_PID=$!
adb reverse tcp:8000 tcp:8000 > /dev/null

bash tools/smoke-test.sh "$@"
RESULT=$?

kill "$SERVER_PID" 2>/dev/null || true

adb emu kill >/dev/null 2>&1 || true
exit $RESULT
