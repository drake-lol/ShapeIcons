#!/usr/bin/env bash
# Switch the pack's built-in shape and refresh Lawnchair on a connected phone:
#   tools/apply_shape.sh <shape> [adb-serial]
# <shape> is circle, squircle, flower, hexagon, diamond, or the name of a PNG in shapes/.
# It rebuilds with a new version, installs, then makes Lawnchair redraw by switching its icon pack to "System icons" and
# back (Lawnchair caches by pack name only). The taps are measured for a Galaxy Z Fold7 cover screen: edit the TAP_*
# values below for another device. Other launchers just need the pack re-applied in their own settings.
set -euo pipefail
cd "$(dirname "$0")/.."
SHAPE=${1:?usage: tools/apply_shape.sh <shape> [adb-serial]}
SERIAL=${2:-}
ADB=(adb ${SERIAL:+-s "$SERIAL"})
LC=${LAWNCHAIR_PACKAGE:-app.lawnchair.nightly}      # app.lawnchair for the release build
TAP_SYSTEM_ICONS="150 1730"; TAP_APPLY="1100 1828"

"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "no device found: connect one (adb devices)" >&2; exit 1; }
./build.sh --bump --shape "$SHAPE" --install ${SERIAL:+"$SERIAL"}

if "${ADB[@]}" shell pm list packages | grep -q "package:$LC"; then
  "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP; sleep 1                          # taps are lost if the screen is off
  "${ADB[@]}" shell am force-stop "$LC"
  "${ADB[@]}" shell am start -a android.intent.action.VIEW -d lawnchair://settings/general-iconpack -p "$LC" >/dev/null; sleep 3
  "${ADB[@]}" shell input tap $TAP_SYSTEM_ICONS; sleep 2
  "${ADB[@]}" shell am start -a app.lawnchair.APPLY_ICONS --es packageName lol.drake.shapeicons >/dev/null; sleep 3
  "${ADB[@]}" shell input tap $TAP_APPLY; sleep 3
  "${ADB[@]}" shell am start -n "$LC/app.lawnchair.LawnchairLauncher" >/dev/null
  sleep 6
else
  echo "Lawnchair ($LC) is not installed: re-apply the pack in your launcher's settings instead."
fi
echo "shape: $SHAPE"
