# Smart Launcher integration (no fork)

Reverse-engineered from Smart Launcher 6.6 build 023 (`ginlemon.flowerfree`, versionCode 660230), pulled from a device.
Class names are partly R8-obfuscated; the strings below are exact, class names in parentheses are SL's.

## Two ways SL loads an icon pack

1. **Static pack** (`r10`): classic `appfilter.xml` from the pack's `res/xml` or `assets`. Supports `item`, `iconback`,
   `iconmask`, `iconupon`, `scale`, `calendar prefix`, `<config adaptive="true">` and an SL-only `res/xml/shader`
   recipe. Icons come from `Resources.getDrawableForDensity()` on the pack's package, so they are fixed at install time.
   Discovery: `org.adw.launcher.THEMES`, MAIN + `com.anddoes.launcher.THEME`.
   Apply from a pack: `ginlemon.smartlauncher.setGSLTHEME` with string extra `package` (`ApplyIconPackActivity`).

2. **Icon generator** (`dh3`, `Ld`, `tp4`): a live pack. This is what Icon Pack Studio
   (`ginlemon.iconpackstudio`, same developer) uses, and it is what this app implements.

## Icon generator contract

Discovery: an activity with `MAIN` + category `ginlemon.smartlauncher.ICONPROVIDER`.
Provider authority must be exactly `<pack package>.iconprovider` (SL calls `resolveContentProvider`).

Per icon SL does:
1. `openOutputStream(content://AUTH/icon?input=<inputFileName>)` and writes the app's own icon as PNG.
2. `query(content://AUTH/icon?output=<hash>, null, null, [json], null)`; `json` is `selectionArgs[0]`:
   ```json
   {"version":"1.0","modelName":"AppConfig|ShortcutConfig",
    "config":{"size":192,"placement":"homescreen|drawer","isBubble":false,"layout":0,"layout2":-1,
              "inputFileName":"...","action":-1,"packageName":"...","activityName":"...","userid":0}}
   ```
   `ShortcutConfig` has `intent` instead of package/activity. `action` is -1 or a main-action id:
   0 dialer, 1 music, 2 browser, 3 gallery, 4 message, 5 camera, 6 email.
3. Reads cursor column `fileuri`, `openInputStream()`s it and decodes the PNG (rejects huge bitmaps).

Refresh handshake (`HomeScreen` registers receiver `tp4` at runtime):
- pack -> SL: `ginlemon.icongenerator.igRequiresUpdate`, extra `requester` = pack package
- SL -> pack: `igAuthorizedToUpdate` or `igNotAuthorizedToUpdate` (authorized only if the requester is the active
  home-screen or drawer pack)
- pack -> SL: `ginlemon.icongenerator.igReadyForUpdatedIcons`, extras `requester`, optional `placement`

Caveat to verify on device: on API 33+ SL registers that receiver with `RECEIVER_NOT_EXPORTED`, which normally blocks
broadcasts from other apps. If it does, a shape change shows up only after SL re-queries on its own (pack re-select,
SL restart, icon cache clear).

## Where it lives in this repo
- `IconGeneratorProvider` – the contract above, plus `requestUpdate` / `notifyReady`
- `AuthReceiver` – answers the handshake
- `IconRenderer` – shape + glyph (or SL's own icon for apps the pack has no glyph for)
- manifest: provider `lol.drake.shapeicons.iconprovider`, alias with the `ICONPROVIDER` category
