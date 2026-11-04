package com.k4811.audioset.eq;

import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Volume de cada fonte no DSP do ARM ({@code key_audio_gain} do {@code /cache/FactoryConfig.ini}).
 *
 * <p>Com o DSP do ARM (ak7604/ak7735), o serviço da NWD (NewOsSystemVolumeManager) lê esses 14 números só ao ligar e,
 * a cada troca de fonte, manda ao DSP o ganho daquela fonte (0–10). O menu de fábrica não grava esse campo nessas
 * centrais, por isso o rádio ficava sempre no máximo. Confirmado na central: rádio 10 → 6 = log "source:4 gain:6".
 *
 * <p>Posições (1 a 14) na ordem da NWD: navi, bt, dvd, tv, ipod, aux, radio, cdc, mp5, aux2, mhl, g3, canusb, onstar.
 * O serviço usa a 1 para o Android, a 7 para o rádio e a 6 para as fontes sem posição própria (SYNC, BT, AUX).
 * Grava o arquivo no lugar (mesmo dono) e a cópia em {@code backups/} do armazenamento, que a NWD também mantém.
 */
public final class FactoryGains {
    private static final String TAG = "K4811Gains";
    private static final String CACHE = "/cache/FactoryConfig.ini";
    private static final String KEY = "key_audio_gain=";
    private static final String[] DSP = {
        "/sys/devices/virtual/misc/ak7604-dsp/user_dsp_exist",
        "/sys/devices/virtual/misc/ak7735-dsp/user_dsp_exist",
        "/sys/devices/virtual/misc/ak7604-dsp",
        "/sys/devices/virtual/misc/ak7735-dsp",
    };
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static final int COUNT = 14;
    public static final int MIN = 1;
    public static final int MAX = 10;
    /** Posições (1–14) que o serviço da NWD usa. */
    public static final int ANDROID = 1;
    public static final int OTHERS = 6;
    public static final int RADIO = 7;

    private FactoryGains() {
    }

    /**
     * Teste no emulador (prints): {@code adb shell setprop debug.k4811.eq_demo 1} e uma cópia do arquivo de fábrica em
     * {@code /sdcard/K4811/FactoryConfig.ini} (o emulador não tem o DSP no sysfs e o /cache dele é só leitura).
     */
    private static boolean demo() {
        return "1".equals(com.k4811.common.system.SystemProps.get("debug.k4811.eq_demo", ""));
    }

    private static File file() {
        return demo() ? new File(android.os.Environment.getExternalStorageDirectory(), "K4811/FactoryConfig.ini")
            : new File(CACHE);
    }

    /** Só nas centrais com DSP do ARM e o arquivo de fábrica no /cache. */
    public static boolean available() {
        if (!file().isFile()) return false;
        if (demo()) return true;
        for (String p : DSP) {
            if (new File(p).exists()) return true;
        }
        return false;
    }

    /** Os 14 ganhos atuais (10 em todos se o campo não existe), ou null sem arquivo. */
    public static int[] read() {
        String text = readText(file());
        if (text == null) return null;
        int[] g = defaults();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (!l.startsWith(KEY)) continue;
            String[] v = l.substring(KEY.length()).split(",");
            if (v.length != COUNT) break;
            try {
                for (int i = 0; i < COUNT; i++) g[i] = Integer.parseInt(v[i].trim());
            } catch (NumberFormatException e) {
                return defaults();
            }
            break;
        }
        return g;
    }

    public static int[] defaults() {
        int[] g = new int[COUNT];
        for (int i = 0; i < COUNT; i++) g[i] = MAX;
        return g;
    }

    /** Grava os 14 ganhos; true se o /cache foi gravado. Vale no próximo reinício da central. */
    public static boolean write(int[] g) {
        if (g == null || g.length != COUNT) return false;
        StringBuilder b = new StringBuilder(KEY);
        for (int i = 0; i < COUNT; i++) b.append(i > 0 ? "," : "").append(Math.max(MIN, Math.min(MAX, g[i])));
        String line = b.toString();
        boolean ok = writeLine(file(), line);
        File backup = new File(Environment.getExternalStorageDirectory(), "backups/FactoryConfig.ini");
        if (backup.isFile()) writeLine(backup, line);
        Log.i(TAG, line + (ok ? "" : " (não gravou no /cache)"));
        return ok;
    }

    private static boolean writeLine(File f, String line) {
        String text = readText(f);
        if (text == null) return false;
        StringBuilder out = new StringBuilder();
        boolean found = false;
        for (String l : text.split("\n", -1)) {
            if (l.trim().startsWith(KEY)) {
                out.append(line).append(l.endsWith("\r") ? "\r" : "");
                found = true;
            } else {
                out.append(l);
            }
            out.append('\n');
        }
        out.setLength(out.length() - 1);
        if (!found) out.append(text.endsWith("\n") || text.isEmpty() ? "" : "\n").append(line).append('\n');
        // no lugar (sem apagar e renomear), para manter o dono e as permissões do arquivo da NWD
        try (OutputStream o = new FileOutputStream(f, false)) {
            o.write(out.toString().getBytes(UTF8));
            return true;
        } catch (Exception e) {
            Log.w(TAG, f + ": " + e.getMessage());
            return false;
        }
    }

    private static String readText(File f) {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), UTF8);
        } catch (Exception e) {
            return null;
        }
    }
}
