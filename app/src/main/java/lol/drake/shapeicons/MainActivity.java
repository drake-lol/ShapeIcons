package lol.drake.shapeicons;

import android.app.Activity;
import android.content.res.XmlResourceParser;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Dashboard: pick shape + size, and watch the icons below change. The grid loads icons through the ContentProvider, exactly as a launcher would. */
public class MainActivity extends Activity {
    private IconPrefs prefs;
    private GridLayout grid;
    private final List<String> packages = new ArrayList<>();
    private final List<Button> shapeButtons = new ArrayList<>(), sizeButtons = new ArrayList<>();
    private final java.util.Set<String> seenIcons = new java.util.HashSet<>();
    private int cell;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new IconPrefs(this);
        int pad = dp(20);
        cell = (getResources().getDisplayMetrics().widthPixels - 2 * pad) / 4;
        loadPackages();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(72), pad, pad);
        root.setBackgroundColor(0xFF12141A);

        root.addView(text("Shape Icons", 28, 0xFFFFFFFF, true));
        root.addView(text("Pick a shape. Smart Launcher updates live. Lawnchair, Nova, Theme Park etc. show the pack's built-in shape (flower; change it with ./build.sh --shape).", 14, 0xFF9AA3B2, false));

        root.addView(text("Shape", 16, 0xFFFFFFFF, true));
        LinearLayout shapes = row();
        for (String s : IconPrefs.shapes(this)) shapes.addView(chip(s, shapeButtons, () -> choose(s, prefs.size())));
        root.addView(scroll(shapes));

        root.addView(text("Icon size", 16, 0xFFFFFFFF, true));
        LinearLayout sizes = row();
        for (int i = 0; i < IconPrefs.SIZE_NAMES.length; i++) { final int sz = i; sizes.addView(chip(IconPrefs.SIZE_NAMES[i], sizeButtons, () -> choose(prefs.shape(), sz))); }
        root.addView(sizes);

        root.addView(text("Apply to a launcher", 16, 0xFFFFFFFF, true));
        root.addView(text("Smart Launcher updates live when you change the shape above. The others show the shape built into the "
                + "pack (" + getString(R.string.baked_shape) + "); change it with tools/apply_shape.sh <shape> on a PC.", 12, 0xFF6B7385, false));
        for (final Launcher l : launchers()) root.addView(applyButton(l));

        grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(0, dp(24), 0, dp(24));
        root.addView(grid);

        root.addView(text("Provider: content://" + DynamicIconProvider.AUTHORITY + "/icon/<package>\nFallback: res/xml/appfilter.xml (static circle).", 12, 0xFF6B7385, false));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        refresh();
    }

    /** One entry per supported launcher: installed if any package is present, applied by the intent it understands. */
    private static final class Launcher {
        final String name; final String[] packages; final Intent intent; final String hint;
        Launcher(String name, String[] packages, Intent intent, String hint) { this.name = name; this.packages = packages; this.intent = intent; this.hint = hint; }
    }

    private List<Launcher> launchers() {
        String me = getPackageName();
        List<Launcher> l = new ArrayList<>();
        l.add(new Launcher("Smart Launcher", new String[]{"ginlemon.flowerfree"},
                new Intent("ginlemon.smartlauncher.setGSLTHEME").putExtra("package", me).setPackage("ginlemon.flowerfree"), "live shape"));
        l.add(new Launcher("Lawnchair", new String[]{"app.lawnchair", "app.lawnchair.nightly", "app.lawnchair.debug"},
                new Intent("app.lawnchair.APPLY_ICONS").putExtra("packageName", me), "built-in shape"));
        l.add(new Launcher("Nova Launcher", new String[]{"com.teslacoilsw.launcher"},
                new Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME").setPackage("com.teslacoilsw.launcher")
                        .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GRID")
                        .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", me), "built-in shape"));
        l.add(new Launcher("Niagara Launcher", new String[]{"bitpit.launcher"},
                new Intent("bitpit.launcher.APPLY_ICONS").setPackage("bitpit.launcher").putExtra("packageName", me), "built-in shape"));
        // no apply intent known for these: open the launcher, the pack is chosen in its own settings
        l.add(new Launcher("Kvaesitso", new String[]{"de.mm20.launcher2.release"},
                getPackageManager().getLaunchIntentForPackage("de.mm20.launcher2.release"), "Settings > Icons"));
        l.add(new Launcher("Microsoft Launcher", new String[]{"com.microsoft.launcher"},
                getPackageManager().getLaunchIntentForPackage("com.microsoft.launcher"), "Settings > Personalization > Icon pack"));
        l.add(new Launcher("Action Launcher", new String[]{"com.actionlauncher.playstore"},
                getPackageManager().getLaunchIntentForPackage("com.actionlauncher.playstore"), "Settings > Icons > Icon pack"));
        l.add(new Launcher("Apex Launcher", new String[]{"com.anddoes.launcher"},
                new Intent("com.anddoes.launcher.SET_THEME").setPackage("com.anddoes.launcher")
                        .putExtra("com.anddoes.launcher.THEME_PACKAGE_NAME", me), "built-in shape"));
        l.add(new Launcher("ADW Launcher", new String[]{"org.adw.launcher", "org.adw.launcher.one"},
                new Intent("org.adw.launcher.SET_THEME").putExtra("org.adw.launcher.theme.NAME", me), "built-in shape"));
        l.add(new Launcher("One UI (Theme Park)", new String[]{"com.samsung.android.themedesigner"},
                getPackageManager().getLaunchIntentForPackage("com.samsung.android.themedesigner"), "pick it under Icon pack"));
        return l;
    }

    private String installedPackage(Launcher l) {
        for (String p : l.packages) {
            try { getPackageManager().getPackageInfo(p, 0); return p; } catch (Exception ignored) { }
        }
        return null;
    }

    private Button applyButton(final Launcher l) {
        final String pkg = installedPackage(l);
        Button bt = new Button(this);
        bt.setAllCaps(false);
        bt.setText(pkg == null ? l.name + "  (not installed)" : l.name + "  \u00b7  " + l.hint);
        bt.setEnabled(pkg != null && l.intent != null);
        bt.setTextColor(pkg == null ? 0xFF6B7385 : 0xFFE5E9F0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(pkg == null ? 0xFF1A1D26 : 0xFF232733);
        bt.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(8);
        bt.setLayoutParams(lp);
        bt.setOnClickListener(v -> {
            try {
                Intent i = new Intent(l.intent);
                if (i.getPackage() == null && i.getComponent() == null && !i.getAction().equals(Intent.ACTION_MAIN)) i.setPackage(pkg);
                startActivity(i);
            } catch (Exception e) {
                android.widget.Toast.makeText(this, l.name + " could not be opened", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        return bt;
    }

    private void choose(String shape, int size) {
        DynamicIconProvider.apply(this, shape, size);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < shapeButtons.size(); i++) mark(shapeButtons.get(i), IconPrefs.shapes(this)[i].equals(prefs.shape()));
        for (int i = 0; i < sizeButtons.size(); i++) mark(sizeButtons.get(i), i == prefs.size());
        final int px = cell, version = prefs.version();
        new Thread(() -> {
            List<Bitmap> bmps = new ArrayList<>();
            for (String pkg : packages) {
                try (InputStream in = getContentResolver().openInputStream(DynamicIconProvider.iconUri(pkg, px))) {
                    bmps.add(BitmapFactory.decodeStream(in));
                } catch (Exception e) { bmps.add(null); }
            }
            runOnUiThread(() -> {
                if (version != prefs.version()) return;      // a newer choice already superseded this load
                grid.removeAllViews();
                for (Bitmap bm : bmps) {
                    if (bm == null) continue;
                    ImageView v = new ImageView(this);
                    v.setImageBitmap(bm);
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = lp.height = cell;
                    grid.addView(v, lp);
                }
            });
        }).start();
    }

    /** Packages with a glyph in the pack, read from appfilter.xml (same source the static fallback uses). */
    private void loadPackages() {
        try (XmlResourceParser p = getResources().getXml(R.xml.appfilter)) {
            for (int e = p.getEventType(); e != XmlResourceParser.END_DOCUMENT; e = p.next()) {
                if (e != XmlResourceParser.START_TAG || !p.getName().equals("item")) continue;
                String comp = p.getAttributeValue(null, "component");
                String pkg = comp.substring(comp.indexOf('{') + 1, comp.indexOf('/'));
                String icon = p.getAttributeValue(null, "drawable");
                if (!icon.startsWith("auto_") && seenIcons.add(icon)) packages.add(pkg);   // baked per-device icons are not shown in the grid          // one grid cell per glyph
            }
        } catch (Exception ignored) { }
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(8), 0, dp(16));
        return l;
    }

    private android.widget.HorizontalScrollView scroll(LinearLayout inner) {
        android.widget.HorizontalScrollView h = new android.widget.HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(inner);
        return h;
    }

    private Button chip(String label, List<Button> into, Runnable onClick) {
        Button bt = new Button(this);
        bt.setText(label.substring(0, 1).toUpperCase() + label.substring(1));
        bt.setAllCaps(false);
        bt.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.rightMargin = dp(8);
        bt.setLayoutParams(lp);
        into.add(bt);
        return bt;
    }

    private void mark(Button bt, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(on ? 0xFF7DD3FC : 0xFF232733);
        bt.setBackground(g);
        bt.setTextColor(on ? 0xFF0B1220 : 0xFFE5E9F0);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setPadding(0, dp(6), 0, dp(4));
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
