#!/usr/bin/env python3
"""Generates every resource of the icon pack (run from anywhere: python3 tools/gen_icons.py).

  src/main/res/drawable/shape_<name>.xml  background shapes (108x108 vectors) the DynamicIconProvider picks from at runtime
  src/main/res/drawable/shape_bg.xml      static fallback shape (copy of DEFAULT_SHAPE) used by icon_* and appfilter.xml
  src/main/res/drawable/fg_*.xml          foreground glyphs (stroke icons on a 24 grid, centered in 108x108)
  src/main/res/drawable-nodpi/icon_*.png  what static launchers show: bitmaps of the DEFAULT_SHAPE (--shape) with the glyph.
                                          Bitmaps (not vectors / adaptive icons) so launchers such as Lawnchair draw them as-is and
                                          ignore their own icon shape setting. Needs ImageMagick (`magick`) with librsvg.
  src/main/res/xml/appfilter.xml          component -> icon_* mapping, plus shape_bg as iconback/iconmask for the rest
  src/main/res/drawable/mono_*.xml        single-colour glyphs, and xml/grayscale_icon_map.xml (themed icons, Lawnchair)
  src/main/res/xml/drawable.xml           list of icons for the launcher's icon picker (and the demo grid)

Edit GLYPHS / APPS / SHAPES, rerun, rebuild. Nothing else needs touching.
"""
import argparse, math, pathlib, re, shutil, subprocess, sys, tempfile

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src"
C, R = 54.0, 48.0          # canvas center / shape radius (canvas is 108)
BG_FILL = "#232733"

# ---------- background shapes (all centered at C, fit in radius ~R) -------------------------------------------------
def poly(pts):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + "Z"

def circle():
    return f"M{C-R},{C} a{R},{R} 0 1,0 {2*R},0 a{R},{R} 0 1,0 {-2*R},0Z"

def squircle(n=4.2, steps=160):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        c, s = math.cos(t), math.sin(t)
        pts.append((C + R * math.copysign(abs(c) ** (2 / n), c), C + R * math.copysign(abs(s) ** (2 / n), s)))
    return poly(pts)

def flower(petals=8, a=6.0, steps=320):
    return poly([(C + (R - a + a * math.cos(petals * t)) * math.cos(t), C + (R - a + a * math.cos(petals * t)) * math.sin(t))
                 for t in (2 * math.pi * i / steps for i in range(steps))])

def rounded_polygon(n, radius, rot, k=0.26):
    """Polygon whose corners are rounded over a fraction k of each edge (k=0.5 would round all the way to the midpoints)."""
    c = [(C + radius * math.cos(rot + 2 * math.pi * i / n), C + radius * math.sin(rot + 2 * math.pi * i / n)) for i in range(n)]
    def toward(a, b): return (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)
    d = ""
    for i in range(n):
        enter, leave = toward(c[i], c[i - 1]), toward(c[i], c[(i + 1) % n])
        d += (f"M{enter[0]:.2f},{enter[1]:.2f}" if i == 0 else f" L{enter[0]:.2f},{enter[1]:.2f}")
        d += f" Q{c[i][0]:.2f},{c[i][1]:.2f} {leave[0]:.2f},{leave[1]:.2f}"
    return d + "Z"

SHAPES = {
    "circle": circle(),
    "squircle": squircle(),
    "flower": flower(),
    "hexagon": rounded_polygon(6, 52, -math.pi / 2),
    "diamond": rounded_polygon(4, 54, -math.pi / 2, 0.22),
}

# ---------- foreground glyphs: (color, [(path, kind)]) on a 24x24 grid; kind: s = stroke, f = fill, d = dot -----------
def circ(cx, cy, r):
    return f"M{cx-r},{cy} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0"

