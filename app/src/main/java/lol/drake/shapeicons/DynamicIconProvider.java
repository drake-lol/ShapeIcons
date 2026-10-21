package lol.drake.shapeicons;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;

/**
 * The dynamic half of the pack. A launcher that speaks it asks
 *
 *   content://lol.drake.shapeicons.provider/icon/<package>[?size=<px>]   -> PNG of that app's icon in the CURRENT shape/size
 *   content://lol.drake.shapeicons.provider/state                        -> cursor(shape, size, version) to detect changes
 *
 * and re-queries when it sees {@link #ACTION_CHANGED} or a ContentObserver notification on the provider URI.
 * Launchers that don't know this scheme fall back to the static res/xml/appfilter.xml.
 */
public class DynamicIconProvider extends ContentProvider {
    public static final String AUTHORITY = "lol.drake.shapeicons.provider";
    public static final Uri BASE = Uri.parse("content://" + AUTHORITY);
    public static final String ACTION_CHANGED = "lol.drake.shapeicons.ICON_PACK_CHANGED";

    public static Uri iconUri(String pkg, int px) { return BASE.buildUpon().appendPath("icon").appendPath(pkg).appendQueryParameter("size", "" + px).build(); }

    /** Saves the choice and tells everyone listening (observers + broadcast) that cached icons are stale. */
    public static void apply(Context c, String shape, int size) {
        new IconPrefs(c).set(shape, size);
        c.getContentResolver().notifyChange(BASE, null);
        c.sendBroadcast(new Intent(ACTION_CHANGED).putExtra("shape", shape).putExtra("size", size));  // implicit: reaches runtime receivers
        IconGeneratorProvider.requestUpdate(c);                                                        // Smart Launcher handshake
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 2 || !seg.get(0).equals("icon") || !mode.equals("r")) throw new FileNotFoundException(uri.toString());
        String pkg = seg.get(1);
        int px = 192;
        try { px = Math.max(48, Math.min(512, Integer.parseInt(uri.getQueryParameter("size")))); } catch (Exception ignored) { }

        Context c = getContext();
        IconPrefs prefs = new IconPrefs(c);
        File dir = new File(c.getCacheDir(), "icons");
        File f = new File(dir, prefs.version() + "_" + px + "_" + pkg + ".png");
        if (!f.exists()) {
            Bitmap b = IconRenderer.render(c, pkg, prefs.shape(), prefs.sizeScale(), px);
            if (b == null) throw new FileNotFoundException("no icon for " + pkg);
            dir.mkdirs();
            pruneOld(dir, prefs.version());
            File tmp = new File(dir, f.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                throw new FileNotFoundException(e.toString());
            }
            tmp.renameTo(f);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static void pruneOld(File dir, int version) {
        File[] all = dir.listFiles();
        if (all == null) return;
        for (File x : all) if (!x.getName().startsWith(version + "_")) x.delete();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        IconPrefs p = new IconPrefs(getContext());
        MatrixCursor cur = new MatrixCursor(new String[]{"shape", "size", "version"});
        cur.addRow(new Object[]{p.shape(), IconPrefs.SIZE_NAMES[p.size()], p.version()});
        return cur;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle b = new Bundle();
        IconPrefs p = new IconPrefs(getContext());
        b.putString("shape", p.shape()); b.putInt("size", p.size()); b.putInt("version", p.version());
        return b;
    }

    @Override public String getType(Uri uri) { return uri.getPathSegments().contains("icon") ? "image/png" : "vnd.android.cursor.item/vnd.lol.drake.shapeicons.state"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
