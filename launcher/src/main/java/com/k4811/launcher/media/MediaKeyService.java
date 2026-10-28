package com.k4811.launcher.media;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.k4811.common.bt.BtClient;
import com.k4811.common.config.LauncherConfig;
import com.k4811.common.media.AppMediaClient;
import com.k4811.common.music.MusicRemote;
import com.k4811.common.radio.RadioClient;
import com.k4811.common.radio.RadioState;
import com.k4811.common.system.NwdConstants;

/**
 * Despacha as teclas de avançar/recuar (do {@link MediaKeyReceiver}) para a fonte que está tocando:
 *
 * <ul>
 *   <li><b>Rádio</b> (fonte 4): próxima/anterior <i>estação salva</i> da banda, ciclando
 *       ({@link RadioClient#presetStep(boolean)}).</li>
 *   <li><b>Android</b> (fonte 0): se a música Bluetooth está tocando, manda AVRCP próxima/anterior;
 *       senão, manda ao MP3 — que só reage se o próprio player estiver tocando.</li>
 *   <li>Qualquer outra fonte, ou nada tocando: não faz nada.</li>
 * </ul>
 *
 * <p>Os serviços de rádio e BT são os da NWD, acessados pelos clientes do common. Mantém a conexão
 * aquecida por um tempo para que apertos seguidos respondam na hora, e se solta depois de ocioso.
 */
public class MediaKeyService extends Service {
    private static final String TAG = "K4811MediaKey";

    public static final String ACTION_KEY = "com.k4811.launcher.MEDIA_KEY";
    /** boolean: true = próxima, false = anterior. */
    public static final String EXTRA_NEXT = "next";

    /** playControl do serviço de BT: 2 próxima, 3 anterior (0 play, 1 pause). */
    private static final int BT_NEXT = 2;
    private static final int BT_PREV = 3;

    /** Solta os serviços depois deste tempo sem tecla. */
    private static final long IDLE_MS = 60_000L;
    /** Espera depois de conectar o rádio a frio, para a estação e as memórias chegarem. */
    private static final long RADIO_WARMUP_MS = 700L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable idle = this::stopSelf;

    private BtClient bt;
    private RadioClient radio;
    private boolean radioReady;
    /** Preset pendente enquanto o rádio ainda conecta (null = nenhum). */
    private Boolean pendingRadioNext;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_KEY.equals(intent.getAction())) {
            handle(intent.getBooleanExtra(EXTRA_NEXT, true));
        }
        main.removeCallbacks(idle);
        main.postDelayed(idle, IDLE_MS);
        return START_NOT_STICKY;
    }

    private void handle(boolean next) {
        int source = currentSource();
        if (source == NwdConstants.SOURCE_RADIO) {
            radioStep(next);
        } else if (source == NwdConstants.SOURCE_ANDROID) {
            androidStep(next);
        }
        // AUX, DVR, nenhuma fonte: nada.
    }

    private void radioStep(boolean next) {
        ensureRadio();
        if (radioReady && radio.getState().connection == RadioState.Connection.CONNECTED) {
            radio.presetStep(next);
        } else {
            pendingRadioNext = next; // roda quando conectar (listener abaixo)
        }
    }

    private void ensureRadio() {
        if (radio != null) return;
        radio = new RadioClient(this);
        radio.setListener(state -> {
            if (state.connection != RadioState.Connection.CONNECTED) return;
            radioReady = true;
            if (pendingRadioNext == null) return;
            final boolean next = pendingRadioNext;
            pendingRadioNext = null;
            main.postDelayed(() -> {
                if (radio != null) radio.presetStep(next);
            }, RADIO_WARMUP_MS);
        });
        radio.connect(false); // só observa: não assume a fonte nem anuncia reprodução
    }

    private void androidStep(boolean next) {
        // Um app de mídia (Spotify, Deezer…) tocando tem prioridade, se o recurso estiver ligado.
        if (LauncherConfig.mediaAppControls(this)
            && AppMediaClient.sendKey(this, new ComponentName(this, MediaNotificationListener.class), next)) {
            return;
        }
        ensureBt();
        bt.query((callState, musicPlaying, a2dp) -> {
            if (musicPlaying) {
                bt.playControl(next ? BT_NEXT : BT_PREV);
            } else {
                MusicRemote.send(this, next ? MusicRemote.CMD_NEXT : MusicRemote.CMD_PREV, true);
            }
        });
    }

    private void ensureBt() {
        if (bt != null) return;
        bt = new BtClient(this);
        bt.connect();
    }

    private int currentSource() {
        try {
            return Settings.System.getInt(getContentResolver(), NwdConstants.SETTING_CURRENT_SOURCE);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(idle);
        if (bt != null) {
            bt.release();
            bt = null;
        }
        if (radio != null) {
            radio.release();
            radio = null;
        }
        radioReady = false;
        pendingRadioNext = null;
        Log.d(TAG, "ocioso: serviços de mídia soltos");
    }

    public static void startKey(Context context, boolean next) {
        Intent cmd = new Intent(context, MediaKeyService.class);
        cmd.setAction(ACTION_KEY);
        cmd.putExtra(EXTRA_NEXT, next);
        try {
            context.startService(cmd);
        } catch (Exception e) {
            Log.w(TAG, "startKey: " + e.getMessage());
        }
    }
}
