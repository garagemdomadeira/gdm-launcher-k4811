package com.k4811.launcher.obd;

/** Última leitura do OBD2. -1 (ou NaN na tensão) = ainda sem valor. */
public final class ObdData {

    public enum State { NO_DEVICE, CONNECTING, CONNECTED, ERROR }

    public State state = State.CONNECTING;
    public int rpm = -1;
    public int speed = -1;
    /** Marcha estimada: 0 = ponto morto/embreagem, -1 = sem dado. */
    public int gear = -1;
    /** Sugestão de troca: 1 subir (↑), -1 reduzir (↓), 0 nenhuma. */
    public int shift;
    public float volts = Float.NaN;
    public int coolant = Integer.MIN_VALUE;
    /** Combustível em %; -1 quando o carro não informa (comum em carros antigos). */
    public int fuel = -1;
    /** Códigos de defeito da última leitura (confirmados antes dos pendentes); null = ainda não lidos. */
    public Dtc[] dtcs;
    /** Luz de injeção acesa (0101, bit 7 do byte A). */
    public boolean mil;

    ObdData copy() {
        ObdData d = new ObdData();
        d.state = state;
        d.rpm = rpm;
        d.speed = speed;
        d.gear = gear;
        d.shift = shift;
        d.volts = volts;
        d.coolant = coolant;
        d.fuel = fuel;
        d.dtcs = dtcs;
        d.mil = mil;
        return d;
    }
}
