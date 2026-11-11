package com.k4811.updater;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.StatFs;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Instala o que a pessoa confirmou: APKs pelo PackageInstaller (sem tela, como sistema) e a ROM pelo caminho do
 * atualizador da NWD ({@code /cache/update.zip} + MASTER_CLEAR com {@code extra_wipe_data=false}).
 */
final class Installer {
    private static final String TAG = "GdmOta";
    private static final String ACTION_RESULT = "com.k4811.updater.action.INSTALL_RESULT";
    static final File CACHE_ZIP = new File("/cache/update.zip");
    private static final String MARK = "META-INF/com/k4811/platform.sha256";

    private Installer() {
    }

    /** Roda como sistema (instala sem tela e grava a ROM). Fora da ROM (emulador), não. */
    static boolean isSystem() {
        return Process.myUid() == Process.SYSTEM_UID;
    }

    // ------------------------------------------------------------------ apps

    /** Baixa, confere e instala um app. Exceção com o motivo se der errado. */
    static void installApp(Context c, Ota.App a, Net.Progress p) throws Exception {
        File dir = isSystem() ? c.getCacheDir() : c.getExternalCacheDir();
        if (dir == null) dir = c.getCacheDir();
        File apk = new File(dir, a.pkg + "-" + a.versionCode + ".apk");
        Net.download(a.url, apk, a.size, p);
        if (!a.sha256.equals(Ota.sha256(apk))) {
            apk.delete();
            throw new IOException("arquivo corrompido (SHA-256 não confere)");
        }
        checkSameSigner(c, a.pkg, apk);
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                session(c, apk);
            } finally {
                apk.delete();
            }
        } else {
            // Android antigo: a tela de instalar do Android
            apk.setReadable(true, false);
            c.startActivity(new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /** O APK novo tem de ter a mesma assinatura do instalado (senão o Android recusa; melhor dizer antes). */
    @SuppressWarnings("deprecation")
    private static void checkSameSigner(Context c, String pkg, File apk) throws Exception {
        PackageManager pm = c.getPackageManager();
        android.content.pm.PackageInfo now = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
        android.content.pm.PackageInfo neu = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (neu == null || neu.signatures == null || !pkg.equals(neu.packageName)) throw new IOException("APK inválido");
        if (now.signatures == null || now.signatures.length == 0
            || !now.signatures[0].equals(neu.signatures[0])) {
            throw new IOException("assinatura diferente da instalada");
        }
    }

    private static void session(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setSize(apk.length());
        int id = pi.createSession(params);
        final int[] status = {Integer.MIN_VALUE};
        final String[] message = {null};
        CountDownLatch done = new CountDownLatch(1);
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                if (i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != id) return;
                int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    // fora da ROM (sem permissão de sistema): a confirmação do próprio Android
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                }
                status[0] = st;
                message[0] = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                done.countDown();
            }
        };
        Context app = c.getApplicationContext();
        app.registerReceiver(r, new IntentFilter(ACTION_RESULT));
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            PendingIntent pend = PendingIntent.getBroadcast(app, id,
                new Intent(ACTION_RESULT).setPackage(app.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(pend.getIntentSender());
        }
        // instalando este próprio app, o processo morre aqui (e tudo bem)
        boolean ok = done.await(isSystem() ? 3 : 10, TimeUnit.MINUTES);
        try {
            app.unregisterReceiver(r);
        } catch (Exception ignored) {
        }
        if (!ok) throw new IOException("a instalação não respondeu");
        if (status[0] != PackageInstaller.STATUS_SUCCESS) {
            throw new IOException("Android recusou: " + (message[0] == null ? status[0] : message[0]));
        }
    }

    // ------------------------------------------------------------------ ROM

    /** Motivo para não poder instalar a ROM por aqui (null = pode). */
    static String romBlocker(Context c, Ota.Rom r) {
        if (!isSystem()) return "fora da ROM";
        String mine = Ota.platform(c);
        if (mine == null || r.platform.isEmpty() || !mine.equals(r.platform)) return "chave";
        if (!CACHE_ZIP.getParentFile().canWrite()) return "cache";
        return null;
    }

    /** Espaço livre no /cache (somando o update.zip antigo, que é apagado). */
    static long cacheFree() {
        try {
            StatFs s = new StatFs(CACHE_ZIP.getParent());
            long free = (long) s.getAvailableBlocks() * s.getBlockSize();
            File part = new File(CACHE_ZIP.getPath() + ".part");
            return free + (CACHE_ZIP.isFile() ? CACHE_ZIP.length() : 0) + (part.isFile() ? part.length() : 0);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Baixa a ROM direto no /cache, confere e manda para o recovery sem apagar os dados. Reinicia a central. */
    static void installRom(Context c, Ota.Rom r, Net.Progress p) throws Exception {
        String why = romBlocker(c, r);
        if (why != null) throw new IOException(why);
        long free = cacheFree();
        if (free >= 0 && free < r.size + (16L << 20)) throw new IOException("sem espaço no /cache");
        if (CACHE_ZIP.isFile()) CACHE_ZIP.delete();
        File part = new File(CACHE_ZIP.getPath() + ".part");
        Net.download(r.url, part, r.size, p);
        if (!r.sha256.equals(Ota.sha256(part))) {
            part.delete();
            throw new IOException("ROM corrompida (SHA-256 não confere)");
        }
        String mark = mark(part);
        if (mark == null || !mark.equals(Ota.platform(c))) {
            part.delete();
            throw new IOException("a ROM não tem a nossa chave de plataforma (apagaria os dados)");
        }
        if (!part.renameTo(CACHE_ZIP)) throw new IOException("não deu para preparar /cache/update.zip");
        CACHE_ZIP.setReadable(true, false);
        Log.i(TAG, "ROM " + r.version + " em " + CACHE_ZIP + ": MASTER_CLEAR sem apagar os dados");
        // igual ao atualizador da NWD (RkQuickUpdateOsActivity), com o extra do KeepData
        Intent i = new Intent("android.intent.action.MASTER_CLEAR");
        i.putExtra("recovery", CACHE_ZIP.getPath());
        i.putExtra("extra_wipe_data", false);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        c.sendBroadcast(i);
    }

    private static String mark(File zip) {
        try (ZipFile z = new ZipFile(zip)) {
            ZipEntry e = z.getEntry(MARK);
            if (e == null) return null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(z.getInputStream(e), "UTF-8"))) {
                String line = r.readLine();
                return line == null ? null : line.trim().toLowerCase();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
