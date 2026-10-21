# Nova Launcher and Niagara Launcher

Neither launcher is installed on the test phone, so both are wired from public documentation and untested on a device.

## Nova
- Discovery: `com.novalauncher.THEME` with category `com.novalauncher.category.CUSTOM_ICON_PICKER` (plus the plain action form)
  is in the manifest alias. Nova reads `res/xml/appfilter.xml` (`item`, `iconback`, `iconmask`, `scale`, `calendar`)
  and `drawable.xml`; the static PNG icons are drawn as-is.
- Apply button: `com.teslacoilsw.launcher.APPLY_ICON_THEME`, package `com.teslacoilsw.launcher`, extras
  `com.teslacoilsw.launcher.extra.ICON_THEME_TYPE=GRID` and `...ICON_THEME_PACKAGE=<pack>`. Written from memory of the
  Blueprint icon pack template; the Nova docs search turned up nothing to confirm the names.
- Nova has no live hook, so the shape is the one built into the pack (`tools/gen_icons.py --shape`).

## Niagara
Source: Niagara's developer page, <https://help.niagaralauncher.app/article/24-resources-for-icon-pack-developers>.
- Apply button: action `bitpit.launcher.APPLY_ICONS`, package `bitpit.launcher`, string extra `packageName` (documented).
- Niagara's app-list buttons can be themed like apps: `bitpit.launcher/bitpit.launcher.Settings`, `.Pro`,
  `.RecentlyInstalled` are in `appfilter.xml` (settings, star and clock glyphs).
- Packs are standard appfilter packs, so the existing ADW/Nova discovery filters apply. Niagara also has an Icon
  Assistant (Pro) that can suggest alternatives from a pack; nothing to do on our side.
- Like Nova it is static: the shape is the built-in one.
