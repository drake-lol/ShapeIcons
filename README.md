# Shape Icons

An Android **icon pack** where you choose the shape behind every icon (circle, squircle, flower, hexagon, diamond, or
your own PNG). The icons themselves are simple line glyphs; the background shape is swappable.

- One app, no extra downloads.
- **Smart Launcher** changes shape **live** when you tap a shape in the app.
- Every other launcher shows the shape that was **built into** the pack (default: flower). To change it, run one command.

> Why two behaviours? Android launchers load a pack's icons once and keep them. Smart Launcher is the only one that lets
> a pack hand over fresh icons on request, so it's the only one that can update live. [More](docs/how-it-works.md).

## Install and use

1. Install the APK (see [Build](#build) below) and open **Shape Icons**.
2. In the app, tap your launcher under **Apply to a launcher**. Or open your launcher's settings and pick the pack
   called **Shape Icons** (details per launcher: [docs/launchers.md](docs/launchers.md)).
3. Pick a shape in the app. Smart Launcher updates immediately. For any other launcher:

```bash
./build.sh --shape hexagon --bump --install   # rebuild with another shape and install
```

then re-apply the pack in that launcher (some need a nudge, see [troubleshooting](docs/troubleshooting.md)).

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
  the shape, then rebuild. Needed for launchers that only theme apps the pack lists (OnePlus does this).
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
