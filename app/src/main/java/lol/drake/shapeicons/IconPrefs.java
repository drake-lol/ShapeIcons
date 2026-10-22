package lol.drake.shapeicons;

import android.content.Context;
import android.content.SharedPreferences;

/** The user's choices from the dashboard. {@link #version()} bumps on every change so cached renders and launchers can tell. */
public final class IconPrefs {
    /** Every shape the pack ships: the vector ones plus any PNG dropped into shapes/ (written by tools/gen_icons.py). */
    public static String[] shapes(Context c) { return c.getResources().getStringArray(R.array.shapes); }

    public static boolean isShape(Context c, String s) { return s != null && java.util.Arrays.asList(shapes(c)).contains(s); }
    public static final String[] COLOR_NAMES = {"Original", "Material You"};
    public static final String[] SIZE_NAMES = {"Small", "Medium", "Large"};
    public static final float[] SIZE_SCALES = {0.8f, 1.0f, 1.2f};

    private final SharedPreferences sp;
    private final Context ctx;

    public IconPrefs(Context c) { ctx = c.getApplicationContext(); sp = c.getApplicationContext().getSharedPreferences("icon_pack", Context.MODE_PRIVATE); }

    public String shape() {
        String s = sp.getString("shape", "circle");
        return isShape(ctx, s) ? s : shapes(ctx)[0];
    }
    public int size() { return sp.getInt("size", 1); }
    public float sizeScale() { return SIZE_SCALES[Math.max(0, Math.min(size(), SIZE_SCALES.length - 1))]; }
    /** 0 = the pack's own colours, 1 = Material You (colours taken from the system palette when the icon is drawn). */
    public int colors() { return Math.max(0, Math.min(sp.getInt("colors", 0), COLOR_NAMES.length - 1)); }
    public int version() { return sp.getInt("version", 1); }

    public void set(String shape, int size, int colors) {
        colors = Math.max(0, Math.min(colors, COLOR_NAMES.length - 1));
        if (!isShape(ctx, shape)) shape = shapes(ctx)[0];
        size = Math.max(0, Math.min(size, SIZE_SCALES.length - 1));
        sp.edit().putString("shape", shape).putInt("size", size).putInt("colors", colors).putInt("version", version() + 1).apply();
    }
}
