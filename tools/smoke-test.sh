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
print("\n".join(t if len(t) <= 80 else t[:79] + "…" for t in seen[:60]))'
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

# Prints "x1 y1 x2 y2" for the first element whose text or description contains $1.
bounds_of() {
  screen_xml | python3 -c '
import re, sys
needle = sys.argv[1]
for node in re.findall(r"<node [^>]*>", sys.stdin.read()):
    text = re.search(r"text=\"([^\"]*)\"", node)
    desc = re.search(r"content-desc=\"([^\"]*)\"", node)
    if (text and needle in text.group(1)) or (desc and needle in desc.group(1)):
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node)
        if b:
            print(*b.groups())
            break' "$1"
}

# Prints the lowest screen row covered by the fixture page's red bar (#c0392b), or -1.
red_bar_bottom() {
  python3 - "$1" <<'PY'
import sys
from PIL import Image
image = Image.open(sys.argv[1]).convert("RGB")
width, height = image.size
samples = range(0, width, 10)
bottom = -1
for y in range(height):
    hits = sum(1 for x in samples if (lambda p: p[0] > 150 and p[1] < 90 and p[2] < 80)(image.getpixel((x, y))))
    if hits > len(samples) * 0.6:
        bottom = y
print(bottom)
PY
}

# within NAME VALUE LOW HIGH: passes when LOW <= VALUE <= HIGH.
within() {
  if [ "$2" -ge "$3" ] 2>/dev/null && [ "$2" -le "$4" ] 2>/dev/null; then
    echo "  ok: $1 ($2 in $3..$4)"
  else
    echo "  FAIL: $1 ($2 not in $3..$4)"
    FAILED=1
  fi
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
# The engine logs whether it read the device profile written by OaviEngine.
adb logcat -d | grep -E "debug configuration|OaviEngine" | sed -E "s/^[0-9-]+ [0-9:.]+ +[0-9]+ +[0-9]+ //" | sort -u > engine-start.log
head -n 6 engine-start.log
if grep -q "Adding debug configuration from" engine-start.log; then
  echo "  ok: device profile was applied"
else
  echo "  FAIL: the engine did not report applying the device profile"
  FAILED=1
fi
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
{ tap "tab open" || tap "tabs open"; } && sleep 3
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
# The first Back hides the keyboard, the second closes the address field.
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1

step "protection sheet"
tap "Site protection" && sleep 2
screen_texts
expect "Connection is encrypted"
shot 7-protection
adb shell input keyevent KEYCODE_BACK
sleep 2

step "settings"
tap "Menu" && sleep 2
tap "More" && sleep 2
tap "Settings" && sleep 3
screen_texts
expect "Tracking protection"
shot 8-settings
adb shell input swipe 540 1900 540 500 300
sleep 2
expect "profile"
adb shell input keyevent KEYCODE_BACK
sleep 2

step "device on the local network"
# 10.0.2.2 is the emulator's host machine, on a private address: Android 17 asks first.
tap "Menu" && sleep 2
tap "New tab" && sleep 3
tap "Search or enter address" && sleep 2
adb shell input text "10.0.2.2"
adb shell input keyevent KEYCODE_ENTER
sleep 5
screen_texts
expect "reach devices on your network"
shot 9-local-network
tap "Not now" && sleep 5
screen_texts
expect "reach this device"
shot 10-local-network-refused

step "page bottom stays clear of the island"
tap "Menu" && sleep 2
tap "New tab" && sleep 3
tap "Search or enter address" && sleep 2
adb shell input text "localhost:8000/bottom.html"
adb shell input keyevent KEYCODE_ENTER
sleep 8
# The engine moves the page's fixed bars in its compositor, which accessibility bounds do
# not show, so the bar is found in the screenshot by its colour instead.
DENSITY=$(adb shell wm density | grep -oE "[0-9]+" | tail -n 1)
dp() { python3 -c "print(round($1 * ${DENSITY:-160} / 160))"; }
read -r _ MENU_TOP _ _ <<< "$(bounds_of "Menu")"
shot 11-fixed-bar
BAR_RESTING=$(red_bar_bottom shots/11-fixed-bar.png)
ISLAND_TOP=$(( ${MENU_TOP:-0} - $(dp 8) ))
echo "  resting island from y=$ISLAND_TOP, page's fixed bar ends at y=$BAR_RESTING"
within "fixed bar sits just above the resting island" "$BAR_RESTING" $(( ISLAND_TOP - $(dp 24) )) "$ISLAND_TOP"

for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do adb shell input swipe 540 1700 540 500 120; sleep 0.4; done
sleep 3
read -r _ PILL_TEXT_TOP _ _ <<< "$(bounds_of "localhost")"
shot 12-page-end
BAR_COMPACT=$(red_bar_bottom shots/12-page-end.png)
PILL_TOP=$(( ${PILL_TEXT_TOP:-0} - $(dp 9) ))
echo "  reading pill from y=$PILL_TOP, page's fixed bar ends at y=$BAR_COMPACT"
within "fixed bar follows the island down to the pill" "$BAR_COMPACT" $(( PILL_TOP - $(dp 24) )) "$PILL_TOP"
within "fixed bar moved down when the island shrank" "$BAR_COMPACT" $(( BAR_RESTING + $(dp 10) )) 99999

step "Turkish and dark theme"
# The phone switches to dark mode, and Oavi alone to Turkish (Android's per-app language).
adb shell cmd uimode night yes
adb shell cmd locale set-app-locales "$PKG" --locales tr-TR
sleep 6
# Bring the island back from its reading pill, unless the change already reset it.
if ! screen_texts | grep -qF "Menü"; then tap "localhost" && sleep 2; fi
screen_texts
expect "Menü"
shot 13-dark-turkish
tap "Menü" && sleep 2
screen_texts
expect "Karanlık sayfalar"
expect "Yeni sekme"
shot 14-menu-dark-turkish
adb shell input keyevent KEYCODE_BACK
sleep 2

step "private tab"
tap "Menü" && sleep 2
tap "Gizli sekme" && sleep 3
screen_texts
expect "gezdiğin sayfaları"
# Private pages are kept out of screenshots (FLAG_SECURE), so none is taken here.

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

cat smoke-output.log
echo
if [ "$RESULT" = "0" ]; then echo "SMOKE TEST PASSED"; else echo "SMOKE TEST FAILED"; fi

# SMOKE_SCREENSHOTS="all" (or a list of step numbers such as "7,9") also prints those
# screenshots, shrunk to 270 px and AVIF-encoded (about 4 KB each), so they can be read
# back from the end of the job log.
if [ -n "${SMOKE_SCREENSHOTS:-}" ] && [ "${SMOKE_SCREENSHOTS}" != "0" ] && ls shots/*.png >/dev/null 2>&1; then
  python3 -m pip install --quiet pillow >/dev/null 2>&1
  python3 - <<'PY'
import base64, glob, io, os
from PIL import Image
wanted = os.environ.get("SMOKE_SCREENSHOTS", "")
chosen = None if wanted in ("1", "all") else {part.strip() for part in wanted.split(",") if part.strip()}
for path in sorted(glob.glob("shots/*.png"), key=lambda p: int(os.path.basename(p).split("-")[0])):
    if chosen is not None and os.path.basename(path).split("-")[0] not in chosen:
        continue
    image = Image.open(path).convert("RGB")
    width = 270
    image = image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)
    data = io.BytesIO()
    image.save(data, "AVIF", quality=45, speed=4)
    text = base64.b64encode(data.getvalue()).decode()
    print("SHOT-BEGIN " + os.path.basename(path).replace(".png", ".avif"))
    for start in range(0, len(text), 1800):
        print(text[start:start + 1800])
    print("SHOT-END")
PY
fi
exit $RESULT
