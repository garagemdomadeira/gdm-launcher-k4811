package com.k4811.common.can;

/**
 * Estado do ar-condicionado do carro, decodificado do frame CAN de A/C (dataType 1, AC_INFO) pelo
 * {@link CanProtocol#parseAir}. Campos iguais aos do AirConditionState original da NWD (sdk_can), mas
 * só os que a central de fato preenche no frame de 16 bytes. Tudo público e simples (POJO), à moda da suíte.
 */
public final class AirState {

    // byte 0 (offset+0)
    public boolean acSwitch;            // A/C ligado
    public boolean acMode;
    public boolean acAuto;
    public boolean insideOrOutside;     // recirculação (ar interno/externo)
    public boolean aqsInsideOrOutside;
    public boolean auto;                // AUTO
    public boolean sync;
    public boolean dual;                // DUAL (zonas independentes)
    // byte 1
    public boolean frontWindowDefog;    // desembaçador dianteiro
    public boolean backWindowDefog;     // desembaçador traseiro
    public boolean maxAc;
    public boolean econMode;
    public boolean rearLock;
    public boolean airSpeedAuto;
    public byte windStrongLv;           // 0..3
    // byte 2 (modo de direção do ar + nível da ventilação)
    public boolean airModeUp;
    public boolean airModeParallel;
    public boolean airModeDown;
    public boolean airModeCeiling;
    public byte airSpeedLevel;          // 0..15 (ventilação atual)
    // byte 3
    public boolean clean;
    public boolean swing;
    public boolean fxpHeat;
    public byte coldHotBox;             // 0..3
    public boolean tempFahrenheit;      // unidade: true = ℉, false = ℃ (bit extraído do byte 3)
    // byte 4
    public boolean ptcMode;
    public boolean sunroof;
    public boolean frontWindowHeat;
    public boolean heat;
    public boolean blowing;
    // bytes 5-6 (bancos)
    public byte leftSeatCold;           // 0..15
    public byte rightSeatCold;
    public byte leftSeatHeat;
    public byte rightSeatHeat;
    // bytes 7-8 (temperaturas, já formatadas: "22.5℃", "Low", "High", "-.--")
    public String leftTemp = "-.--";
    public String rightTemp = "-.--";
    // bytes 9-15 (zona traseira / máximos)
    public boolean backAirState;
    public boolean backAirModeAuto;
    public boolean backAirSync;
    public boolean backDual;
    public boolean backAirLock;
    public boolean backAuto;
    public byte backLeftSeatHot;
    public byte backRightSeatHot;
    public boolean backAirModeUp;
    public boolean backAirModeParallel;
    public boolean backAirModeDown;
    public byte backAirSpeedLevel;
    public String backLeftTemp = "-.--";
    public String backRightTemp = "-.--";
    public byte backLeftSeatCold;
    public byte backRightSeatCold;
    public byte airSpeedLevelMax;       // ventilação máxima (para a barra). 0 = usar o padrão 7.

    /** Ventilação máxima efetiva para a UI (a NWD usa 7 quando o carro não informa). */
    public int fanMax() {
        return airSpeedLevelMax > 0 ? airSpeedLevelMax : 7;
    }
}
