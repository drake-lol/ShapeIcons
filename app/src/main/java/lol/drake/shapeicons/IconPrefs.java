package lol.drake.shapeicons;

import android.content.Context;
import android.content.SharedPreferences;

/** The user's choices from the dashboard. {@link #version()} bumps on every change so cached renders and launchers can tell. */
public final class IconPrefs {
    public static final String[] SHAPES = {"circle", "squircle", "flower", "hexagon", "diamond"};
    public static final String[] SIZE_NAMES = {"Small", "Medium", "Large"};
    public static final float[] SIZE_SCALES = {0.8f, 1.0f, 1.2f};

    private final SharedPreferences sp;

    public IconPrefs(Context c) { sp = c.getApplicationContext().getSharedPreferences("icon_pack", Context.MODE_PRIVATE); }

    public String shape() { return sp.getString("shape", "circle"); }
    public int size() { return sp.getInt("size", 1); }
    public float sizeScale() { return SIZE_SCALES[Math.max(0, Math.min(size(), SIZE_SCALES.length - 1))]; }
    public int version() { return sp.getInt("version", 1); }

    public void set(String shape, int size) {
        sp.edit().putString("shape", shape).putInt("size", size).putInt("version", version() + 1).apply();
    }
}
