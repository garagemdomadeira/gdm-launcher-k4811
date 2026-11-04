package com.k4811.updater;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * O arquivo de versões ({@code ota.json}) publicado na última release do GitHub e o que ele traz de novo para esta
 * central.
 *
 * <p>Formato (gerado por {@code tools/publica_ota.py}):
 * <pre>
 * {"formato":1, "gerado":"2026-11-04T10:00:00Z",
 *  "apps":[{"pacote","nome","versionCode","versionName","url","sha256","tamanho","novidades"}],
 *  "roms":[{"canal","codigo","versao","url","sha256","tamanho","novidades","plataforma"}]}
 * </pre>
 * O {@code ota.json.sig} ao lado é a assinatura RSA/SHA-256 do arquivo com a chave do OTA da GDM ({@link #PUBLIC_KEY}); sem
 * assinatura válida nada é instalado (a chave de teste do AOSP que o recovery aceita é pública).
 */
final class Ota {
    static final String REPO = "guarienteherbert-ux/gdm-k4811-ota";
    static final String MANIFEST_URL = "https://github.com/" + REPO + "/releases/latest/download/ota.json";
    static final String SIG_URL = MANIFEST_URL + ".sig";
    /** Chave pública (X.509/DER em base64) que confere o ota.json. A privada fica em K4811_mod/ota_keys. */
    static final String PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAp7GjFQSnsW4arrQf+lpGcsfZRFlukvq3uXKvukhCgZtd5ljlCWQa11iYfSiZtXRV/6kwYkZeG2PCedo9F3IvNPcg2jNvYAs8OAcUNa2ODVBponNrooP41tgPi+p1278AcYsXKQ/snjWOrEqknXCu7zF4aSg791dkAyXJtUz9/Y5iuLadr+100r9Ru06aSmmp7nkpGzbbuGTmY/+K+16Q04YXC0vCGMbSLF0g1j9qX06eRUhb9GgbwrIftRbcICVPyyg+8wxlDAyM1TfrfPgLFKrNXWrqXr+Xb4v/ZC5ibOoRIObxQvKh4It15M9HpEZFcoWZqHUgHx6DnzYlFZZbYQIDAQAB";

    static final String PREFS = "ota";
    static final String KEY_LAST_CHECK = "last_check";
    static final String KEY_MANIFEST = "manifest";
    static final String KEY_SNOOZE = "snooze_until";
    static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    static final long DAY_MS = 24L * 60 * 60 * 1000;

    static final class App {
        String pkg;
        String name;
        int versionCode;
        String versionName;
        String url;
        String sha256;
        long size;
        String notes;
        /** Versão instalada (versionName) ou null. */
        String installedName;
    }

    static final class Rom {
        String channel;
        int code;
        String version;
        String url;
        String sha256;
        long size;
        String notes;
        /** SHA-256 do certificado de plataforma da ROM (igual ao instalado = atualiza sem apagar). */
        String platform;
    }

    final List<App> apps = new ArrayList<>();
    final List<Rom> roms = new ArrayList<>();
    final String generated;

    private Ota(JSONObject o) throws Exception {
        generated = o.optString("gerado", "");
        JSONArray a = o.optJSONArray("apps");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject j = a.getJSONObject(i);
            App x = new App();
            x.pkg = j.getString("pacote");
            x.name = j.optString("nome", x.pkg);
            x.versionCode = j.getInt("versionCode");
            x.versionName = j.optString("versionName", String.valueOf(x.versionCode));
            x.url = j.getString("url");
            x.sha256 = j.getString("sha256").toLowerCase();
            x.size = j.optLong("tamanho", 0);
            x.notes = j.optString("novidades", "");
            apps.add(x);
        }
        JSONArray r = o.optJSONArray("roms");
        for (int i = 0; r != null && i < r.length(); i++) {
            JSONObject j = r.getJSONObject(i);
            Rom x = new Rom();
            x.channel = j.getString("canal");
            x.code = j.getInt("codigo");
            x.version = j.optString("versao", String.valueOf(x.code));
            x.url = j.getString("url");
            x.sha256 = j.getString("sha256").toLowerCase();
            x.size = j.optLong("tamanho", 0);
            x.notes = j.optString("novidades", "");
            x.platform = j.optString("plataforma", "").toLowerCase();
            roms.add(x);
        }
    }

    /** Confere a assinatura e lê; exceção se não bater. */
    static Ota parse(byte[] json, byte[] sig) throws Exception {
        if (!verify(json, sig)) throw new SecurityException("assinatura do ota.json não confere");
        return new Ota(new JSONObject(new String(json, "UTF-8")));
    }

    static boolean verify(byte[] data, byte[] sig) {
        try {
            PublicKey key = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)));
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(key);
            s.update(data);
            return s.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ o que é novo aqui

    /** Apps instalados nesta central com versão mais nova publicada (este app por último). */
    List<App> pendingApps(Context c) {
        List<App> out = new ArrayList<>();
        App self = null;
        PackageManager pm = c.getPackageManager();
        for (App a : apps) {
            PackageInfo pi;
            try {
                pi = pm.getPackageInfo(a.pkg, 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue; // só atualiza o que a pessoa tem
            }
            if (a.versionCode <= pi.versionCode) continue;
            a.installedName = pi.versionName;
            if (a.pkg.equals(c.getPackageName())) self = a;
            else out.add(a);
        }
        // o launcher perto do fim (fecha a tela inicial), este app no fim (fecha a si mesmo)
        App launcher = null;
        for (App a : out) if (a.pkg.equals(com.k4811.common.Apps.LAUNCHER)) launcher = a;
        if (launcher != null) {
            out.remove(launcher);
            out.add(launcher);
        }
        if (self != null) out.add(self);
        return out;
    }

    /** A ROM mais nova do canal desta central, ou null. */
    Rom pendingRom(Context c) {
        RomInfo info = RomInfo.read();
        if (info.channel == null) return null;
        Rom best = null;
        for (Rom r : roms) {
            if (!r.channel.equals(info.channel) || r.code <= info.code) continue;
            if (best == null || r.code > best.code) best = r;
        }
        return best;
    }

    // ------------------------------------------------------------------ ROM instalada

    /**
     * Canal e número da ROM instalada: {@code k4811_ota_canal} e {@code k4811_ota_rom} no UpdateConfig.ini (a cópia em
     * /data/nwdappconfig, que a NWD refaz do /system a cada OTA, ou a do /system). ROM sem essas linhas = sem
     * atualização de ROM (só apps).
     */
    static final class RomInfo {
        String channel;
        int code;

        static RomInfo read() {
            RomInfo r = new RomInfo();
            for (String path : new String[] {"/data/nwdappconfig/app/UpdateConfig.ini", "/system/config/app/UpdateConfig.ini"}) {
                File f = new File(path);
                if (!f.canRead()) continue;
                try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("k4811_ota_canal=")) r.channel = line.substring(14).trim();
                        else if (line.startsWith("k4811_ota_rom=")) r.code = Integer.parseInt(line.substring(12).trim());
                    }
                } catch (Exception ignored) {
                }
                if (r.channel != null && !r.channel.isEmpty()) return r;
                r.channel = null;
            }
            return r;
        }
    }

    // ------------------------------------------------------------------ preferências

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Último ota.json válido guardado (para perguntar de novo sem rede), ou null. */
    static Ota cached(Context c) {
        try {
            String j = prefs(c).getString(KEY_MANIFEST, null);
            return j == null ? null : new Ota(new JSONObject(j));
        } catch (Exception e) {
            return null;
        }
    }

    static void store(Context c, byte[] json) {
        try {
            prefs(c).edit().putString(KEY_MANIFEST, new String(json, "UTF-8"))
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
        }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    /** SHA-256 do certificado de plataforma da ROM instalada (o do pacote "android"). */
    static String platform(Context c) {
        try {
            @SuppressWarnings("deprecation")
            android.content.pm.Signature[] s = c.getPackageManager()
                .getPackageInfo("android", PackageManager.GET_SIGNATURES).signatures;
            if (s == null || s.length != 1) return null;
            return hex(MessageDigest.getInstance("SHA-256").digest(s[0].toByteArray()));
        } catch (Exception e) {
            return null;
        }
    }
}
