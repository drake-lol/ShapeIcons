# OxygenOS / ColorOS (OnePlus)

Checked on a OnePlus 13s (CPH2723), OxygenOS 16.1 (Android 16), launcher `com.android.launcher` 16.6.8,
over wireless adb.

## How it works
- No extra code is needed: the OS lists any pack that answers the standard discovery intents (`org.adw.launcher.THEMES`,
  Nova, Apex) under Settings > Home screen, Lock screen & style > Icons (also reachable by long-pressing an empty spot on
  the home screen > Icons). Shape Icons appears there next to Waffle and iLiquid.
- Applying stores the package name in `Settings.System icon_pack_name`. The launcher's UxIcon engine
  (`com.oplus.uxicon.ui.morphicon.IconPackLoaderFactory`) reads the pack's `appfilter.xml`, takes the drawable by name and
  caches per pack package + version code (so bump `versionCode` when the icons change, then re-apply).
- There is no apply intent: the UXDesign Icons screen is permission-protected (`com.oplus.permission.safe.SECURITY`),
  and Blueprint-style packs document the same manual steps. The dashboard has no OnePlus button for that reason.
- Static, like Nova/Lawnchair: the pack's baked shape (`tools/gen_icons.py --shape`), not the dashboard choice.

## Mappings
`appfilter.xml` includes OnePlus apps read from the device launcher list: Camera, Files, Gallery, Clock, Calculator,
Contacts, Phone dialer, OnePlus browser, Video and App Market.

## Observed
Mapped apps (Play Store, Photos, Chrome, YouTube, Phone, Camera) show the pack. The launcher only picked up new mappings
after a version bump plus re-applying the pack in Settings > Icons (Camera was stale until the second re-apply).
Files, "Mail" (Gmail, renamed on this phone) and Settings still showed non-pack icons although they are mapped; the
renamed label suggests per-app custom icons set in the launcher, which override the pack. Reset those items' icons in
the launcher to see the pack. Unmapped apps (Instagram, Beeper, Life360) keep their own icons.

## Icons for every installed app (tools/bake_installed.py)
Launchers that ignore the pack's generic `iconback`/`iconmask` leave unmapped apps untouched. `tools/bake_installed.py <serial...>`
asks the installed app (through its Smart Launcher icon-generator provider) to render each launchable app's own icon
clipped to the pack's shape, saves the PNGs to `tools/auto_icons/` (git-ignored, device specific), and
`tools/gen_icons.py` then adds them to `appfilter.xml` and `drawable.xml` (category "Your apps"). 212 icons were baked from a
OnePlus 13s and a Galaxy Z Fold7.

Result on the OnePlus 13s (OxygenOS 16.1): at first the baked entries were **not** drawn, even for an app mapped to a glyph
that worked elsewhere. The cause (found by reading the system parser, `q8.d0` in `com.oplus.uxdesign`, and its
`OplusIconPackUtil` log lines in logcat): OxygenOS/ColorOS first reads a Blueprint-style string array named `icon_pack`
(or `theme_iconpack`) of package names written with underscores (`com_instagram_android`), each backed by a drawable of the
same name, and only then `appfilter.xml`. Entries not in that array were not drawn. `tools/gen_icons.py` now writes
`res/values/icon_pack.xml` (drawable aliases plus the array) for every glyph-mapped and baked app. Logcat then reported
`getIconResMapFromXml final cache size: 895` and every app on the home screen drew in the pack's shape.

Check what the launcher loaded: `adb logcat -d | grep OplusIconPackUtil`.
