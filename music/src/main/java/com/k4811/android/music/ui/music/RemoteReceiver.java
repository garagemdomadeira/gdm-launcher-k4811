package com.k4811.android.music.ui.music;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.k4811.common.music.MusicRemote;

/**
 * Comandos e pedidos de estado vindos do launcher ({@link MusicRemote}).
 * Comando inicia o player se preciso; pedido de estado só é respondido com o player rodando.
 */
public class RemoteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (MusicRemote.ACTION_QUERY.equals(action)) {
            MusicPlaybackService running = MusicPlaybackService.running();
            if (running != null) running.broadcastState(true);
            return;
        }
        if (!MusicRemote.ACTION_COMMAND.equals(action)) return;
        // Teclas do volante: só mexem na fonte ativa, então ignora se o player não está tocando
        // (não inicia a reprodução por cima do rádio ou do BT).
        if (intent.getBooleanExtra(MusicRemote.EXTRA_ONLY_IF_ACTIVE, false)
            && !MusicPlaybackService.isPlayingNow()) {
            return;
        }
        Intent cmd = new Intent(context, MusicPlaybackService.class);
        cmd.setAction(MusicPlaybackService.ACTION_REMOTE);
        cmd.putExtra(MusicRemote.EXTRA_CMD, intent.getIntExtra(MusicRemote.EXTRA_CMD, MusicRemote.CMD_TOGGLE));
        try {
            context.startService(cmd);
        } catch (Exception ignored) {
            // Sistema não deixou iniciar o serviço agora.
        }
    }
}
