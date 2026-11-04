package com.k4811.launcher.media;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Process;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Qual app está tocando agora. Na central da NWD o {@link AudioManager#isMusicActive()} responde
 * "não" mesmo com o Spotify tocando (o áudio passa pelo sistema de áudio da NWD), então olhamos o
 * próprio player: a sessão de mídia em PLAYING (precisa de MEDIA_CONTENT_CONTROL, que vale como app
 * privilegiado) e, sem ela, os players de mídia que o sistema lista como iniciados.
 */
public final class ActivePlayer {
    /** AudioPlaybackConfiguration.PLAYER_STATE_STARTED (API oculta). */
    private static final int PLAYER_STATE_STARTED = 2;
    private static Method sGetPlayerState;
    private static Method sGetClientUid;
    private static boolean sMethodsResolved;

    private ActivePlayer() {
    }

    private static void ensureMethods() {
        if (sMethodsResolved || Build.VERSION.SDK_INT < 26) return;
        sMethodsResolved = true;
        try {
            sGetPlayerState = AudioPlaybackConfiguration.class.getMethod("getPlayerState");
            sGetClientUid = AudioPlaybackConfiguration.class.getMethod("getClientUid");
        } catch (Exception ignored) {
        }
    }

    /**
     * Sessões de mídia ativas: como app privilegiado (MEDIA_CONTENT_CONTROL) ou pelo leitor de
     * notificações ({@link MediaNotificationListener}); null sem nenhum dos dois.
     */
    public static List<MediaController> sessions(Context c) {
        MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (m == null) return null;
        try {
            return m.getActiveSessions(null);
        } catch (Exception ignored) {
        }
        try {
            return m.getActiveSessions(new ComponentName(c, MediaNotificationListener.class));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Sessão que está tocando, ou null. */
    public static MediaController playingController(Context c) {
        return playing(sessions(c));
    }

    private static MediaController playing(List<MediaController> list) {
        if (list == null) return null;
        for (MediaController mc : list) {
            PlaybackState st = mc.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) return mc;
        }
        return null;
    }

    /** Package do app que está tocando, ou "" se nenhum (ou se o sistema não diz de quem é). */
    public static String playingPackage(Context c) {
        List<MediaController> list = sessions(c);
        if (list != null) {
            MediaController mc = playing(list);
            return mc == null ? "" : mc.getPackageName();
        }
        if (Build.VERSION.SDK_INT < 26) return "";
        AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return "";
        ensureMethods();
        try {
            if (sGetPlayerState != null && sGetClientUid != null) {
                for (AudioPlaybackConfiguration cfg : audio.getActivePlaybackConfigurations()) {
                    if ((Integer) sGetPlayerState.invoke(cfg) != PLAYER_STATE_STARTED) continue;
                    int uid = (Integer) sGetClientUid.invoke(cfg);
                    if (uid == Process.myUid() || uid < 0) continue;
                    String[] pkgs = c.getPackageManager().getPackagesForUid(uid);
                    if (pkgs != null && pkgs.length > 0) return pkgs[0];
                }
            }
        } catch (Exception ignored) {
        }
        // a NWD guarda o último app que abriu o áudio de música, mesmo parado: só vale com música tocando agora
        String nwdProc = audio.isMusicActive()
            ? com.k4811.common.system.SystemProps.get("sys.nwd.cur.music.stream.proc", "") : "";
        if (!nwdProc.isEmpty() && !nwdProc.equals(c.getPackageName())) {
            return nwdProc;
        }
        return "";
    }

    /**
     * Algum player de mídia tocando. Sem permissão de sistema o Android esconde o dono dos players
     * (uid -1), mas ainda diz que estão tocando: basta para o botão e o equalizador do dock.
     */
    public static boolean anyPlaying(Context c) {
        AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return false;
        if (audio.isMusicActive() || !playingPackage(c).isEmpty()) return true;
        if (Build.VERSION.SDK_INT < 26) return false;
        ensureMethods();
        try {
            if (sGetPlayerState != null) {
                for (AudioPlaybackConfiguration cfg : audio.getActivePlaybackConfigurations()) {
                    if ((Integer) sGetPlayerState.invoke(cfg) != PLAYER_STATE_STARTED) continue;
                    int usage = cfg.getAudioAttributes().getUsage();
                    if (usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
