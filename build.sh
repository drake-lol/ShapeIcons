#!/usr/bin/env bash
# One command: generate icons -> check -> build -> (optionally) install.
#   ./build.sh                       build only
#   ./build.sh --shape hexagon       choose the built-in shape (circle squircle flower hexagon diamond, or a PNG name from shapes/)
#   ./build.sh --install [serial]    also install on a connected phone
#   ./build.sh --bump                increase the version first (needed so launchers notice new icons)
#   ./build.sh --baked               include icons baked from your own apps (personal builds only, never commit/release these)
set -euo pipefail
cd "$(dirname "$0")"
SHAPE=(); BAKED=(); INSTALL=0; SERIAL=""; BUMP=0
while [ $# -gt 0 ]; do
  case "$1" in
    --shape) SHAPE=(--shape "${2:?--shape needs a name}"); shift 2;;
    --install) INSTALL=1; shift; if [ $# -gt 0 ] && [[ "$1" != --* ]]; then SERIAL="$1"; shift; fi;;
    --baked) BAKED=(--baked); shift;;
    --bump) BUMP=1; shift;;
    -h|--help) sed -n 2,7p "$0"; exit 0;;
    *) echo "unknown option: $1 (see ./build.sh --help)" >&2; exit 2;;
  esac
done
command -v magick >/dev/null || { echo "ImageMagick ('magick') is required." >&2; exit 1; }
[ -f local.properties ] || echo "note: local.properties missing; set sdk.dir=/path/to/Android/Sdk" >&2

python3 tools/gen_icons.py "${SHAPE[@]}" "${BAKED[@]}"
python3 tools/check.py
if [ "$BUMP" = 1 ]; then
  code=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
  sed -i "s/versionCode = $code\$/versionCode = $((code+1))/; s/versionName = \"[^\"]*\"/versionName = \"0.$((code+1))\"/" app/build.gradle.kts
fi
./gradlew --offline -q assembleDebug 2>&1 | grep -v '^Note' || true
APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || { echo "build failed: no APK" >&2; exit 1; }
echo "built $APK"
if [ "$INSTALL" = 1 ]; then
  ADB=(adb ${SERIAL:+-s "$SERIAL"})
  "${ADB[@]}" get-state >/dev/null 2>&1 || { echo "no device found (adb devices)" >&2; exit 1; }
  "${ADB[@]}" install -r "$APK"
fi
