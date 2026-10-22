# Shape Icons

**Quick links:** [Install and use](#install-and-use) · [Works with](#works-with) · [Build](#build) · [Make it yours](#make-it-yours) ·
[Launchers](docs/launchers.md) · [How it works](docs/how-it-works.md) ·
[Build the shape selector into your own pack](docs/implementing-shape-selector.md) · [Customizing](docs/customizing.md) ·
[Troubleshooting](docs/troubleshooting.md) · [Launcher notes](docs/notes/)

An Android **icon pack** where you choose the shape behind every icon (circle, squircle, flower, hexagon, diamond, or
your own PNG). The icons themselves are simple line glyphs; the background shape is swappable.

- One app, no extra downloads.
- **Smart Launcher** changes shape **live** when you tap a shape in the app.
- Every other launcher shows the shape that was **built into** the pack (default: flower). To change it, run one command.

> Why two behaviours? Android launchers load a pack's icons once and keep them. Smart Launcher is the only one that lets
> a pack hand over fresh icons on request, so it's the only one that can update live. [More](docs/how-it-works.md).

## Install and use

**Easiest: download the app.**

1. Go to the [**latest release**](https://github.com/drake-lol/ShapeIcons/releases/latest) and download
   `ShapeIcons-<version>.apk` (about 1 MB). Each release also lists its SHA-256 so you can check the file.
2. Open the file on your phone and install it. Android will ask you to allow installs from your browser or file manager
   the first time.
3. Open **Shape Icons**. Under **Apply to a launcher**, tap your launcher. Or open your launcher's settings and pick the
   pack called **Shape Icons** (steps per launcher: [docs/launchers.md](docs/launchers.md)).
4. Pick a shape in the app. **Smart Launcher** updates right away. For any other launcher the shape is the one built in
   (flower), see below to change it.

Notes about the download: it needs Android 7.0 or newer, and it is signed with the standard Android debug key (not a Play
Store build), so to update to a newer release you may need to uninstall the old one first. It does not contain icons
from anyone's phone. All releases: [Releases page](https://github.com/drake-lol/ShapeIcons/releases).

**Change the built-in shape** (for every launcher except Smart Launcher): either build it yourself (see [Build](#build)):

```bash
./build.sh --shape hexagon --bump --install   # rebuild with another shape and install
```

then re-apply the pack in that launcher (some need a nudge, see [troubleshooting](docs/troubleshooting.md)).

**Built-in shapes:** circle, squircle, flower, hexagon, diamond, plus placeholder PNG shapes to try (`glassy`,
`gradient_tile`, `ring`, `neon`, `paper`, `gem`, `sticker`). They live in [`shapes/`](shapes/) and appear in the app's Shape row.

## Material You colours

Under **Colors** in the app, choose **Material You** and the icons take your wallpaper colours (and follow light/dark mode)
instead of the pack's own colours. Needs Android 12 or newer.

- **Smart Launcher:** the shape *and* glyph are drawn in the system palette each time, so it follows wallpaper changes.
  Apps the pack doesn't list use their own single-colour (monochrome) layer when they have one.
- **Lawnchair:** the pack also provides single-colour glyphs, so you can pick **Shape Icons** as the *themed icon source*
  and let Lawnchair colour them. This part is built but not tested yet.
- **Other launchers** show the fixed icons as before.

**Swap colors** (next to the colour choice) flips background and glyph: a light shape with a dark glyph instead of a dark
shape with a light glyph. It works in Original and Material You, and like those it only changes live in Smart Launcher.

## Works with

| Launcher | Shape changes | How you apply it |
|---|---|---|
| Smart Launcher | **live**, from the app | button in the app |
| Lawnchair | on rebuild | button in the app |
| Niagara | on rebuild | button in the app |
| Nova | on rebuild | pick it under Icon theme |
| Kvaesitso | on rebuild | Settings > Grid and icons > Icon pack |
| OxygenOS / ColorOS (OnePlus, Oppo, Realme) | on rebuild | Settings > Home screen > Icons |
| One UI (Samsung) | on rebuild | Theme Park > Icon pack |
| Microsoft, Action, Apex, ADW, GO, Atom, Motorola | on rebuild | pick it in the launcher's settings |

"Tested" status for each is in [docs/launchers.md](docs/launchers.md). Not everything was tested on a real device.

## Build

You need Android Studio's SDK (set `sdk.dir` in `local.properties`), Java 17, Python 3 and ImageMagick (`magick`).

```bash
./build.sh                                  # generate icons, check them, build the APK
./build.sh --install                        # ...and install on the connected phone
./build.sh --shape flower --bump --install  # pick the built-in shape; --bump = new version so launchers notice
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Make it yours

- **New shape:** drop a PNG in [`shapes/`](shapes/README.md). Done.
- **Icons for the apps you have:** `python3 tools/bake_installed.py <adb-serial>` draws every app on your phone onto
  the shape, then `./build.sh --baked --bump --install`. Keep these builds to yourself: they contain the list of apps on your phone
  (they are ignored by git, and `--baked` is off by default so releases never include them). Needed for launchers that only theme apps the pack lists (OnePlus does this).
- **Different glyph or app mapping:** edit the tables at the top of `tools/gen_icons.py`. See
  [docs/customizing.md](docs/customizing.md).

## Docs

- [Which launchers, how to apply each](docs/launchers.md)
- [How it works](docs/how-it-works.md)
- [How the shape selector works, and how to add it to your own icon pack](docs/implementing-shape-selector.md)
- [Customizing](docs/customizing.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Reverse-engineering notes per launcher](docs/notes/) (for the curious)

## Project layout

```
app/                Android app (dashboard + the pack + providers)
shapes/             your PNG shapes
tools/gen_icons.py  makes every icon and the pack index from small tables
tools/check.py      checks the generated files before building
tools/bake_installed.py   makes icons for the apps on your phone
tools/apply_shape.sh      change the shape and refresh Lawnchair
build.sh            generate + check + build + install
```
