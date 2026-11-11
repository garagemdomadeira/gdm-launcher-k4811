package com.k4811.common.can;

/**
 * Protocolo do CAN SDK da NWD (sdk_can V.220707), reconstruído do com.nwd.statusbarbottom decompilado.
 * Frame: [0]=HEAD(0x6E) [1]=dataType [2]=len [3..]=payload [len+3]=TAIL(0xFF). O offset do payload é 3.
 *
 * Só o necessário para o A/C: decodificar o frame de status (dataType 1 = AC_INFO) em {@link AirState}
 * e montar os frames de ajuste (dataType 39 = AIR_SETTING) de temperatura e ventilação.
 */
public final class CanProtocol {

    public static final byte HEAD = 110;        // 0x6E
    public static final byte TAIL = -1;         // 0xFF
    public static final byte DATATYPE_AC_INFO = 1;
    public static final byte DATATYPE_AIR_SETTING = 39;

    // mSettingType do frame AIR_SETTING (da interface SettingType do AirConditionState original da NWD).
    // Toggles/incrementos: valor 0, o MCU alterna/incrementa. Os "_VALUE" levam um valor absoluto.
    public static final byte POWER_SWITCH = 1;       // liga/desliga o A/C
    public static final byte AC_MODE = 2;
    public static final byte AC_MAX = 3;
    public static final byte AUTO = 4;
    public static final byte FRONT_DEFOG = 5;
    public static final byte REAR_DEFOG = 6;
    public static final byte RECIRC = 7;             // ar interno/externo (IN_OUTLOOP)
    public static final byte SYNC = 8;
    public static final byte DUAL = 9;
    public static final byte REAR_LOCK = 10;
    public static final byte WIND_UP = 14;           // modo de direção do ar
    public static final byte WIND_PARALLEL = 15;
    public static final byte WIND_DOWN = 16;
    public static final byte FAN_INC = 21;           // ventilação + / -
    public static final byte FAN_DEC = 22;
    public static final byte FAN_VALUE = 23;         // ventilação absoluta (valor)
    public static final byte LEFT_TEMP_INC = 24;     // temperatura esquerda + / -
    public static final byte LEFT_TEMP_DEC = 25;
    public static final byte LEFT_TEMP_VALUE = 26;
    public static final byte RIGHT_TEMP_INC = 27;    // temperatura direita + / -
    public static final byte RIGHT_TEMP_DEC = 28;
    public static final byte RIGHT_TEMP_VALUE = 29;
    public static final byte ECO_MODE = 58;

    private CanProtocol() {
    }

    public static int offset() {
        return 3;
    }

    public static byte dataType(byte[] f) {
        return f[1];
    }

    public static boolean isAc(byte[] f) {
        return f != null && f.length > 5 && f[0] == HEAD && f[1] == DATATYPE_AC_INFO;
    }

    /** Frame vazio: HEAD, dataType, len, payload (len bytes a zero), TAIL. */
    public static byte[] generateNullProtocal(int len, byte dataType) {
        byte[] f = new byte[len + 4];
        f[0] = HEAD;
        f[1] = dataType;
        f[2] = (byte) (len & 255);
        f[len + 3] = TAIL;
        return f;
    }

    // ---- Controle (app -> carro) -----------------------------------------------------------------

    /** Comando de toggle/incremento (valor 0): POWER_SWITCH, AUTO, DUAL, FRONT_DEFOG, FAN_INC, LEFT_TEMP_INC… */
    public static byte[] packControl(byte settingType) {
        return packSetting(settingType, (byte) 0);
    }

    /** Comando com valor absoluto (FAN_VALUE, LEFT_TEMP_VALUE, RIGHT_TEMP_VALUE). */
    public static byte[] packValue(byte settingType, int value) {
        return packSetting(settingType, (byte) value);
    }

    /** Frame AIR_SETTING: 6E 27 02 <settingType> <value> FF. */
    public static byte[] packSetting(byte settingType, byte value) {
        byte[] f = generateNullProtocal(2, DATATYPE_AIR_SETTING);
        int o = offset();
        f[o] = settingType;
        f[o + 1] = value;
        return f;
    }

    // ---- Status (carro -> app) -------------------------------------------------------------------