GLYPHS = {
    "phone":    ("#5BE3A0", [(f"M{x},{y}h0.01", "d") for x in (7, 12, 17) for y in (6, 10.5, 15, 19.5)]),
    "messages": ("#6CB4FF", [("M4,5h16v11H9l-5,4z", "s"), ("M8.5,10.5h0.01M12,10.5h0.01M15.5,10.5h0.01", "d")]),
    "camera":   ("#FF7A8A", [("M3,8h4l2,-3h6l2,3h4v11H3z", "s"), (circ(12, 13, 3.5), "s")]),
    "gallery":  ("#FFC857", [("M4,4h16v16H4z", "s"), ("M4,17l5,-6l4,4l3,-3l4,5", "s"), (circ(16, 8.5, 1.4), "s")]),
    "browser":  ("#7DD3FC", [(circ(12, 12, 9), "s"), ("M3,12h18", "s"), ("M12,3c3.5,3.5 3.5,14.5 0,18c-3.5,-3.5 -3.5,-14.5 0,-18", "s")]),
    "music":    ("#C4A1FF", [("M9,17V6l10,-2v11", "s"), (circ(7, 17.5, 2.2), "s"), (circ(17, 15.5, 2.2), "s")]),
    "mail":     ("#FF9E7A", [("M3,6h18v12H3z", "s"), ("M3,7l9,7l9,-7", "s")]),
    "calendar": ("#FF6B6B", [("M4,6h16v14H4z", "s"), ("M4,10.5h16", "s"), ("M8,3v4M16,3v4", "s"), ("M8,14h0.01M12,14h0.01M16,14h0.01M8,17h0.01M12,17h0.01", "d")]),
    "clock":    ("#FFD166", [(circ(12, 12, 9), "s"), ("M12,7v5l3.5,2", "s")]),
    "settings": ("#B8C0CC", [(circ(12, 12, 3), "s"), (circ(12, 12, 7), "s")] +
                 [(f"M{12+7*math.cos(a):.2f},{12+7*math.sin(a):.2f}L{12+9.6*math.cos(a):.2f},{12+9.6*math.sin(a):.2f}", "s")
                  for a in (k * math.pi / 4 for k in range(8))]),
    "maps":     ("#5BE3A0", [("M12,21c-4.5,-5.5 -6.5,-8.5 -6.5,-11.5a6.5,6.5 0 0,1 13,0c0,3 -2,6 -6.5,11.5z", "s"), (circ(12, 9.5, 2.2), "s")]),
    "files":    ("#FFC857", [("M3,6h6l2,2.5h10V19H3z", "s")]),
    "video":    ("#FF5D5D", [("M3,6h18v12H3z", "s"), ("M10,9.2l5,2.8l-5,2.8z", "f")]),
    "store":    ("#6CB4FF", [("M5,8h14l-1,12H6z", "s"), ("M9,10V7.5a3,3 0 0,1 6,0V10", "s")]),
    "contacts": ("#7DD3FC", [(circ(12, 8.5, 3.5), "s"), ("M5,20c0,-4.2 3,-6.2 7,-6.2s7,2 7,6.2", "s")]),
    "calc":     ("#C4A1FF", [("M5,3h14v18H5z", "s"), ("M8,7.5h8", "s"), ("M8.5,12h0.01M12,12h0.01M15.5,12h0.01M8.5,16h0.01M12,16h0.01M15.5,16h0.01", "d")]),
    "star":     ("#FFD166", [("M12,3.2l2.7,5.6l6.1,0.8l-4.5,4.3l1.1,6.1L12,17.1l-5.4,2.9l1.1,-6.1L3.2,9.6l6.1,-0.8z", "s")]),
    "pack":     ("#FFFFFF", [(circ(8, 9, 3.6), "s"), ("M13,5.5h6.5v7H13z", "s"), ("M5,14.5l3.5,6l3.5,-6z", "s"), ("M15,16.5h0.01", "d")]),
}

