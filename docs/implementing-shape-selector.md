# How the shape selector works, and how to build it into your own icon pack

This explains the whole mechanism in plain steps, then gives a recipe you can copy into another icon pack. Every file
mentioned is in this repo, so you can read the real code next to the explanation.

**Short version.** A normal icon pack is a pile of finished icons that launchers read once. To let the user choose a
shape, you have to do one of two things:

1. **Bake the shape into the icons** at build time (works in every launcher, but changing shape means a rebuild), or
2. **Draw the icons on request** (Smart Launcher asks the pack for each icon, so the shape can change live).

This pack does both. Part A and B below cover the first, Part C the second.

---

## Part A. What an icon is here: a shape plus a glyph

Every icon is two layers drawn on a 108 x 108 canvas:

- **Shape** (the background). A vector path, or any PNG you supply.
- **Glyph** (the picture: camera, phone, ...). Line art on a 24 x 24 grid, scaled to sit in the middle.

`tools/gen_icons.py` holds both as small tables:

```python
SHAPES = {"circle": circle(), "squircle": squircle(), "flower": flower(), ...}   # name -> vector path
GLYPHS = {"camera": ("#FF7A8A", [("M3,8h4l2,-3h6l2,3h4v11H3z", "s"), ...]), ...} # name -> colour + strokes
```

Because the two are separate, "change the shape" only means "draw the same glyphs on a different shape".

### Shapes are just paths
A shape is one closed path inside the 108 x 108 box. Examples in the generator:

- circle: two arcs.
- squircle: a "superellipse", `|x|^n + |y|^n = R^n` with n about 4.2, sampled into 160 points.
- flower: radius changes with the angle, `r = (R - a) + a * cos(petals * angle)`.
- hexagon / diamond: a polygon whose corners are cut with small curves.

To add a shape in code, add a function that returns a path string and put it in `SHAPES`.
To add one **without code**, drop a PNG in `shapes/` (see [shapes/README.md](../shapes/README.md)): it is used as is.

---

## Part B. Baking the shape in (works everywhere)

Static launchers (Nova, Lawnchair, Niagara, Kvaesitso, OxygenOS, One UI, ...) read a file called `appfilter.xml` that says
"app X uses drawable Y". We make drawable Y a **PNG that already contains the shape and the glyph**.

```
gen_icons.py --shape flower
   for each glyph:  SVG(shape path + glyph)  --ImageMagick-->  res/drawable-nodpi/icon_camera.png
   appfilter.xml:   <item component="ComponentInfo{com.oplus.camera/...}" drawable="icon_camera"/>
```

### Why PNGs and not vectors
Launchers re-mask vector and adaptive icons with **their own** shape setting. Lawnchair, for example, applies its icon
shape to every adaptive icon and puts non-adaptive vectors on a white plate. A plain **bitmap** from a pack is drawn
untouched. So baking the shape into a PNG is what makes your shape survive. (Trim the transparent padding first, or the
launcher will shrink the icon and it will look small.)

### How the user changes shape
Run the generator with another shape and rebuild with a **higher version code** (launchers cache by pack name + version):

```bash
./build.sh --shape hexagon --bump --install
```

then re-apply the pack in the launcher (some cache harder than others, see [troubleshooting](troubleshooting.md)).

### The app list inside the pack
The "Shape" row in the app also needs to know which shapes exist. The generator writes `res/values/shapes.xml`:

```xml
<string-array name="shapes"><item>circle</item><item>flower</item> ... <item>glassy</item></string-array>
```

and one drawable per shape, `shape_circle.xml` (vector) or `shape_glassy.png` (your PNG). `IconPrefs.shapes(context)` reads
the array, so adding a PNG to `shapes/` adds a chip to the app with no Java changes.

---

## Part C. Live shape change (Smart Launcher)

