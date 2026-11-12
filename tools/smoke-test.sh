#!/usr/bin/env bash
# Installs a build on a running emulator and walks through first launch:
# choose a search engine, skip the default-browser step, open a real page.
# Prints what is on screen at each step and fails if the app crashes or a step is missing.
set -u

# Usage: smoke-test.sh [apk] [package]
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="${2:-com.oavi.browser.debug}"
ACTIVITY="com.oavi.browser.MainActivity"
FAILED=0

screen_xml() {
  adb shell uiautomator dump /sdcard/oavi-ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/oavi-ui.xml 2>/dev/null
}

# Prints the visible texts, one per line.
screen_texts() {
  screen_xml | python3 -c '
import re, sys
xml = sys.stdin.read()
seen = []
for text in re.findall(r"text=\"([^\"]+)\"", xml) + re.findall(r"content-desc=\"([^\"]+)\"", xml):
    if text not in seen:
        seen.append(text)
print("\n".join(seen[:60]))'
}

# Taps the centre of the first element whose text or description contains $1.
tap() {
  local point
  point=$(screen_xml | python3 -c '
import re, sys
needle = sys.argv[1]
xml = sys.stdin.read()
for node in re.findall(r"<node [^>]*>", xml):
    text = re.search(r"text=\"([^\"]*)\"", node)
    desc = re.search(r"content-desc=\"([^\"]*)\"", node)
    if (text and needle in text.group(1)) or (desc and needle in desc.group(1)):
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break' "$1")
  if [ -z "$point" ]; then
    echo "  !! nothing on screen matches \"$1\""
    return 1
  fi
  adb shell input tap $point
}

expect() {
  if screen_texts | grep -qF "$1"; then
    echo "  ok: \"$1\" is on screen"
  else
    echo "  FAIL: expected \"$1\" on screen"
    FAILED=1
  fi
}

alive() {
  if adb shell pidof "$PKG" >/dev/null 2>&1; then
    echo "  ok: app is running"
  else
    echo "  FAIL: app is not running"
    FAILED=1
  fi
}

step() { echo; echo "== $1"; }

# Saves what is on screen as shots/<name>.png.
shot() {
  mkdir -p shots
  adb exec-out screencap -p > "shots/$1.png" 2>/dev/null || true
}

main() {
step "install"
adb install -r "$APK" || return 1
adb logcat -c

step "launch"
adb shell am start -W -n "$PKG/$ACTIVITY" | tail -n 4
sleep 10
alive
screen_texts
expect "Which search engine do you use?"
shot 1-first-launch

step "choose a search engine"
tap "DuckDuckGo" && sleep 1
tap "Continue" && sleep 2
screen_texts
expect "Open links in Oavi?"

step "skip default browser"
tap "Not now" && sleep 3
screen_texts
expect "Search or enter address"
shot 2-new-tab

step "open a page from another app"
adb shell am start -a android.intent.action.VIEW -d "https://example.com/" "$PKG" | tail -n 2
sleep 20
alive
screen_texts
expect "example.com"
shot 3-page

step "open the menu"
tap "Menu" && sleep 2
screen_texts
expect "New tab"
expect "Dark pages"
shot 4-menu
adb shell input keyevent KEYCODE_BACK
sleep 1

step "open the tab grid"
tap "tabs open" && sleep 3
screen_texts
expect "New tab"
shot 5-tabs
adb shell input keyevent KEYCODE_BACK
sleep 1

step "address field"
tap "example.com" && sleep 2
adb shell input text "oavi"
sleep 2
screen_texts
shot 6-address
adb shell input keyevent KEYCODE_BACK
sleep 1

step "crashes"
CRASHES=$(adb logcat -d -s AndroidRuntime:E | grep -c "FATAL EXCEPTION" || true)
if [ "$CRASHES" != "0" ]; then
  echo "  FAIL: $CRASHES crash(es) logged"
  adb logcat -d -s AndroidRuntime:E | head -n 80
  FAILED=1
else
  echo "  ok: no crashes logged"
fi
alive

return $FAILED
}

main > smoke-output.log 2>&1
RESULT=$?

# With SMOKE_SCREENSHOTS=1 the screenshots are also printed, shrunk and base64-encoded, so
# they can be read back from the job log. They come first; the walk-through follows.
if [ "${SMOKE_SCREENSHOTS:-0}" = "1" ] && ls shots/*.png >/dev/null 2>&1; then
  python3 -m pip install --quiet pillow >/dev/null 2>&1
  python3 - <<'PY'
import base64, glob, io, os
from PIL import Image
for path in sorted(glob.glob("shots/*.png")):
    image = Image.open(path).convert("RGB")
    width = 300
    image = image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)
    data = io.BytesIO()
    image.save(data, "WEBP", quality=42, method=6)
    text = base64.b64encode(data.getvalue()).decode()
    print("SHOT-BEGIN " + os.path.basename(path))
    for start in range(0, len(text), 1800):
        print(text[start:start + 1800])
    print("SHOT-END")
PY
fi

cat smoke-output.log
echo
if [ "$RESULT" = "0" ]; then echo "SMOKE TEST PASSED"; else echo "SMOKE TEST FAILED"; fi
exit $RESULT
