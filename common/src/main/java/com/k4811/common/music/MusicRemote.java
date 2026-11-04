package com.k4811.common.music;

import android.content.Context;
import android.content.Intent;

import com.k4811.common.Apps;

/**
 * Contrato entre o player do music e quem mostra o "o que está tocando" (o launcher).
 * O player envia {@link #ACTION_STATE} a cada mudança (posição no máximo 1×/s); quem mostra
 * pede o estado atual com {@link #query} e manda play/pause, anterior e próxima com {@link #send}.
 */
public final class MusicRemote {
    private MusicRemote() {
    }

    /** Estado do player (broadcast enviado pelo music). */
    public static final String ACTION_STATE = "com.k4811.action.MUSIC_STATE";
    /** Pede ao music que reenvie o estado (só responde se o player estiver rodando). */
    public static final String ACTION_QUERY = "com.k4811.action.MUSIC_QUERY";
    /** Comando para o player: extra {@link #EXTRA_CMD}. */
    public static final String ACTION_COMMAND = "com.k4811.action.MUSIC_COMMAND";

    public static final String EXTRA_CMD = "cmd";
    public static final int CMD_TOGGLE = 0;
    public static final int CMD_NEXT = 1;
    public static final int CMD_PREV = 2;

    /**
     * Com {@code true}, o comando só vale se o player já estiver tocando; se nada estiver tocando,
     * o music ignora (não inicia a reprodução). Usado pelas teclas do volante, que só mexem na
     * fonte ativa. Ausente/false mantém o comportamento antigo (o comando inicia o player).
     */
    public static final String EXTRA_ONLY_IF_ACTIVE = "only_if_active";

    public static final String EXTRA_HAS_TRACK = "has_track";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_ALBUM = "album";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_POSITION = "position_ms";
    public static final String EXTRA_DURATION = "duration_ms";
    public static final String EXTRA_MEDIA_ID = "media_id";
    public static final String EXTRA_PATH = "path";

    public static void query(Context c) {
        Intent i = new Intent(ACTION_QUERY);
        i.setPackage(Apps.MUSIC);
        c.sendBroadcast(i);
    }

    public static void send(Context c, int cmd) {
        send(c, cmd, false);
    }

    public static void send(Context c, int cmd, boolean onlyIfActive) {
        Intent i = new Intent(ACTION_COMMAND);
        i.setPackage(Apps.MUSIC);
        i.putExtra(EXTRA_CMD, cmd);
        if (onlyIfActive) i.putExtra(EXTRA_ONLY_IF_ACTIVE, true);
        c.sendBroadcast(i);
    }
}
