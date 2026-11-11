package com.k4811.launcher.obd;

/**
 * Sugestão de troca, como o indicador ↑/↓ do painel: rodar entre 2.000 e 2.500 rpm no dia a dia.
 * <ul>
 * <li>↑ subir: 1ª acima de 2.200 rpm (a 1ª é só para arrancar), 2ª a 4ª acima de 2.600 rpm;</li>
 * <li>↓ reduzir: da 2ª em diante abaixo de 1.400 rpm andando (acima de 10 km/h): giro baixo força
 * o motor e a embreagem.</li>
 * </ul>
 * Precisa de duas leituras seguidas para aparecer e some com folga de 200 rpm (não pisca na
 * fronteira). Sem marcha (N, parado) não sugere nada.
 */
final class ShiftHint {
    static final int NONE = 0;
    static final int UP = 1;
    static final int DOWN = -1;

    private static final int UP_FIRST_RPM = 2200;
    private static final int UP_RPM = 2600;
    private static final int DOWN_RPM = 1400;
    private static final int HYSTERESIS = 200;
    private static final int DOWN_MIN_SPEED = 10;
    /** Marcha mais alta (5 marchas): nela não há ↑. */
    private static final int TOP_GEAR = 5;

    private int shown = NONE;
    private int pending = NONE;

    int update(int gear, int rpm, int speed) {
        if (gear < 1 || rpm < 0) {
            shown = pending = NONE;
            return NONE;
        }
        int upAt = gear == 1 ? UP_FIRST_RPM : UP_RPM;
        int want;
        if (shown == UP) {
            want = gear < TOP_GEAR && rpm > upAt - HYSTERESIS ? UP : NONE;
        } else if (shown == DOWN) {
            want = gear > 1 && speed >= DOWN_MIN_SPEED && rpm < DOWN_RPM + HYSTERESIS ? DOWN : NONE;
        } else if (gear < TOP_GEAR && rpm >= upAt) {
            want = UP;
        } else if (gear > 1 && speed >= DOWN_MIN_SPEED && rpm <= DOWN_RPM) {
            want = DOWN;
        } else {
            want = NONE;
        }
        // Apagar é na hora; acender só com a segunda leitura igual.
        if (want == NONE || want == pending) shown = want;
        pending = want;
        return shown;
    }
}
