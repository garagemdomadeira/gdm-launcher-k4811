package com.k4811.launcher.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.Log;

import com.k4811.common.system.Storages;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ícones trocados pela pessoa: {@code GDM-icones/<pacote>@<Classe>.png} (uma tela do app: apps como as Ferramentas
 * da NWD têm várias na gaveta) ou {@code GDM-icones/<pacote>.png} (o app todo), na memória interna (fica na memória
 * da central) ou na raiz de um pen drive. Valem na gaveta, na dock e no seletor de apps, com o formato escolhido em {@link IconStyle}. Trocar pelo
 * toque longo do app na gaveta copia o PNG escolhido para a pasta da memória interna; "ícone original" apaga a cópia.
 */
public final class CustomIcons {
    private static final String TAG = "GdmIcons";
    public static final String DIR = "GDM-icones";
    /** Ícone maior que isso é reduzido ao carregar (os da gaveta têm ~85 px). */
    private static final int MAX_PX = 256;
    private static final Map<String, Bitmap> CACHE = new HashMap<>();
    private static final Map<String, Long> CACHE_TIME = new HashMap<>();
    /** Nome de arquivo de ícone aplicado: "com.android.settings.png". */
    private static final java.util.regex.Pattern PACKAGE_FILE =
        java.util.regex.Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+(@[a-z0-9_.$]+)?\\.png");

    private CustomIcons() {
    }

    /** A pasta da memória interna, onde ficam as trocas feitas pela pessoa. */
    public static File home() {
        return new File(Environment.getExternalStorageDirectory(), DIR);
    }

    private static List<File> dirs(Context c) {
        List<File> out = new ArrayList<>();
        out.add(home());
        for (File r : Storages.removable(c)) out.add(new File(r, DIR));
        return out;
    }

    /** O ícone trocado deste pacote, ou null. */
    public static Drawable get(Context c, String pkg) {
        return get(c, pkg, null);
    }

    /** O ícone trocado desta tela ({@code cls}, pode ser null) ou, se não houver, do pacote; null se nenhum. */
    public static Drawable get(Context c, String pkg, String cls) {
        if (pkg == null) return null;
        String[] names = cls == null ? new String[] {pkg} : new String[] {key(pkg, cls), pkg};
        for (String n : names) {
            for (File d : dirs(c)) {
                File f = new File(d, n + ".png");
                if (!f.isFile()) continue;
                Bitmap b = load(f);
                if (b != null) return new BitmapDrawable(c.getResources(), b);
            }
        }
        return null;
    }

    /** "pacote@Classe" (classe completa, como o Android a mostra). */
    public static String key(String pkg, String cls) {
        if (cls == null) return pkg;
        return pkg + "@" + (cls.startsWith(".") ? pkg + cls : cls);
    }

    /** Marca que muda quando a pasta muda: a tela inicial recarrega a dock. */
    public static long stamp() {
        File[] list = home().listFiles();
        long s = home().lastModified();
        if (list != null) {
            for (File f : list) s = s * 31 + f.lastModified() + f.getName().hashCode();
        }
        return s;
    }

    /** Os PNG que dá para escolher: a pasta GDM-icones, a dos pen drives e a de downloads. */
    public static List<File> candidates(Context c) {
        List<File> dirs = dirs(c);
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        for (File r : Storages.removable(c)) dirs.add(r);
        List<File> out = new ArrayList<>();
        for (File d : dirs) {
            File[] list = d.listFiles();
            if (list == null) continue;
            for (File f : list) {
                String n = f.getName().toLowerCase(Locale.US);
                if (!f.isFile() || !(n.endsWith(".png") || n.endsWith(".webp")) || f.length() >= 4 * 1024 * 1024) continue;
                // as cópias já aplicadas (<pacote>.png) não entram na lista: só os ícones baixados
                if (PACKAGE_FILE.matcher(n).matches()) continue;
                out.add(f);
            }
        }
        return out;
    }

    /** Usa {@code src} como ícone de {@code key} (pacote ou {@link #key}); false se não deu. */
    public static boolean set(String pkg, File src) {
        File dir = home();
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        File dst = new File(dir, pkg + ".png");
        if (src.getAbsolutePath().equals(dst.getAbsolutePath())) return true;
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "copiar " + src + ": " + e.getMessage());
            return false;
        }
    }

    /** Volta ao ícone do app: apaga a troca desta tela e a do pacote (memória interna). */
    public static void clear(String pkg, String cls) {
        //noinspection ResultOfMethodCallIgnored
        new File(home(), key(pkg, cls) + ".png").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(home(), pkg + ".png").delete();
    }

    public static boolean has(String pkg, String cls) {
        return new File(home(), key(pkg, cls) + ".png").isFile() || new File(home(), pkg + ".png").isFile();
    }

    /** Lê o PNG reduzido a no máximo {@link #MAX_PX}; guarda enquanto o arquivo não muda. */
    public static Bitmap load(File f) {
        String key = f.getAbsolutePath();
        Long t = CACHE_TIME.get(key);
        if (t != null && t == f.lastModified()) {
            Bitmap b = CACHE.get(key);
            if (b != null && !b.isRecycled()) return b;
        }
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(key, o);
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_PX) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(key, o);
            if (b == null) return null;
            CACHE.put(key, b);
            CACHE_TIME.put(key, f.lastModified());
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
