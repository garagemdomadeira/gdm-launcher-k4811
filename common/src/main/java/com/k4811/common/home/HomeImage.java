package com.k4811.common.home;

import com.k4811.common.config.LauncherConfig;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.k4811.common.system.SystemProps;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Picture of the "Image" home module: one of the images the driver copied into {@code K4811/}
 * on internal storage (subfolders too, except {@code K4811/radio}, which holds the station art).
 * Only its path is saved; if the file goes away, the module asks for another one.
 */
public final class HomeImage {
    private HomeImage() {
    }

    private static final String TAG = "K4811Home";
    private static final String PROP_INTERNAL_PATH = "ro.nwd.internal.path";
    private static final String DEFAULT_INTERNAL_PATH = "/mnt/media_rw/sdcard";
    private static final String FOLDER = "K4811";
    /** Station art lives here; it isn't offered as a home picture. */
    private static final String SKIP = "radio";
    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};
    private static final int MAX_DEPTH = 3;

    /** The {@code K4811} folders searched: NWD internal storage, then Android internal storage. */
    public static List<File> roots() {
        Set<String> seen = new LinkedHashSet<>();
        List<File> out = new ArrayList<>();
        String internal = SystemProps.get(PROP_INTERNAL_PATH, "");
        File ext = Environment.getExternalStorageDirectory();
        File[] candidates = {
            new File(internal.isEmpty() ? DEFAULT_INTERNAL_PATH : internal, FOLDER),
            ext == null ? null : new File(ext, FOLDER),
        };
        for (File dir : candidates) {
            if (dir != null && seen.add(canonical(dir))) out.add(dir);
        }
        return out;
    }

    /** Every image under the roots, sorted by name. Does I/O: call off the main thread. */
    public static List<File> list() {
        Set<String> seen = new LinkedHashSet<>();
        List<File> out = new ArrayList<>();
        for (File root : roots()) collect(root, 0, seen, out);
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private static void collect(File dir, int depth, Set<String> seen, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                boolean skip = depth == 0 && f.getName().equalsIgnoreCase(SKIP);
                if (!skip && depth < MAX_DEPTH) collect(f, depth + 1, seen, out);
            } else if (isImage(f) && seen.add(canonical(f))) {
                out.add(f);
            }
        }
    }

    /** The chosen image, or null if none was chosen. It may no longer exist. */
    public static File file(Context c) {
        String path = LauncherConfig.homeImage(c);
        return path == null ? null : new File(path);
    }

    public static boolean exists(Context c) {
        File f = file(c);
        return f != null && f.isFile();
    }

    public static void choose(Context c, File f) {
        LauncherConfig.setHomeImage(c, f.getAbsolutePath());
    }

    // ---- Background image of the home screen (same folders, a separate choice; optional) ----

    /** The chosen background image, or null if none was chosen. It may no longer exist. */
    public static File fileBg(Context c) {
        String path = LauncherConfig.homeBgImage(c);
        return path == null ? null : new File(path);
    }

    public static boolean existsBg(Context c) {
        File f = fileBg(c);
        return f != null && f.isFile();
    }

    public static void chooseBg(Context c, File f) {
        LauncherConfig.setHomeBgImage(c, f.getAbsolutePath());
    }

    /** Clears the background: the home screen falls back to the theme colour. */
    public static void clearBg(Context c) {
        LauncherConfig.setHomeBgImage(c, null);
    }

    /** Name shown in Settings: the path inside {@code K4811/}, e.g. {@code fotos/carro.jpg}. */
    public static String displayName(File f) {
        String path = f.getAbsolutePath();
        for (File root : roots()) {
            String prefix = root.getAbsolutePath() + File.separator;
            if (path.startsWith(prefix)) return path.substring(prefix.length());
        }
        return f.getName();
    }

    /** {@code f} decoded for a {@code widthPx}×{@code heightPx} area (it's cropped to fill); null on failure. */
    public static Bitmap load(File f, int widthPx, int heightPx) {
        if (f == null || !f.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (bounds.outWidth / (opts.inSampleSize * 2) >= widthPx
                && bounds.outHeight / (opts.inSampleSize * 2) >= heightPx) {
                opts.inSampleSize *= 2;
            }
            return BitmapFactory.decodeFile(f.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "Can't load " + f, e);
            return null;
        }
    }

    private static boolean isImage(File f) {
        String name = f.getName().toLowerCase(Locale.US);
        for (String ext : EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (Exception e) {
            return f.getAbsolutePath();
        }
    }
}
