# One UI / Theme Park

Checked on a Galaxy Z Fold7 (One UI 9.0, Android 17): Theme Park 1.1.02.2, One UI Home 18.0.03.29, Theme Center.

## How it works
- **One UI Home** has no third-party icon-pack support of its own. It shows icons that Theme Center keeps as PNGs in
  `/data/overlays/themepark/icons/` (and Theme Store packs).
- **Theme Park** (`com.samsung.android.themedesigner`) is the only door for third-party packs. It lists packs with
  `queryIntentActivities(org.adw.launcher.THEMES)` (`IconPackUtil.load3rdPartyIconPack`), reads the pack's
  `res/xml/appfilter` (or `assets/appfilter.xml`) and `res/xml/drawable.xml` (`IconPack.readAppFilter`,
  `readDrawableList`: `item`, `iconback img*`, `iconmask`, `iconupon`, `scale`), renders each mapped drawable
  with `ResourcesCompat.getDrawable` (`IconPack.loadBitmap`) and hands the bitmaps to Theme Center
  (`MyReceiver$IconWorker.getIconFromIconPack`, also re-run for newly installed apps).
- Theme Center's services need the signature permission `com.samsung.android.permission.MODIFY_THEME`, so a third-party
  app cannot push icons or trigger a refresh itself.

## What this means for the pack
- Already supported through the static path: the `org.adw.launcher.THEMES` activity alias, `appfilter.xml`,
  `drawable.xml`. The pack appears in Theme Park's icon pack list (`cmd package query-activities -a org.adw.launcher.THEMES`).
- It is a **snapshot**: icons are drawn from the pack's compiled resources when you apply it in Theme Park. There is no
  provider hook, so the dynamic shape/size switch does not reach One UI. Re-applying the pack picks up the resources
  baked into the installed APK, i.e. the shape chosen with `python3 tools/gen_icons.py --shape <name>` at build time.
- `appfilter.xml` now also maps Samsung stock apps (Camera, Gallery, Samsung Internet, My Files, Galaxy Store verified
  on the device; Phone, Messages, Contacts, Calendar, Clock, Calculator, Email are the usual component names but
  unverified because those apps are not on the test phone).

## Ways to get a live shape on One UI (not implemented)
- Self-update the pack APK with the new shape (needs a same-key signing step on device plus installer-of-record
  silent update; heavy).
- Ship one pack per shape (rejected: multiple installs).