# component -> glyph (well-known Google/AOSP launcher activities; OnePlus/OxygenOS 16 ones verified on a OnePlus 13s; extend freely)
APPS = {
    "phone": ["com.android.contacts/com.android.contacts.DialtactsActivityAlias", "com.google.android.dialer/com.google.android.dialer.extensions.GoogleDialtactsActivity",
              "com.samsung.android.dialer/com.samsung.android.dialer.DialtactsActivity",          # One UI, unverified
              "com.android.dialer/com.android.dialer.main.impl.MainActivity"],
    "messages": ["com.google.android.apps.messaging/com.google.android.apps.messaging.ui.ConversationListActivity",
                 "com.samsung.android.messaging/com.samsung.android.messaging.ui.view.main.WithActivity"],   # One UI, unverified
    "camera": ["com.oplus.camera/com.oplus.camera.Camera", "com.google.android.GoogleCamera/com.android.camera.CameraLauncher", "com.android.camera2/com.android.camera.CameraLauncher",
               "com.sec.android.app.camera/com.sec.android.app.camera.Camera"],                              # One UI, verified on a Fold7
    "gallery": ["com.oneplus.gallery/com.coloros.gallery3d.app.MainActivity", "com.google.android.apps.photos/com.google.android.apps.photos.home.HomeActivity",
                "com.sec.android.gallery3d/com.samsung.android.gallery.app.activity.GalleryActivity"],        # One UI, verified
    "browser": ["com.heytap.browser/com.android.browser.BrowserActivity", "com.android.chrome/com.google.android.apps.chrome.Main",
                "com.sec.android.app.sbrowser/com.sec.android.app.sbrowser.SBrowserMainActivity"],            # Samsung Internet, verified
    "mail": ["com.google.android.gm/com.google.android.gm.ConversationListActivityGmail",
             "com.samsung.android.email.provider/com.samsung.android.email.ui.messagelist.MessageListActivity"],  # One UI, unverified
    "calendar": ["com.google.android.calendar/com.android.calendar.AllInOneActivity",
                 "com.samsung.android.calendar/com.samsung.android.app.calendar.activity.MainActivity"],       # One UI, unverified
    "clock": ["com.oneplus.deskclock/com.oplus.alarmclock.AlarmClock", "com.google.android.deskclock/com.android.deskclock.DeskClock", "bitpit.launcher/bitpit.launcher.RecentlyInstalled",
              "com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.ClockPackage"],              # One UI, unverified
    "settings": ["com.android.settings/com.android.settings.Settings",
                 "bitpit.launcher/bitpit.launcher.Settings"],                                                   # Niagara's bottom buttons (documented)
    "maps": ["com.google.android.apps.maps/com.google.android.maps.MapsActivity"],
    "files": ["com.oneplus.filemanager/com.oplus.filemanager.main.ui.MainActivity", "com.google.android.apps.nbu.files/com.google.android.apps.nbu.files.home.HomeActivity",
              "com.sec.android.app.myfiles/com.sec.android.app.myfiles.ui.MainActivity"],                     # My Files, verified
    "video": ["com.coloros.video/com.heytap.video.splash.VideoSplashActivity", "com.google.android.youtube/com.google.android.apps.youtube.app.WatchWhileActivity"],
    "store": ["com.heytap.market/com.heytap.market.activity.MainActivity", "com.android.vending/com.android.vending.AssetBrowserActivity",
              "com.sec.android.app.samsungapps/com.sec.android.app.samsungapps.SamsungAppsMainActivity"],     # Galaxy Store, verified
    "contacts": ["com.android.contacts/com.android.contacts.PeopleActivityAlias", "com.google.android.contacts/com.android.contacts.activities.PeopleActivity",
                 "com.samsung.android.app.contacts/com.samsung.android.contacts.contactslist.PeopleActivity"], # One UI, unverified
    "star": ["bitpit.launcher/bitpit.launcher.Pro"],                                                    # Niagara Pro button
    "calc": ["com.oneplus.calculator/com.android.calculator2.Calculator", "com.google.android.calculator/com.android.calculator2.Calculator",
             "com.sec.android.app.popupcalculator/com.sec.android.app.popupcalculator.Calculator"],           # One UI, unverified
    "music": ["com.google.android.apps.youtube.music/com.google.android.apps.youtube.music.activities.MusicActivity"],
}

S = 2.25                                   # glyph scale: 24-unit grid -> 54 of 108
OFF = C - 12 * S

def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)

def vector(body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            f'{body}</vector>\n')

# PNG backgrounds: every shapes/<name>.png (square, any size, transparent corners welcome) becomes one more shape.
CUSTOM_DIR = pathlib.Path(__file__).resolve().parent.parent / "shapes"
CUSTOM = {f.stem.lower(): f for f in sorted(CUSTOM_DIR.glob("*.png")) if re.fullmatch(r"[a-z][a-z0-9_]*", f.stem.lower())}
for bad in CUSTOM_DIR.glob("*.png"):
    if bad.stem.lower() not in CUSTOM:
        print(f"warning: shapes/{bad.name} ignored (name must be letters, digits, underscore, starting with a letter)")
ALL_SHAPES = sorted(set(SHAPES) | set(CUSTOM))

ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
ap.add_argument("--baked", action="store_true",
                help="also add icons baked from YOUR installed apps (tools/auto_icons). Off by default so committed/released builds never "
                     "contain the list of apps on your phone.")
ap.add_argument("--shape", default="flower", choices=ALL_SHAPES,
                help="shape baked into the STATIC fallback (appfilter.xml / icon_*). Theme Park and other snapshot-style "
                     "consumers can't use the dynamic providers, so this is the shape they get.")
_args = ap.parse_args()
DEFAULT_SHAPE = _args.shape
for old in (RES / "main/res/drawable-nodpi").glob("shape_*.png"):
    old.unlink()                                                  # removed custom shapes must not linger
for name, src in CUSTOM.items():
    dst = RES / f"main/res/drawable-nodpi/shape_{name}.png"
    dst.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["magick", str(src), "-resize", "432x432", "-background", "none", "-gravity", "center", "-extent", "432x432", str(dst)], check=True)