Smart Launcher has a second way to load packs: a **content provider**. Instead of reading finished icons, it sends the
app's own icon to the pack and the pack answers with the finished icon. Because the pack draws it at that moment, it can
use whatever shape the user chose a second ago.

### Step 1. Tell Smart Launcher the pack exists
In `AndroidManifest.xml`, an activity with this exact filter, and a provider whose authority is **your package name +
`.iconprovider`** (Smart Launcher looks it up by that exact name):

```xml
<activity-alias android:name=".SmartLauncherPack" android:targetActivity=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="ginlemon.smartlauncher.ICONPROVIDER"/>
    </intent-filter>
</activity-alias>

<provider android:name=".IconGeneratorProvider"
          android:authorities="com.yourcompany.yourpack.iconprovider"   <!-- = applicationId + ".iconprovider" -->
          android:exported="true"/>
```

### Step 2. Answer its requests (`IconGeneratorProvider.java`)
For every icon Smart Launcher does three things:

1. **Upload**: opens `content://AUTH/icon?input=<name>` for writing and saves the app's own icon as a PNG.
   Implement `openFile()` to give it a file in your cache folder.
2. **Query**: `query(content://AUTH/icon?output=<hash>, ..., selectionArgs = [json])`. The JSON says which app and how big:
   ```json
   {"version":"1.0","modelName":"AppConfig",
    "config":{"size":192,"packageName":"com.android.chrome","activityName":"...","action":-1,
              "placement":"homescreen","inputFileName":"<name from step 1>"}}
   ```
   (`modelName` is `ShortcutConfig` for shortcuts; `action` is -1 or 0-6 for dialer, music, browser, gallery, message,
   camera, email.) Draw the finished icon, save it as a PNG, and return a one-row cursor with column `fileuri`.
3. **Download**: opens that `fileuri`, reads the PNG, and shows it.

The heart of step 2 is tiny. Real code, simplified:

```java
String shape = prefs.shape();                       // what the user chose in the dashboard
Bitmap out = IconRenderer.render(ctx, pkg, activity, action, input, shape, prefs.sizeScale(), px);
// ...write PNG to cache, then:
MatrixCursor cur = new MatrixCursor(new String[]{"fileuri"});
cur.addRow(new Object[]{ content://AUTH/result/<file>.png });
return cur;
```

### Step 3. Draw the icon (`IconRenderer.java`)
`render()` composites in this order, which you can copy as is:

