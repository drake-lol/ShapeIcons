#!/usr/bin/env bash
# Switch the pack's built-in shape (what Lawnchair, Nova, Theme Park show) and refresh Lawnchair, all from the PC:
#   tools/apply_shape.sh <circle|squircle|flower|hexagon|diamond> [adb-serial]
# Regenerates the icons, bumps the version (so launchers see a new pack), builds, installs, then makes Lawnchair rebuild its
# icon cache by switching its icon pack to "System icons" and back (it caches on the pack name only).
# The tap coordinates below were measured on a Galaxy Z Fold7 cover screen; adjust for other devices.
set -euo pipefail
cd "$(dirname "$0")/.."
SHAPE=${1:?usage: apply_shape.sh <shape> [serial]}
ADB=(adb ${2:+-s "$2"})
LC=app.lawnchair.nightly            # app.lawnchair for the release build
DISPLAY_ID=${DISPLAY_ID:-}          # set for multi-display devices, e.g. 4630947123231501204 (only used for the screenshot)

python3 tools/gen_icons.py --shape "$SHAPE"
code=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
sed -i "s/versionCode = $code\$/versionCode = $((code+1))/; s/versionName = \"[^\"]*\"/versionName = \"0.$((code+1))\"/" app/build.gradle.kts
./gradlew --offline assembleDebug -q 2>&1 | grep -v '^Note' || true
"${ADB[@]}" install -r app/build/outputs/apk/debug/app-debug.apk | tail -1

if "${ADB[@]}" shell pm list packages | grep -q "package:$LC"; then
  "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP; sleep 1                         # taps are lost if the screen is off
  "${ADB[@]}" shell am force-stop $LC
  "${ADB[@]}" shell am start -a android.intent.action.VIEW -d lawnchair://settings/general-iconpack -p $LC >/dev/null; sleep 3
  "${ADB[@]}" shell input tap 150 1730; sleep 2                                    # "System icons" (first tile)
  "${ADB[@]}" shell am start -a app.lawnchair.APPLY_ICONS --es packageName lol.drake.shapeicons >/dev/null; sleep 3
  "${ADB[@]}" shell input tap 1100 1828; sleep 3                                   # "Apply" on Lawnchair's confirm sheet
  "${ADB[@]}" shell am start -n $LC/app.lawnchair.LawnchairLauncher >/dev/null
  sleep 6                                                                          # Lawnchair rebuilds its icons a few seconds later
fi
echo "shape: $SHAPE"
