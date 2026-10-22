#!/usr/bin/env python3
"""Self-check for the generated resources. Run after tools/gen_icons.py (build.sh does it for you).

    python3 tools/check.py

Fails (exit 1) if anything a launcher would trip over is wrong: appfilter entries pointing at missing drawables,
duplicate components, a broken icon_pack.xml, a shapes list that does not match the files, malformed XML or PNGs."""
import pathlib, re, sys
import xml.etree.ElementTree as ET

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
errors = []

def err(msg): errors.append(msg)

def drawables():
    names = set()
    for d in RES.glob("drawable*"):
        names |= {f.stem for f in d.iterdir() if f.suffix in (".png", ".xml")}
    return names

def parse(path):
    try:
        return ET.parse(path).getroot()
    except ET.ParseError as e:
        err(f"{path.relative_to(RES)}: malformed XML ({e})")

have = drawables()

# every vector / xml resource parses
for f in RES.rglob("*.xml"):
    parse(f)

# PNGs really are PNGs
for f in RES.rglob("*.png"):
    if f.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        err(f"{f.relative_to(RES)}: not a PNG")

# appfilter
root = parse(RES / "xml/appfilter.xml")
seen = {}
if root is not None:
    for el in root:
        if el.tag == "item":
            comp, dr = el.get("component", ""), el.get("drawable", "")
            if not re.fullmatch(r"ComponentInfo\{[\w.$]+/[\w.$]+\}", comp):
                err(f"appfilter: bad component {comp!r}")
            if dr not in have:
                err(f"appfilter: {comp} -> missing drawable {dr!r}")
            if comp in seen:
                err(f"appfilter: duplicate component {comp}")
            seen[comp] = dr
        elif el.tag in ("iconback", "iconmask", "iconupon"):
            for k, v in el.attrib.items():
                if v not in have:
                    err(f"appfilter: <{el.tag}> -> missing drawable {v!r}")

# drawable.xml (the picker list)
root = parse(RES / "xml/drawable.xml")
if root is not None:
    for el in root.iter("item"):
        if el.get("drawable") not in have:
            err(f"drawable.xml: missing drawable {el.get('drawable')!r}")

# icon_pack.xml (OxygenOS / ColorOS index): every array entry has a drawable alias
root = parse(RES / "values/icon_pack.xml")
if root is not None:
    aliases = {el.get("name"): (el.text or "").strip() for el in root.iter("item") if el.get("type") == "drawable"}
    arr = [el.text for el in root.iter("string-array") for el in el.iter("item")]
    for a in arr:
        if a not in aliases:
            err(f"icon_pack.xml: array entry {a!r} has no drawable alias")
    for a, t in aliases.items():
        if t.startswith("@drawable/") and t[len("@drawable/"):] not in have:
            err(f"icon_pack.xml: alias {a} -> missing {t}")
    if len(set(arr)) != len(arr):
        err("icon_pack.xml: duplicate array entries")

# shapes list matches the shape_* resources
root = parse(RES / "values/shapes.xml")
if root is not None:
    listed = [el.text for el in root.iter("item")]
    for s in listed:
        if f"shape_{s}" not in have:
            err(f"shapes.xml: shape {s!r} has no shape_{s} drawable")
    baked = parse(RES / "values/shape.xml")
    if baked is not None and baked.find("string").text not in listed:
        err("shape.xml: baked_shape is not in the shapes list")
    for h in have:
        if h.startswith("shape_") and h not in ("shape_bg",) and h[6:] not in listed:
            err(f"{h}: present but not listed in shapes.xml")

# grayscale_icon_map (themed icons)
root = parse(RES / "xml/grayscale_icon_map.xml")
if root is not None:
    for el in root.iter("icon"):
        t = el.get("drawable", "")
        if not t.startswith("@drawable/") or t[len("@drawable/"):] not in have:
            err(f"grayscale_icon_map.xml: {el.get('package')} -> missing {t}")

# manifest declares the discovery filters launchers look for
man = (RES.parent / "AndroidManifest.xml").read_text()
for needed in ("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME",
               "lol.drake.shapeicons.iconprovider", "ginlemon.smartlauncher.ICONPROVIDER", "app.lawnchair.icons.THEMED_ICON"):
    if needed not in man:
        err(f"AndroidManifest.xml: missing {needed}")

if errors:
    print("\n".join("FAIL " + e for e in errors))
    sys.exit(1)
print(f"ok: {len(seen)} appfilter entries, {len(have)} drawables, all references resolve")
