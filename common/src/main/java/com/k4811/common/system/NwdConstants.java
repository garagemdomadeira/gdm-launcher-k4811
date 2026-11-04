package com.k4811.common.system;

/** NWD system actions and extras used by the radio. */
public final class NwdConstants {
    private NwdConstants() {
    }

    public static final String ACTION_RADIO_SERVICE = "com.nwd.radio.service.ACTION_RADIO_SERVICE";

    public static final String ACTION_APP_IN_OUT = "com.nwd.action.ACTION_APP_IN_OUT";
    public static final String ACTION_MEDIA_PLAY = "com.nwd.ACTION_MEDIA_PLAY";
    public static final String ACTION_REQUEST_CHANGE_SOURCE = "com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE";
    public static final String ACTION_STOP_QQ_MUSIC = "com.music.action.STOP_QQ_MUSIC";
    public static final String ACTION_SEND_RADIO_FREQUENCE_NEW = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW";

    public static final String EXTRA_APP_ID = "extra_app_id";
    public static final String EXTRA_APP_OPERATION = "extra_app_operation";
    public static final String EXTRA_APP_EVENT = "extra_app_event";
    public static final String EXTRA_SOURCE_ID = "extra_source_id";
    public static final String EXTRA_FREQUENCE = "extra_frequence";
    public static final String EXTRA_BAND = "extra_band";

    public static final int APP_ID_RADIO = 8;
    /** USB/SD music. Sent when entering the screen. */
    public static final int APP_ID_MUSIC = 2;
    /** Bluetooth phone. */
    public static final int APP_ID_PHONE = 10;
    /** Bluetooth music. Sent when entering the screen. */
    public static final int APP_ID_BT_MUSIC = 15;

    public static final String ACTION_CHANGE_SOURCE = "com.nwd.action.ACTION_CHANGE_SOURCE";
    public static final String SETTING_CURRENT_SOURCE = "mcu_current_source";

    /** Current source values of {@link #SETTING_CURRENT_SOURCE}. */
    public static final int SOURCE_ANDROID = 0;
    public static final int SOURCE_RADIO = 4;
    public static final int SOURCE_AUX = 7;
    public static final int SOURCE_DVR = 8;

    /**
     * Teclas do painel/volante/controle, entregues pelo kernel da NWD a qualquer app por broadcast.
     * Vol± e mudo o kernel executa sozinho; avançar/recuar/play ele só repassa, e cabe ao app da
     * fonte ativa reagir. Extras: {@link #EXTRA_KEY_VALUE} (byte, um {@code KEY_*}) e
     * {@link #EXTRA_KEY_TYPE} (0 painel, 1 controle, 2 volante, 3 toque).
     */
    public static final String ACTION_KEY_VALUE = "com.nwd.action.ACTION_KEY_VALUE";
    public static final String EXTRA_KEY_VALUE = "extra_key_value";
    public static final String EXTRA_KEY_TYPE = "extra_key_type";

    /** McuKeyValue: próxima faixa/estação. */
    public static final int KEY_NEXT = 5;
    /** McuKeyValue: faixa/estação anterior. */
    public static final int KEY_PREVIOUS = 6;
    /** McuKeyValue: tocar/pausar. */
    public static final int KEY_PLAY = 7;
}