1. Draw the **shape** (`shape_<name>` drawable) onto a square bitmap.
2. If the pack has a **glyph for that app** (looked up from `appfilter.xml`), draw it on top, scaled by the size option.
3. Otherwise draw the **app's own icon**:
   - an adaptive icon: draw its background and foreground layers yourself (without the icon's own mask), then cut them with
     the shape using `PorterDuff.Mode.DST_IN`;
   - an old-style icon: draw it smaller on top of the shape.

That is how apps the pack knows nothing about still end up in your shape.

### Step 4. Make it change live (the handshake)
Smart Launcher remembers icons, so you must tell it to ask again. When the user picks a shape:

1. Save the choice and **bump a version number** (`IconPrefs`), so cached files with the old version are ignored.
2. Send Smart Launcher a broadcast: action `ginlemon.icongenerator.igRequiresUpdate`, package `ginlemon.flowerfree`, extra
   `requester` = your package name.
3. Smart Launcher replies `igAuthorizedToUpdate` (if your pack is its current pack) or `igNotAuthorizedToUpdate`.
   Register a receiver for the reply (`AuthReceiver.java`).
4. On *authorized*, send `ginlemon.icongenerator.igReadyForUpdatedIcons` with the same `requester`.
   Smart Launcher then re-queries every icon, and your provider draws them in the new shape.

All of this is in `DynamicIconProvider.apply()` and `AuthReceiver`.

> **Honest caveat.** The query/answer protocol (steps 1-3) was reverse-engineered from Smart Launcher 6.6 and tested
> directly against the provider on a phone. On Android 13+ Smart Launcher registers its receiver as "not exported",
> which can block the broadcast in step 4. If the home screen does not change, re-selecting the pack in Smart Launcher
> makes it re-query. The protocol may change in later versions.

---

## Part C2. Material You colours (optional)

Because the provider draws icons on request, it can colour them from the system palette:

```java
// MaterialYou.java (Android 12+): colours are read when the icon is drawn, so they follow the wallpaper and dark mode
dark ? new MaterialYou(c.getColor(android.R.color.system_accent1_800), c.getColor(android.R.color.system_accent1_100))
     : new MaterialYou(c.getColor(android.R.color.system_accent1_100), c.getColor(android.R.color.system_accent1_700));
```

In `IconRenderer.render(..., colorMode)`: tint the vector shape with `background`, tint the glyph with `glyph`
(`drawable.setTint()` recolours everything it draws and keeps transparency). For apps you have no glyph for, use the
app's `AdaptiveIconDrawable.getMonochrome()` (Android 13+) tinted with `glyph`, on the tinted shape.

Two details: put a signature of the colours in the cache file name (so a new wallpaper makes a new file), and leave PNG
shapes untinted, since tinting would flatten them.

For launchers that recolour packs themselves (Lawnchair), ship single-colour glyphs (`mono_*.xml`, white) and a
`res/xml/grayscale_icon_map.xml` (`<icon package="..." drawable="@drawable/mono_camera"/>`), and add the action
`app.lawnchair.icons.THEMED_ICON` to the pack's activity filter. `tools/gen_icons.py` writes both.

---

## Part D. Recipe for another icon pack

You can adopt as much or as little as you need.

**Level 1: shape chosen at build time (any launcher).**
1. Keep shape and glyph separate in your icon pipeline (two layers).
2. Render finished bitmaps (shape plus glyph) for each icon at about 192 px, trimming transparent padding.
3. Write `appfilter.xml` + `drawable.xml`, and add the discovery filters (`org.adw.launcher.THEMES`,
   `com.novalauncher.THEME`, `com.anddoes.launcher.THEME`, ...). Copy the `<activity-alias>` from this repo's manifest.
4. Add a build option (like `--shape`) that picks which shape to render, and bump the version code on every shape change.

**Level 2: a shape picker in your app.**
5. Put the shape names in a `string-array` and a drawable per shape. Show them as buttons. Save the choice in
   `SharedPreferences` (`IconPrefs`). This alone only affects your own preview.

**Level 3: live change in Smart Launcher.**
6. Copy `IconGeneratorProvider`, `IconRenderer`, `CacheFiles`, `AuthReceiver` and the manifest entries from Step 1.
7. Rename the provider authority to `<your package>.iconprovider`.
8. Call your equivalent of `DynamicIconProvider.apply()` when the user picks a shape.

**Level 4 (optional): cover apps you don't list.**
9. For launchers that only theme listed apps, bake icons for the user's installed apps (see `tools/bake_installed.py`)
   and add them to `appfilter.xml`. OxygenOS/ColorOS also needs the `icon_pack` string-array index
   (`tools/gen_icons.py` writes it).

### Things that bit me (so they don't bite you)
- **Vectors and adaptive icons get re-masked** by many launchers. Use PNGs for the finished icons.
- **Version code**: launchers cache by pack name + version. Forgetting to bump it looks like "nothing changed".
- **Lawnchair** caches by pack name only: switch the pack away and back after a rebuild.
- **Nova** puts its own plate behind icons unless *Reshape legacy icons* is Unthemed/Off.
- **OxygenOS/ColorOS** ignores `appfilter.xml` entries that aren't in its `icon_pack` package array.
- **ImageMagick** shrink-wraps an SVG to its content: add an invisible full-size rectangle if you render a glyph alone.
- **Untrusted input**: the provider is exported. Validate file names, cap image sizes, prune the cache, and return "no
  icon" instead of throwing.
