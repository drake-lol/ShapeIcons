package lol.drake.shapeicons;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/** Composites background shape + foreground glyph into a bitmap, the same two layers the static icon_* drawables use. */
final class IconRenderer {
    private static Map<String, String> glyphByPackage;   // "com.android.chrome" -> "fg_browser", parsed from appfilter.xml

    private IconRenderer() { }

    /** Smart Launcher's "main action" ids (qm8 enum in its dex): dialer, music, browser, gallery, message, camera, email. */
    private static final String[] ACTION_GLYPHS = {"fg_phone", "fg_music", "fg_browser", "fg_gallery", "fg_messages", "fg_camera", "fg_mail"};

    static Bitmap render(Context c, String pkg, String shape, float sizeScale, int px) {
        return render(c, pkg, -1, null, shape, sizeScale, px);
    }

    /**
     * @param action Smart Launcher main-action id, or -1
     * @param input  the app's own icon as the launcher drew it (used when the pack has no glyph), or null
     */
    static Bitmap render(Context c, String pkg, int action, Bitmap input, String shape, float sizeScale, int px) {
        return render(c, pkg, null, action, input, shape, sizeScale, px);
    }

    static Bitmap render(Context c, String pkg, String activity, int action, Bitmap input, String shape, float sizeScale, int px) {
        return render(c, pkg, activity, action, input, shape, sizeScale, px, 0);
    }

    /** @param colorMode 0 = the pack's own colours, 1 = Material You (system palette) */
    static Bitmap render(Context c, String pkg, String activity, int action, Bitmap input, String shape, float sizeScale, int px, int colorMode) {
        return render(c, pkg, activity, action, input, shape, sizeScale, px, colorMode, false);
    }

    /** @param swap draw the background in the glyph colour and the glyph in the background colour */
    static Bitmap render(Context c, String pkg, String activity, int action, Bitmap input, String shape, float sizeScale, int px, int colorMode, boolean swap) {
        if (c == null || px <= 0 || px > 1024) return null;
        final MaterialYou my = colorMode == 1 ? MaterialYou.current(c) : null;   // null on Android 11- : falls back to the original look
        final int swapBg = my != null ? my.glyph : 0, swapGlyph = my != null ? my.background : 0;
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Drawable shapeDrawable = drawable(c, "shape_" + shape);
        String fg = pkg == null ? null : glyphs(c).get(pkg);
        if (fg == null && action >= 0 && action < ACTION_GLYPHS.length) fg = ACTION_GLYPHS[action];
        // colours: Material You palette, or the pack's own (background colour + this glyph's colour); swap flips them
        int bgColor = 0, glyphColor = 0;
        boolean tinted = false;
        if (my != null) {
            bgColor = swap ? swapBg : my.background; glyphColor = swap ? swapGlyph : my.glyph; tinted = true;
        } else if (swap && fg != null) {
            int own = color(c, "glyph_" + fg.substring("fg_".length()));
            int base = color(c, "icon_bg");
            if (own != 0 && base != 0) { bgColor = own; glyphColor = base; tinted = true; }
        }
        if (tinted && shapeDrawable != null && !(shapeDrawable instanceof android.graphics.drawable.BitmapDrawable)) {
            shapeDrawable.setTint(bgColor);                                       // vector shapes take the colour; PNG shapes keep their look
        }
        draw(cv, shapeDrawable, px, 1f);
        if (fg != null) {
            Drawable glyph = drawable(c, fg);
            if (tinted && glyph != null) glyph.setTint(glyphColor);
            draw(cv, glyph, px, sizeScale);                                // our glyph, scaled by the size option
        } else if (input != null) {                                           // not in the pack: the launcher's icon on the shape
            draw(cv, new android.graphics.drawable.BitmapDrawable(c.getResources(), input), px, 0.5f * sizeScale);
        } else {
            try {
                android.content.pm.PackageManager pm = c.getPackageManager();
                Drawable d = null;
                if (activity != null && !activity.isEmpty()) {
                    try { d = pm.getActivityIcon(new android.content.ComponentName(pkg, activity)); } catch (Exception ignored) { }
                }
                if (d == null) d = pm.getApplicationIcon(pkg);
                if (d instanceof android.graphics.drawable.AdaptiveIconDrawable) {
                    // draw the layers ourselves (not through the icon's own mask) and cut them with the pack's shape
                    android.graphics.drawable.AdaptiveIconDrawable aid = (android.graphics.drawable.AdaptiveIconDrawable) d;
                    Bitmap layers = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                    Canvas lc = new Canvas(layers);
                    int off = Math.round(px * 0.1675f);                         // layers are 108dp, the visible window 72dp
                    Drawable mono = (my != null && Build.VERSION.SDK_INT >= 33) ? aid.getMonochrome() : null;
                    Drawable[] stack = mono != null ? new Drawable[]{new android.graphics.drawable.ColorDrawable(swap ? swapBg : my.background), mono}
                                                    : new Drawable[]{aid.getBackground(), aid.getForeground()};
                    if (mono != null) mono.mutate().setTint(swap ? swapGlyph : my.glyph);              // the app's own single-colour layer, in the theme colour
                    for (Drawable l : stack) {
                        if (l == null) continue;
                        l.setBounds(-off, -off, px + off, px + off);
                        l.draw(lc);
                    }
                    Bitmap mask = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                    draw(new Canvas(mask), drawable(c, "shape_" + shape), px, 1f);
                    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
                    lc.drawBitmap(mask, 0, 0, paint);
                    cv.drawBitmap(layers, 0, 0, null);
                } else {
                    draw(cv, d, px, 0.58f * sizeScale);                         // legacy icon: on the dark shape
                }
            } catch (Exception e) {
                return null;
            }
        }
        return bmp;
    }

    private static void draw(Canvas cv, Drawable d, int px, float scale) {
        if (d == null) return;
        int s = Math.round(px * scale), o = (px - s) / 2;
        d.setBounds(o, o, o + s, o + s);
        d.draw(cv);
    }

    private static int color(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "color", c.getPackageName());
        return id == 0 ? 0 : c.getColor(id);
    }

    private static Drawable drawable(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        if (id == 0) return null;
        Drawable d = c.getDrawable(id);
        return d == null ? null : d.mutate();
    }

    private static synchronized Map<String, String> glyphs(Context c) {
        if (glyphByPackage != null) return glyphByPackage;
        Map<String, String> m = new HashMap<>();
        try (XmlResourceParser p = c.getResources().getXml(R.xml.appfilter)) {
            for (int e = p.getEventType(); e != XmlResourceParser.END_DOCUMENT; e = p.next()) {
                if (e != XmlResourceParser.START_TAG || !p.getName().equals("item")) continue;
                String comp = p.getAttributeValue(null, "component");           // ComponentInfo{pkg/activity}
                String icon = p.getAttributeValue(null, "drawable");            // icon_browser
                if (comp == null || icon == null || !icon.startsWith("icon_")) continue;   // skip baked auto_* entries
                int a = comp.indexOf('{'), b = comp.indexOf('/');
                if (a >= 0 && b > a) m.put(comp.substring(a + 1, b), "fg_" + icon.substring("icon_".length()));
            }
        } catch (Exception ignored) { }
        return glyphByPackage = m;
    }
}
