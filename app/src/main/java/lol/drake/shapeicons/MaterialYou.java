package lol.drake.shapeicons;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

/** Material You colours for icons: read from the system palette each time an icon is drawn (Android 12+). */
final class MaterialYou {
    final int background, glyph;

    private MaterialYou(int background, int glyph) { this.background = background; this.glyph = glyph; }

    /** The palette for the current wallpaper and light/dark mode, or null when it is not available (Android 11 and older). */
    static MaterialYou current(Context c) {
        if (Build.VERSION.SDK_INT < 31) return null;
        try {
            boolean dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            return dark
                    ? new MaterialYou(c.getColor(android.R.color.system_accent1_800), c.getColor(android.R.color.system_accent1_100))
                    : new MaterialYou(c.getColor(android.R.color.system_accent1_100), c.getColor(android.R.color.system_accent1_700));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Short value that changes whenever the palette changes, used in cache file names (0 = not themed). */
    static String signature(Context c, int colorMode, boolean swap) {
        MaterialYou m = colorMode == 1 ? current(c) : null;
        return (m == null ? "0" : Integer.toHexString(m.background * 31 + m.glyph)) + (swap ? "s" : "");
    }
}
