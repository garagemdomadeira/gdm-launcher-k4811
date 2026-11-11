package com.k4811.updater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Agenda a procura: um alarme por dia (o primeiro 10 min depois de ligar). A cada alarme o {@link CheckService} vê se
 * já faz uma semana desde a última procura no GitHub; se ainda não, só lembra o que já estava pendente.
 */
public final class CheckReceiver extends BroadcastReceiver {
    static final String ACTION_CHECK = "com.k4811.updater.action.CHECK";
    private static final long FIRST_DELAY_MS = 10 * 60 * 1000L;

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (ACTION_CHECK.equals(a)) {
            c.startService(new Intent(c, CheckService.class));
            return;
        }
        // ligou, ou este app foi atualizado (o alarme antigo some junto)
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(a)) {
            cleanCache();
        }
        schedule(c);
    }

    /**
     * Depois de gravar, o recovery deixa a ROM no /cache (1,6 GB): ao ligar ela já foi usada (ou a gravação falhou e o
     * download começa de novo). Apaga para o /cache ficar livre para a próxima.
     */
    private static void cleanCache() {
        try {
            java.io.File zip = Installer.CACHE_ZIP;
            java.io.File part = new java.io.File(zip.getPath() + ".part");
            if (zip.isFile() && zip.delete()) android.util.Log.i("OtaLimpeza", "apagado " + zip);
            if (part.isFile() && part.delete()) android.util.Log.i("OtaLimpeza", "apagado " + part);
        } catch (Exception ignored) {
        }
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0,
            new Intent(ACTION_CHECK).setClass(c, CheckReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + FIRST_DELAY_MS,
            AlarmManager.INTERVAL_DAY, pi);
    }
}
