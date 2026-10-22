# Troubleshooting

**The shape didn't change.**
Only Smart Launcher changes live. For others: `./build.sh --shape NAME --bump --install`, then re-apply the pack in the
launcher. Lawnchair: `tools/apply_shape.sh NAME`. OxygenOS: re-apply under Settings > Icons.

**Icons look old after a rebuild.**
You forgot `--bump`. Launchers cache by version. Rebuild with `--bump` and re-apply.

**Nova puts a coloured square behind icons.**
Nova > Look & feel > Icon style > *Reshape legacy icons* > **Unthemed** (or Off).

**OnePlus: some apps keep their own icon.**
Run `python3 tools/bake_installed.py <serial>`, rebuild with `./build.sh --baked --bump --install`, re-apply under Settings > Icons. If one specific app
still looks stock, reset a custom icon you set on it in the launcher. Check what the system loaded:
`adb logcat -d | grep OplusIconPackUtil`.

**Smart Launcher didn't update after I tapped a shape.**
Smart Launcher only asks again if Shape Icons is its current icon pack (Settings > Icon pack). Re-apply it with the
button. If it still lags, open the app's Apply button for Smart Launcher once more.

**The pack isn't listed in my launcher.**
Check the pack is installed and the launcher is supported ([launchers.md](launchers.md)). To see what the system sees:
`adb shell cmd package query-activities --brief -a org.adw.launcher.THEMES`.

**`./build.sh` says ImageMagick is required.**
Install it (`sudo pacman -S imagemagick`, `sudo apt install imagemagick`, `brew install imagemagick`).

**`./build.sh` fails the check.**
It prints exactly which icon or file is missing. Fix that line (usually a typo in `APPS` or a bad PNG name in `shapes/`).

**wireless adb won't pair.**
The six digit code and port change each time the pairing dialog opens, and the dialog must stay open. Type the code
exactly as shown.