icon_svgs = {}
for name, d in SHAPES.items():
    v = vector(f'    <path android:fillColor="{BG_FILL}" android:pathData="{d}"/>\n')
    write(RES / f"main/res/drawable/shape_{name}.xml", v)
    if name == DEFAULT_SHAPE:
        write(RES / "main/res/drawable/shape_bg.xml", v)
if DEFAULT_SHAPE in CUSTOM:
    write(RES / "main/res/drawable/shape_bg.xml", vector(f'    <path android:fillColor="{BG_FILL}" android:pathData="{SHAPES["circle"]}"/>\n'))

for name, (color, parts) in GLYPHS.items():
    body = f'    <group android:translateX="{OFF}" android:translateY="{OFF}" android:scaleX="{S}" android:scaleY="{S}">\n'
    for d, kind in parts:
        if kind == "f":
            body += f'        <path android:fillColor="{color}" android:pathData="{d}"/>\n'
        else:
            w = 3.2 if kind == "d" else 1.7
            body += (f'        <path android:strokeColor="{color}" android:strokeWidth="{w}" android:strokeLineCap="round" '
                     f'android:strokeLineJoin="round" android:pathData="{d}"/>\n')
    body += '    </group>\n'
    write(RES / f"main/res/drawable/fg_{name}.xml", vector(body))
    icon_svgs[name] = (color, parts)

import subprocess, tempfile

def glyph_svg(color, parts):
    body = f'<g transform="translate({OFF} {OFF}) scale({S})">'
    for d, kind in parts:
        if kind == "f":
            body += f'<path fill="{color}" d="{d}"/>'
        else:
            w = 3.2 if kind == "d" else 1.7
            body += f'<path fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round" d="{d}"/>'
    return body + "</g>"

