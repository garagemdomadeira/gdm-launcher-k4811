package com.k4811.audioset.eq;

import android.content.Context;

import com.k4811.common.system.Storages;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Os EQs salvos pelo usuário, no mesmo formato e nas mesmas pastas do DspAdjust3 original, para os
 * dois apps enxergarem os mesmos arquivos:
 *
 * <ul>
 *   <li>{@code <config>/app/eq_customize/<nome>}: Properties com {@code key_7604_frequence},
 *       {@code key_7735_gain} (ganhos já no valor do quadro) e {@code key_7735_qvalue}, 32 números
 *       separados por vírgula (o {@code saveCustomizeEqDataFile} original);</li>
 *   <li>{@code <pen drive>/EQ_Setup/}: exportar copia os arquivos para lá e importar copia de lá
 *       (o {@code Utils.EQ_Setup} original).</li>
 * </ul>
 *
 * Se a pasta da NWD não puder ser criada (emulador, app instalado à parte), usa a do próprio app.
 */
public final class CustomEqFiles {

    public static final String USB_DIR = "EQ_Setup";
    /** Só letras, números, "_" e "-", como o original pede ("only letters and digits"). */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    private final Context context;
    private final ArmDsp arm;

    public CustomEqFiles(Context context, ArmDsp arm) {
        this.context = context.getApplicationContext();
        this.arm = arm;
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** A pasta eq_customize em uso (criada se preciso). */
    public File dir(String configPath) {
        File nwd = new File(configPath, "app/eq_customize");
        if (nwd.isDirectory() || nwd.mkdirs()) {
            open(nwd);
            if (nwd.canWrite()) return nwd;
        }
        File own = new File(context.getFilesDir(), "eq_customize");
        //noinspection ResultOfMethodCallIgnored
        own.mkdirs();
        return own;
    }

    /** Nomes dos EQs salvos, em ordem alfabética. */
    public List<String> list(String configPath) {
        return names(dir(configPath));
    }

    public boolean exists(String configPath, String name) {
        return new File(dir(configPath), name).isFile();
    }

    public boolean save(String configPath, String name, int[] freqs, int[] gainsWire, int[] q) {
        if (!validName(name)) return false;
        File f = new File(dir(configPath), name);
        Properties p = new Properties();
        p.setProperty("key_7604_frequence", join(freqs));
        p.setProperty("key_7735_gain", join(gainsWire));
        p.setProperty("key_7735_qvalue", join(q));
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, null);
        } catch (Exception e) {
            return false;
        }
        open(f);
        return true;
    }

    public ArmDsp.PresetFile read(String configPath, String name) {
        File f = new File(dir(configPath), name);
        return f.isFile() ? arm.readPresetFile(f) : null;
    }

    public boolean delete(String configPath, String name) {
        return new File(dir(configPath), name).delete();
    }

    /** Pen drives montados, na ordem do Android (o primeiro é o usado para exportar). */
    public List<File> usbRoots() {
        return Storages.removable(context);
    }

    /** Copia os EQs para {@code <pen drive>/EQ_Setup}; devolve quantos foram, ou -1 sem pen drive. */
    public int export(String configPath, List<String> names) {
        List<File> roots = usbRoots();
        if (roots.isEmpty()) return -1;
        File target = new File(roots.get(0), USB_DIR);
        if (!target.isDirectory() && !target.mkdirs()) return 0;
        File src = dir(configPath);
        int n = 0;
        for (String name : names) {
            if (copy(new File(src, name), new File(target, name))) n++;
        }
        return n;
    }

    /**
     * Copia para eq_customize os EQs válidos da primeira pasta {@code EQ_Setup} com arquivos;
     * devolve quantos entraram, ou -1 sem pen drive, -2 sem a pasta.
     */
    public int importAll(String configPath) {
        List<File> roots = usbRoots();
        if (roots.isEmpty()) return -1;
        File dst = dir(configPath);
        for (File root : roots) {
            File src = new File(root, USB_DIR);
            List<String> found = names(src);
            if (found.isEmpty()) continue;
            int n = 0;
            for (String name : found) {
                File f = new File(src, name);
                // Só o que for EQ de verdade (32 frequências e ganhos) e com nome aceito.
                if (!validName(name) || arm.readPresetFile(f) == null) continue;
                File out = new File(dst, name);
                if (copy(f, out)) {
                    open(out);
                    n++;
                }
            }
            return n;
        }
        return -2;
    }

    private static List<String> names(File dir) {
        String[] files = dir.list();
        List<String> out = new ArrayList<>();
        if (files == null) return out;
        Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
        for (String n : files) {
            if (new File(dir, n).isFile() && !n.startsWith(".")) out.add(n);
        }
        return out;
    }

    private static boolean copy(File from, File to) {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Leitura e escrita para todos, como o chmod 777 do original (o DspAdjust3 também lê). */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void open(File f) {
        f.setReadable(true, false);
        f.setWritable(true, false);
        if (f.isDirectory()) f.setExecutable(true, false);
    }

    private static String join(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.toString();
    }
}