    /** Decodifica um frame AC_INFO (dataType 1) em {@link AirState}; null se o frame não serve. */
    public static AirState parseAir(byte[] f) {
        if (!isAc(f)) return null;
        int o = offset();
        if (f.length < o + 16) return null;
        AirState s = new AirState();
        s.acSwitch = bit(f[o], 7);
        s.acMode = bit(f[o], 6);
        s.acAuto = bit(f[o], 5);
        s.insideOrOutside = bit(f[o], 4);
        s.aqsInsideOrOutside = bit(f[o], 3);
        s.auto = bit(f[o], 2);
        s.sync = bit(f[o], 1);
        s.dual = bit(f[o], 0);

        s.frontWindowDefog = bit(f[o + 1], 7);
        s.backWindowDefog = bit(f[o + 1], 6);
        s.maxAc = bit(f[o + 1], 5);
        s.econMode = bit(f[o + 1], 4);
        s.rearLock = bit(f[o + 1], 3);
        s.airSpeedAuto = bit(f[o + 1], 2);
        s.windStrongLv = (byte) (f[o + 1] & 3);

        s.airModeUp = bit(f[o + 2], 7);
        s.airModeParallel = bit(f[o + 2], 6);
        s.airModeDown = bit(f[o + 2], 5);
        s.airModeCeiling = bit(f[o + 2], 4);
        s.airSpeedLevel = (byte) (f[o + 2] & 15);

        s.clean = bit(f[o + 3], 7);
        s.swing = bit(f[o + 3], 6);
        s.fxpHeat = bit(f[o + 3], 5);
        s.coldHotBox = (byte) ((f[o + 3] >> 3) & 3);
        byte unit = (byte) ((f[o + 3] >> 2) & 1);
        s.tempFahrenheit = unit == 1;

        s.ptcMode = bit(f[o + 4], 7);
        s.sunroof = bit(f[o + 4], 6);
        s.frontWindowHeat = bit(f[o + 4], 5);
        s.heat = bit(f[o + 4], 4);
        s.blowing = bit(f[o + 4], 3);

        s.leftSeatCold = (byte) ((f[o + 5] >> 4) & 15);
        s.rightSeatCold = (byte) (f[o + 5] & 15);
        s.leftSeatHeat = (byte) ((f[o + 6] >> 4) & 15);
        s.rightSeatHeat = (byte) (f[o + 6] & 15);

        s.leftTemp = temp(f[o + 7], unit);
        s.rightTemp = temp(f[o + 8], unit);

        s.backAirState = bit(f[o + 9], 7);
        s.backAirModeAuto = bit(f[o + 9], 6);
        s.backAirSync = bit(f[o + 9], 5);
        s.backDual = bit(f[o + 9], 4);
        s.backAirLock = bit(f[o + 9], 3);
        s.backAuto = bit(f[o + 9], 2);
        s.backLeftSeatHot = (byte) ((f[o + 10] >> 6) & 3);
        s.backRightSeatHot = (byte) ((f[o + 10] >> 4) & 3);
        s.backAirModeUp = bit(f[o + 11], 7);
        s.backAirModeParallel = bit(f[o + 11], 6);
        s.backAirModeDown = bit(f[o + 11], 5);
        s.backAirSpeedLevel = (byte) (f[o + 11] & 15);
        s.backLeftTemp = temp(f[o + 12], unit);
        s.backRightTemp = temp(f[o + 13], unit);
        s.backLeftSeatCold = (byte) ((f[o + 14] >> 4) & 15);
        s.backRightSeatCold = (byte) (f[o + 14] & 15);
        s.airSpeedLevelMax = (byte) ((f[o + 15] >> 4) & 15);
        return s;
    }

    private static boolean bit(byte b, int n) {
        return ((b >> n) & 1) == 1;
    }

    /** Temperatura formatada (igual ao getTemp original): 0=Low, -1=High, -2=inválida, senão (v/2)°. */
    private static String temp(byte b, byte unit) {
        if (b == 0) return "Low";
        if (b == -1) return "High";
        if (b == -2) return "-.--";
        return ((b & 255) / 2.0f) + (unit == 0 ? "℃" : "℉");
    }
}
