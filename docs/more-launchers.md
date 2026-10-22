# Kvaesitso, Microsoft Launcher, Action Launcher, Motorola

## Kvaesitso (tested, v1.41.0 from the project's GitHub release on a Galaxy Z Fold7)
- Source read: `AppFilterIconPackInstaller` discovers packs with `org.adw.ActivityStarter.THEMES`,
  `com.novalauncher.THEME` and `org.adw.launcher.THEMES`, then parses `appfilter.xml` (`item`, `calendar`, `iconback`,
  `iconmask`, `iconupon`, `scale`) and `drawable.xml`. It also supports `grayscale_icon_map` themed packs.
- The manifest alias now declares `org.adw.ActivityStarter.THEMES` too.
- Settings > Grid and icons > Icon pack lists "Shape Icons"; after selecting it, search results show Calculator, Calendar,
  Camera, Chrome and Clock in the pack's shape. There is no apply intent: the button just opens Kvaesitso.
- Static, like Lawnchair: the shape is the one built in.

## Microsoft Launcher (installed on the Fold, not exercised)
Documented route is Launcher Settings > Personalization > Icon pack, standard `appfilter.xml`. It was on its first-run
welcome screen, so I stopped before its onboarding. The dashboard button opens the launcher.

## Action Launcher (installed from Play on the Fold, not exercised)
Reads packs made for Nova/Apex/GO. Dashboard button opens the launcher; the pack is chosen in its settings.

## Motorola
`com.motorola.launcher.ACTION_ICON_PACK` filter added (it is in Lawnicons' manifest); the pack resolves under that action.
No Motorola device was available to test.
