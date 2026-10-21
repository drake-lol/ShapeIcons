#!/usr/bin/env python3
"""Bake icons for the apps installed on connected devices, so launchers that ignore the pack's generic iconback/iconmask
(OxygenOS/ColorOS, Lawnchair, ...) still draw every app in the pack's shape.

    tools/bake_installed.py [--shape flower] [serial ...]     # then: python3 tools/gen_icons.py && build && re-apply the pack

It asks the installed Shape Icons app (its Smart Launcher icon-generator provider, no permission needed) to render each
launchable app's own icon onto the shape, saves the PNGs to tools/auto_icons/ and writes tools/auto_icons/index.json, which
tools/gen_icons.py turns into appfilter entries. Components the pack already maps with a glyph are skipped. Results are
specific to your devices' apps and are not committed (see .gitignore).
"""
import argparse, hashlib, json, pathlib, re, shlex, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "tools/auto_icons"
AUTH = "lol.drake.shapeicons.iconprovider"

def adb(serial, *args, binary=False):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    r = subprocess.run(cmd, capture_output=True)
    return r.stdout if binary else r.stdout.decode(errors="replace")

def launchables(serial):
    out = adb(serial, "shell", "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
    comps = []
    for line in out.replace("\r", "").splitlines():
        line = line.strip()
        if "/" in line and not line.startswith(("Activity", "priority")):
            pkg, act = line.split("/", 1)
            comps.append((pkg, pkg + act if act.startswith(".") else act))
    return comps

def mapped_components():
    txt = (ROOT / "tools/gen_icons.py").read_text()
    return set(re.findall(r'"([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)"', txt))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shape", default=None, help="default: the shape baked into the pack (res/values/shape.xml)")
    ap.add_argument("serials", nargs="*")
    a = ap.parse_args()
    shape = a.shape
    if not shape:
        m = re.search(r">(\w+)<", (ROOT / "app/src/main/res/values/shape.xml").read_text())
        shape = m.group(1) if m else "flower"
    OUT.mkdir(parents=True, exist_ok=True)
    index = json.loads((OUT / "index.json").read_text()) if (OUT / "index.json").exists() else {}
    skip = mapped_components()
    serials = a.serials or [None]
    for serial in serials:
        comps = launchables(serial)
        print(f"{serial or 'default device'}: {len(comps)} launchable components, shape {shape}")
        for pkg, act in comps:
            key = f"{pkg}/{act}"
            if (pkg, act) in skip or pkg == "lol.drake.shapeicons":
                continue
            name = "auto_" + hashlib.sha1(key.encode()).hexdigest()[:10]
            cfg = {"version": "1.0", "modelName": "AppConfig",
                   "config": {"size": 192, "packageName": pkg, "activityName": act, "action": -1, "shape": shape}}
            q = adb(serial, "shell", "content query --uri %s --where %s" % (
                shlex.quote(f"content://{AUTH}/icon?output={name}"), shlex.quote(json.dumps(cfg))))
            m = re.search(r"fileuri=(\S+)", q)
            if not m:
                print("  skip", key); continue
            png = adb(serial, "exec-out", "content read --uri " + shlex.quote(m.group(1)), binary=True)
            if png[:4] != b"\x89PNG":
                print("  bad icon", key); continue
            (OUT / f"{name}.png").write_bytes(png)
            index[key] = name
            print("  baked", key)
    (OUT / "index.json").write_text(json.dumps(index, indent=1, sort_keys=True))
    print(f"{len(index)} baked icons in {OUT}")

main()
