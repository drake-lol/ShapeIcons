package lol.drake.shapeicons;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Smart Launcher's "icon generator" contract (reverse-engineered from SL 6.6; same one Icon Pack Studio speaks).
 * Authority must be exactly "<this package>.iconprovider". Per icon, the launcher:
 *   1. writes the app's own icon PNG to        content://AUTH/icon?input=<inputFileName>     (openOutputStream)
 *   2. queries                                 content://AUTH/icon?output=<hash>             selectionArgs[0] = JSON config
 *   3. reads column "fileuri" of the cursor and decodes that PNG (openInputStream).
 * JSON: {"version":"1.0","modelName":"AppConfig|ShortcutConfig","config":{"size":px,"placement":"homescreen|drawer",
 *        "packageName","activityName","userid","action":-1|0..6,"inputFileName",...}}
 */
public class IconGeneratorProvider extends ContentProvider {
    public static final String AUTHORITY = "lol.drake.shapeicons.iconprovider";
    private static final Uri BASE = Uri.parse("content://" + AUTHORITY);

    static final String SL_PACKAGE = "ginlemon.flowerfree";
    static final String ACTION_REQUIRES_UPDATE = "ginlemon.icongenerator.igRequiresUpdate";
    static final String ACTION_AUTHORIZED = "ginlemon.icongenerator.igAuthorizedToUpdate";
    static final String ACTION_NOT_AUTHORIZED = "ginlemon.icongenerator.igNotAuthorizedToUpdate";
    static final String ACTION_READY = "ginlemon.icongenerator.igReadyForUpdatedIcons";

    @Override public boolean onCreate() { return true; }

    private File dir(String name) { return CacheFiles.dir(getContext(), name); }

    private static String safe(String n) throws FileNotFoundException { return CacheFiles.safe(n); }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            String first = uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0);
            if (first.equals("icon") && mode.contains("w")) {                                   // launcher uploads the source icon
                File d = dir("gen_in");
                CacheFiles.prune(d);
                File f = new File(d, safe(uri.getQueryParameter("input")));
                return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
            }
            if (first.equals("result") && uri.getPathSegments().size() == 2 && mode.equals("r")) {   // launcher downloads the result
                return ParcelFileDescriptor.open(new File(dir("gen_out"), safe(uri.getPathSegments().get(1))), ParcelFileDescriptor.MODE_READ_ONLY);
            }
        } catch (FileNotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FileNotFoundException(String.valueOf(e));
        }
        throw new FileNotFoundException(uri.toString());
    }

    /** Decodes a launcher-supplied PNG, refusing anything absurdly large (it is untrusted input). */
    private static Bitmap decodeSafely(File f) {
        if (!f.isFile() || f.length() > 8L * 1024 * 1024) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 2048 || o.outHeight > 2048) return null;
        return BitmapFactory.decodeFile(f.getPath());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            Context c = getContext();
            if (c == null) return null;
            String json = args != null && args.length > 0 ? args[0] : selection;      // `adb shell content query --where` fallback
            if (json == null || json.length() > 16 * 1024) return null;
            JSONObject cfg = new JSONObject(json).getJSONObject("config");
            IconPrefs prefs = new IconPrefs(c);

            int px = Math.max(48, Math.min(512, cfg.optInt("size", 192)));
            Bitmap input = null;
            String in = cfg.optString("inputFileName", "");
            if (in.matches("[A-Za-z0-9._-]{1,128}")) input = decodeSafely(new File(dir("gen_in"), in));

            String shape = cfg.optString("shape", "");                       // optional override (used by tools/bake_installed.py)
            if (!IconPrefs.isShape(c, shape)) shape = prefs.shape();
            String colorOverride = cfg.optString("colors", "");                // optional override (testing / baking): original | material_you
            int colorMode = colorOverride.equals("material_you") ? 1 : colorOverride.equals("original") ? 0 : prefs.colors();
            String swapOverride = cfg.optString("swap", "");
            boolean swap = swapOverride.equals("true") ? true : swapOverride.equals("false") ? false : prefs.swap();
            Bitmap out = IconRenderer.render(c, cfg.optString("packageName", null), cfg.optString("activityName", null),
                    cfg.optInt("action", -1), input, shape, shape.equals(prefs.shape()) ? prefs.sizeScale() : 1f, px, colorMode, swap);
            if (out == null) return null;

            File d = dir("gen_out");
            CacheFiles.prune(d);
            String name = safe(uri.getQueryParameter("output")) + "_v" + prefs.version() + "_" + shape + "_" + MaterialYou.signature(c, colorMode, swap) + ".png";
            File f = new File(d, name), tmp = new File(d, name + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) {                 // write then rename: readers never see half a PNG
                if (!out.compress(Bitmap.CompressFormat.PNG, 100, os)) return null;
            }
            if (!tmp.renameTo(f)) return null;
            MatrixCursor cur = new MatrixCursor(new String[]{"fileuri"});
            cur.addRow(new Object[]{BASE.buildUpon().appendPath("result").appendPath(name).build().toString()});
            return cur;
        } catch (Throwable e) {                                                      // incl. OutOfMemoryError: never crash the provider
            android.util.Log.e("IconGenerator", "query failed", e);
            return null;
        }
    }

    /** Ask Smart Launcher to re-query; it answers igAuthorizedToUpdate if we are its active generator pack. */
    public static void requestUpdate(Context c) {
        c.sendBroadcast(new Intent(ACTION_REQUIRES_UPDATE).setPackage(SL_PACKAGE).putExtra("requester", c.getPackageName()));
    }

    /** Second half of the handshake (see AuthReceiver): tell SL the new icons are ready. */
    public static void notifyReady(Context c) {
        c.sendBroadcast(new Intent(ACTION_READY).setPackage(SL_PACKAGE).putExtra("requester", c.getPackageName()));
    }

    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
