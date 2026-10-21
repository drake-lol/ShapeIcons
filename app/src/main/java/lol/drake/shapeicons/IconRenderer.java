package lol.drake.shapeicons;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/** Composites background shape + foreground glyph into a bitmap, the same two layers the static icon_* drawables use. */
final class IconRenderer {
    private static Map<String, String> glyphByPackage;   // "com.android.chrome" -> "fg_browser", parsed from appfilter.xml

    private IconRenderer() { }

    static Bitmap render(Context c, String pkg, String shape, float sizeScale, int px) {
        Resources r = c.getResources();
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        draw(cv, drawable(c, "shape_" + shape), px, 1f);
        String fg = glyphs(c).get(pkg);
        if (fg != null) {
            draw(cv, drawable(c, fg), px, sizeScale);                      // our glyph, scaled by the size option
        } else {
            try {                                                             // not in the pack: the app's own icon on the shape
                draw(cv, c.getPackageManager().getApplicationIcon(pkg), px, 0.5f * sizeScale);
            } catch (PackageManager.NameNotFoundException e) {
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

    private static Drawable drawable(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        return id == 0 ? null : c.getDrawable(id).mutate();
    }

    private static synchronized Map<String, String> glyphs(Context c) {
        if (glyphByPackage != null) return glyphByPackage;
        Map<String, String> m = new HashMap<>();
        try (XmlResourceParser p = c.getResources().getXml(R.xml.appfilter)) {
            for (int e = p.getEventType(); e != XmlResourceParser.END_DOCUMENT; e = p.next()) {
                if (e != XmlResourceParser.START_TAG || !p.getName().equals("item")) continue;
                String comp = p.getAttributeValue(null, "component");           // ComponentInfo{pkg/activity}
                String icon = p.getAttributeValue(null, "drawable");            // icon_browser
                if (comp == null || icon == null) continue;
                int a = comp.indexOf('{'), b = comp.indexOf('/');
                if (a >= 0 && b > a) m.put(comp.substring(a + 1, b), "fg_" + icon.substring("icon_".length()));
            }
        } catch (Exception ignored) { }
        return glyphByPackage = m;
    }
}
