package com.k4811.launcher.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.k4811.common.system.NwdConstants;

/**
 * Teclas de mídia do painel/volante/controle ({@link NwdConstants#ACTION_KEY_VALUE}). O launcher é o
 * único que escuta: decide a fonte ativa e despacha avançar/recuar, no {@link MediaKeyService}. Vol±
 * e mudo não passam por aqui — o kernel da NWD os executa sozinho.
 *
 * <p>Receiver de manifest: com targetSdk 19 o broadcast implícito ainda chega mesmo com o launcher
 * em segundo plano, e o sistema inicia o processo para entregá-lo.
 */
public class MediaKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "K4811MediaKey";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !NwdConstants.ACTION_KEY_VALUE.equals(intent.getAction())) return;
        int key = readKey(intent);
        final boolean next;
        if (key == NwdConstants.KEY_NEXT) next = true;
        else if (key == NwdConstants.KEY_PREVIOUS) next = false;
        else return; // outras teclas não são nossas

        Intent cmd = new Intent(context, MediaKeyService.class);
        cmd.setAction(MediaKeyService.ACTION_KEY);
        cmd.putExtra(MediaKeyService.EXTRA_NEXT, next);
        try {
            context.startService(cmd);
        } catch (Exception e) {
            Log.w(TAG, "startService: " + e.getMessage());
        }
    }

    /** O extra vem como byte (RadioService lê getByteExtra), mas aceita int por garantia. */
    private static int readKey(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(NwdConstants.EXTRA_KEY_VALUE)) return -1;
        Object v = extras.get(NwdConstants.EXTRA_KEY_VALUE);
        if (v instanceof Byte) return ((Byte) v) & 0xFF;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Short) return ((Short) v) & 0xFFFF;
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
