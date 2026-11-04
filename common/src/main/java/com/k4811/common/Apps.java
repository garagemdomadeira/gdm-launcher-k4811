package com.k4811.common;

/**
 * Packages dos apps da suíte (tabela no build.gradle.kts da raiz, entregue pelo BuildConfig): os
 * originais da NWD, menos o launcher (com.garagemdomadeira.nwd.launcher) e o car-setting
 * (com.nwd.car.setting). O código Java fica sempre em com.k4811.*.
 */
public final class Apps {
    private Apps() {
    }

    public static final String LAUNCHER = BuildConfig.PKG_LAUNCHER;
    public static final String RADIO = BuildConfig.PKG_RADIO;
    public static final String MUSIC = BuildConfig.PKG_MUSIC;
    public static final String BT_MUSIC = BuildConfig.PKG_BT_MUSIC;
    public static final String PHONE = BuildConfig.PKG_PHONE;
    public static final String EQ = BuildConfig.PKG_EQ;
    /** Ventoinha. Aberta pelo bloco da ventoinha na tela inicial (fora do dock). */
    public static final String FAN = BuildConfig.PKG_FAN;
    /** Configurações (launcher, dock, idioma e central). Aberto pela engrenagem da tela inicial. */
    public static final String SETTINGS = BuildConfig.PKG_CAR_SETTING;

    /** Gaveta de apps do launcher. */
    public static final String ACTION_APPS = "com.k4811.launcher.action.APPS";
    /**
     * Launcher → telefone: a tela inicial está mostrando o cartão da ligação ({@link #EXTRA_SHOWN}
     * true) ou deixou de mostrar. Enquanto mostra, a janela flutuante da ligação fica escondida.
     */
    public static final String ACTION_HOME_CALL_CARD = "com.k4811.action.HOME_CALL_CARD";
    public static final String EXTRA_SHOWN = "shown";

    /**
     * Enviado por um app antes de virar a fonte de áudio. Os players do suíte pausam ao receber,
     * a não ser que sejam o remetente (extra {@link #EXTRA_FROM}).
     */
    public static final String ACTION_PAUSE_PLAYERS = "com.k4811.action.PAUSE_PLAYERS";
    public static final String EXTRA_FROM = "from";
}
