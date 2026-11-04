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
        schedule(c);
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
