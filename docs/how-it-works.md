# How it works

## The pack
Every icon is **a shape plus a glyph**. The generator (`tools/gen_icons.py`) draws the glyph (camera, phone, ...) and the
shape from small tables, then writes everything a launcher needs:

- `appfilter.xml`: "this app uses this icon". The standard format almost every launcher reads.
- `drawable.xml`: the list of icons for pickers.
- `icon_pack.xml`: an extra index that OxygenOS/ColorOS needs.
- the icons themselves as PNGs, already containing the shape.

The shape is baked into the PNGs, so launchers can't re-mask them. That is why the same pack looks identical everywhere.

## Why only Smart Launcher is live
Launchers read icons straight out of the installed pack and remember them. An app can't change its own installed icons
at runtime, so for most launchers a new shape means **rebuilding the pack** and re-applying it.

Smart Launcher is different: it can ask a pack for an icon whenever it wants, through a *content provider*.
`IconGeneratorProvider` answers those requests: Smart Launcher sends the app's own icon, we draw it onto the chosen shape
and send back a PNG. When you change shape in the app we nudge Smart Launcher (a broadcast) and it asks again.

```
Smart Launcher --(app icon + settings)--> IconGeneratorProvider --(PNG in your shape)--> Smart Launcher
                                                  ^
                                          shape / size you chose
```

There is also `DynamicIconProvider` (`content://lol.drake.shapeicons.provider/icon/<package>`) which the app's own
preview uses. No launcher uses it yet; it is there for a launcher (or fork) that wants a simple API.

## Apps the pack doesn't list
- Smart Launcher: we draw the app's own icon onto the shape automatically.
- Other launchers: `tools/bake_installed.py` does the same ahead of time and adds those icons to the pack.

## Safety of the providers
They only ever return PNGs of icons, take no secrets, validate every name and size they are given, cap cache size, and
never crash on bad input (errors return "no icon").