def render_png(name, color, parts, out_path, px=192):
    custom = DEFAULT_SHAPE in CUSTOM
    bg = '<rect width="108" height="108" fill="#000" fill-opacity="0.01"/>' if custom else f'<path fill="{BG_FILL}" d="{SHAPES[DEFAULT_SHAPE]}"/>'
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">'
           f'{bg}{glyph_svg(color, parts)}</svg>')
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
        f.write(svg)
    density = str(72 * px // 108 * 4)
    try:
        if custom:     # PNG background as-is, glyph on top (two steps: magick's parenthesised form drops the background)
            glyph_png = f.name + ".png"
            subprocess.run(["magick", "-background", "none", "-density", density, f.name, "-resize", f"{px}x{px}", glyph_png], check=True)
            subprocess.run(["magick", str(CUSTOM[DEFAULT_SHAPE]), "-background", "none", "-resize", f"{px}x{px}", "-gravity", "center",
                            "-extent", f"{px}x{px}", glyph_png, "-composite", str(out_path)], check=True)
            pathlib.Path(glyph_png).unlink(missing_ok=True)
        else:          # trim: the vector shape fills the bitmap
            subprocess.run(["magick", "-background", "none", "-density", density, f.name, "-trim", "+repage", "-resize", f"{px}x{px}",
                            "-gravity", "center", "-background", "none", "-extent", f"{px}x{px}", str(out_path)], check=True)
    except FileNotFoundError:
        sys.exit("ImageMagick ('magick') is required: install it (e.g. sudo pacman -S imagemagick) and rerun.")
    finally:
        pathlib.Path(f.name).unlink(missing_ok=True)

for name, (color, parts) in icon_svgs.items():
    render_png(name, color, parts, RES / f"main/res/drawable-nodpi/icon_{name}.png")

# Single-colour glyphs + map for launchers that recolour icons themselves (Lawnchair "themed icons", Material You).
for name, (color, parts) in GLYPHS.items():
    body = f'    <group android:translateX="{OFF}" android:translateY="{OFF}" android:scaleX="{S}" android:scaleY="{S}">\n'
    for d, kind in parts:
        if kind == "f":
            body += f'        <path android:fillColor="#FFFFFFFF" android:pathData="{d}"/>\n'
        else:
            w = 3.2 if kind == "d" else 1.7
            body += (f'        <path android:strokeColor="#FFFFFFFF" android:strokeWidth="{w}" android:strokeLineCap="round" '
                     f'android:strokeLineJoin="round" android:pathData="{d}"/>\n')
    write(RES / f"main/res/drawable/mono_{name}.xml", vector(body + '    </group>\n'))
gm = {}
for g, comps in APPS.items():
    for comp in comps:
        gm.setdefault(comp.split("/")[0], g)
write(RES / "main/res/xml/grayscale_icon_map.xml", '<?xml version="1.0" encoding="utf-8"?>\n<icons>\n' +
      "".join(f'    <icon package="{pkg}" drawable="@drawable/mono_{g}"/>\n' for pkg, g in sorted(gm.items())) + '</icons>\n')

write(RES / "main/res/values/glyph_colors.xml", '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' +
      "".join(f'    <color name="glyph_{n}">{c}</color>\n' for n, (c, _) in GLYPHS.items()) + '</resources>\n')
write(RES / "main/res/values/shapes.xml", '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string-array name="shapes">\n' +
      "".join(f"        <item>{n}</item>\n" for n in ALL_SHAPES) + "    </string-array>\n</resources>\n")
write(RES / "main/res/values/shape.xml",
      f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="baked_shape">{DEFAULT_SHAPE}</string>\n</resources>\n')
write(RES / "main/res/values/colors.xml",
      f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="icon_bg">{BG_FILL}</color>\n</resources>\n')

af = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>',
      '    <!-- apps without an entry below: their own icon, scaled, on the pack\'s shape (shape_bg = the swappable asset) -->',
      '    <iconback img1="shape_bg"/>', '    <iconmask img1="shape_bg"/>', '    <scale factor="0.55"/>']
for g, comps in APPS.items():
    for c in comps:
        af.append(f'    <item component="ComponentInfo{{{c}}}" drawable="icon_{g}"/>')
af.append('</resources>')
write(RES / "main/res/xml/appfilter.xml", "\n".join(af) + "\n")

import json, shutil
AUTO = pathlib.Path(__file__).resolve().parent / "auto_icons"
auto_index = json.loads((AUTO / "index.json").read_text()) if (_args.baked and (AUTO / "index.json").exists()) else {}
_out = RES / "main/res/drawable-nodpi"
if not auto_index:
    for f in _out.glob("auto_*.png"):
        f.unlink()
if auto_index:
    out = RES / "main/res/drawable-nodpi"
    for f in out.glob("auto_*.png"):
        f.unlink()
    for key, name in auto_index.items():
        if (AUTO / f"{name}.png").exists():
            shutil.copy(AUTO / f"{name}.png", out / f"{name}.png")
    lines = [f'    <item component="ComponentInfo{{{k}}}" drawable="{n}"/>' for k, n in sorted(auto_index.items()) if (AUTO / f"{n}.png").exists()]
    af_text = (RES / "main/res/xml/appfilter.xml").read_text().replace("</resources>", "    <!-- baked from installed apps by tools/bake_installed.py -->\n" + "\n".join(lines) + "\n</resources>")
    write(RES / "main/res/xml/appfilter.xml", af_text)
    print(f"{len(lines)} baked app icons added")

# Blueprint-style package index (what OxygenOS/ColorOS's icon pack parser reads first): a string-array "icon_pack" of
# package names written with underscores, each backed by a drawable of the same name (aliases here, no file copies).
import re as _re
pkg_target = {}
for g, comps in APPS.items():
    for comp in comps:
        pkg_target.setdefault(comp.split("/")[0], f"icon_{g}")
for key, name in sorted(auto_index.items()):
    if (AUTO / f"{name}.png").exists():
        pkg_target.setdefault(key.split("/")[0], name)
alias_lines, array_lines = [], []
for pkg, target in sorted(pkg_target.items()):
    res_name = pkg.lower().replace(".", "_")
    if _re.fullmatch(r"[a-z][a-z0-9_]*", res_name) and res_name != target:
        alias_lines.append(f'    <item name="{res_name}" type="drawable">@drawable/{target}</item>')
        array_lines.append(f"        <item>{res_name}</item>")
write(RES / "main/res/values/icon_pack.xml",
      '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(alias_lines) +
      '\n    <string-array name="icon_pack">\n' + "\n".join(array_lines) + "\n    </string-array>\n</resources>\n")
print(f"{len(array_lines)} package aliases in icon_pack array")

icons = [n for n in GLYPHS if n != "pack"]
write(RES / "main/res/xml/drawable.xml", '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <category title="All"/>\n' +
      "".join(f'    <item drawable="icon_{n}"/>\n' for n in icons) +
      ('    <category title="Your apps"/>\n' + "".join(f'    <item drawable="{n}"/>\n' for _, n in sorted(auto_index.items()) if (AUTO / f"{n}.png").exists()) if auto_index else "") +
      '</resources>\n')
print(f"{len(ALL_SHAPES)} shapes ({len(CUSTOM)} PNG), {len(GLYPHS)} glyphs, {sum(map(len, APPS.values()))} component mappings")
