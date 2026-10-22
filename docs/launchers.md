# Launchers

Short answer for each launcher: how to apply, whether the shape changes live, and how well it was tested.
"Tested" means I ran it on a real phone and looked at the home screen.

| Launcher | Apply | Live shape | Tested |
|---|---|---|---|
| Smart Launcher | **Apply** button, or Settings > Icon pack | Yes | Yes (Galaxy Z Fold7) |
| Lawnchair | **Apply** button | No | Yes (nightly 16-dev, Fold7) |
| Nova | Settings > Look & feel > Icon style > Icon theme | No | Yes (8.9.4, Fold7) |
| Niagara | **Apply** button | No | Yes (1.16.33, Fold7) |
| Kvaesitso | Settings > Grid and icons > Icon pack | No | Yes (1.41.0, Fold7) |
| OxygenOS 16 | Settings > Home screen, Lock screen & style > Icons | No | Yes (OnePlus 13s) |
| One UI (Theme Park) | Theme Park > Icon pack | No | Partly (pack is listed; applying not run) |
| Microsoft Launcher | Launcher settings > Personalization > Icon pack | No | No (installed, not set up) |
| Action Launcher | Launcher settings | No | No (installed, not opened) |
| Apex, ADW, GO, Atom | their icon pack settings | No | No (standard format, should work) |
| Motorola | Icon pack setting | No | No (filter only) |

**Live shape** means: tap a shape in the app and the home screen changes by itself. Only Smart Launcher supports that.
For the rest the shape is whatever was built in; change it with `./build.sh --shape NAME --bump --install` and re-apply.

## Small things per launcher

- **Nova:** set *Icon style > Reshape legacy icons* to **Unthemed** or **Off**, otherwise Nova puts its own coloured plate
  behind our icons.
- **Lawnchair:** it remembers icons by pack name, so after a rebuild switch to *System icons* and back (the script
  `tools/apply_shape.sh` does this for you). Lawnchair's own *Icon shape* setting does not affect our icons.
- **OxygenOS / ColorOS:** the launcher only themes apps the pack knows about. Run `tools/bake_installed.py` for your phone
  and build with `./build.sh --baked` so every app is covered. If an icon still looks stock, you may have set a custom icon for it in the launcher; reset it.
- **One UI:** Samsung only takes packs through Theme Park, and only reads them when you apply the pack.
- **Pixel Launcher, Nothing, HyperOS, Vivo:** don't support third-party icon packs, so nothing to do.

Technical notes on how each launcher loads packs are in [notes/](notes/).
