package com.k4811.updater;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.List;

/**
 * Procura atualizações em segundo plano: no GitHub uma vez por semana (ou na hora, com {@link #EXTRA_FORCE}); nos
 * outros dias usa o último ota.json conferido. Achando algo, mostra o {@link Prompt} (a pessoa decide; nada é
 * instalado sem "Confirmar update").
 */
public final class CheckService extends Service {
    static final String EXTRA_FORCE = "force";
    private static final String TAG = "GdmOta";
    private static volatile boolean busy;

    /** Resultado da última procura (para a tela). */
    enum Result { OK, OFFLINE, BAD_SIGNATURE }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean force = intent != null && intent.getBooleanExtra(EXTRA_FORCE, false);
        if (busy) return START_NOT_STICKY;
        busy = true;
        Context app = getApplicationContext();
        new Thread(() -> {
            try {
                Ota ota = obtain(app, force).ota;
                if (ota == null) return;
                List<Ota.App> apps = ota.pendingApps(app);
                Ota.Rom rom = ota.pendingRom(app);
                long snooze = Ota.prefs(app).getLong(Ota.KEY_SNOOZE, 0);
                if ((!apps.isEmpty() || rom != null) && System.currentTimeMillis() >= snooze) {
                    new Handler(Looper.getMainLooper()).post(() -> Prompt.show(app, apps, rom));
                }
            } catch (Throwable t) {
                Log.w(TAG, "procura falhou", t);
            } finally {
                busy = false;
                stopSelf(startId);
            }
        }, "gdm-ota-check").start();
        return START_NOT_STICKY;
    }

    static final class Fetched {
        Ota ota;
        Result result = Result.OK;
    }

    /**
     * O ota.json: do GitHub se {@code force} ou se já faz uma semana, senão o guardado. Sem rede, o guardado.
     * Roda fora da thread principal.
     */
    static Fetched obtain(Context c, boolean force) {
        Fetched f = new Fetched();
        long last = Ota.prefs(c).getLong(Ota.KEY_LAST_CHECK, 0);
        boolean due = force || System.currentTimeMillis() - last >= Ota.WEEK_MS;
        if (due) {
            try {
                byte[] json = Net.bytes(Ota.MANIFEST_URL);
                byte[] sig = Net.bytes(Ota.SIG_URL);
                f.ota = Ota.parse(json, sig);
                Ota.store(c, json);
                Log.i(TAG, "ota.json de " + f.ota.generated + ": " + f.ota.apps.size() + " apps, " + f.ota.roms.size() + " ROMs");
                return f;
            } catch (SecurityException e) {
                Log.w(TAG, e.getMessage());
                f.result = Result.BAD_SIGNATURE;
            } catch (Exception e) {
                Log.w(TAG, "sem acesso ao GitHub: " + e);
                f.result = Result.OFFLINE;
            }
        }
        f.ota = Ota.cached(c);
        return f;
    }
}
