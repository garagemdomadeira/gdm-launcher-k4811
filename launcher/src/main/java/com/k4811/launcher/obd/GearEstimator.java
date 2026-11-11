package com.k4811.launcher.obd;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.Locale;

/**
 * Marcha pela relação rotação ÷ velocidade (rpm por km/h), para câmbio manual sem sinal de marcha
 * no OBD.
 *
 * <p>Ponto de partida: EcoSport/Fiesta 1.6 manual de 5 marchas (3,846 / 2,038 / 1,281 / 0,951 /
 * 0,756, diferencial 4,56, pneu 205/65 R15: 5ª ≈ 2.820 rpm a 100 km/h). Cada carro é diferente, então
 * o valor de cada marcha se ajusta ao que é medido e fica gravado (o aprendizado sobrevive ao
 * reinício):
 * <ul>
 * <li>só aprende com leitura estável (duas seguidas com a mesma relação, ±4%), acima de 15 km/h e
 * 1.000 rpm: troca de marcha e embreagem patinando mudam a relação de uma leitura para a outra;</li>
 * <li>a leitura puxa a marcha mais próxima (em escala logarítmica) mesmo um pouco fora da
 * tolerância, então se o carro tem relações bem diferentes do ponto de partida as marchas vão até
 * os valores certos em alguns minutos de uso;</li>
 * <li>marcha com poucas leituras segue o desvio médio das que já aprenderam: velocímetro da ECU que
 * marca abaixo do real ou pneu diferente deslocam todas as marchas na mesma proporção (no
 * EcoSport do autor, 2ª a 4ª aprenderam ~5% acima da ficha e a 5ª, pouco usada, ficou na ficha);</li>
 * <li>as marchas ficam sempre em ordem e a pelo menos 12% uma da outra (nunca duas viram uma).</li>
 * </ul>
 *
 * <p>Na tela, a marcha só muda depois de duas leituras seguidas iguais (~0,6 s): embreagem
 * patinando ou o meio de uma troca não fazem a indicação piscar.
 */
final class GearEstimator {
    private static final String TAG = "GdmGear";
    private static final String PREFS = "obd_gears";

    /** rpm por km/h da 1ª à 5ª (ponto de partida). */
    static final float[] DEFAULT = {143.7f, 76.1f, 47.9f, 35.5f, 28.2f};
    /** Leitura até 12% de uma marcha mostra essa marcha; fora disso é embreagem/troca (N). */
    private static final float SHOW_TOLERANCE = 0.12f;
    /** Até onde uma leitura estável ainda puxa a marcha mais próxima (aprendizado). */
    private static final float LEARN_TOLERANCE = 0.30f;
    private static final float STABLE = 0.04f;
    private static final float LEARN = 0.04f;
    /** Distância mínima entre marchas vizinhas (relação). */
    private static final float MIN_GAP = 1.12f;
    private static final long SAVE_EVERY_MS = 60_000;
    /** Leituras estáveis para uma marcha valer por si (antes disso segue o desvio das outras). */
    private static final int TRUSTED = 40;
    /** Leituras seguidas iguais para a indicação mudar. */
    private static final int CONFIRM = 2;

    /** Relação aprendida de cada marcha. */
    private final float[] ratio = DEFAULT.clone();
    /** Leituras estáveis usadas no aprendizado de cada marcha. */
    private final int[] samples = new int[DEFAULT.length];
    /** Relação usada para identificar (aprendida, ou ficha × desvio médio se ainda com poucas leituras). */
    private final float[] effective = DEFAULT.clone();
    private SharedPreferences prefs;
    private float last = -1;
    private long lastSave;
    private boolean dirty;
    private int shown = -1;
    private int candidate = -1;
    private int candidateCount;

    /** Carrega o aprendizado gravado (uma vez; antes disso usa o ponto de partida). */
    synchronized void attach(Context c) {
        if (prefs != null) return;
        prefs = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (int i = 0; i < ratio.length; i++) {
            ratio[i] = prefs.getFloat("g" + i, DEFAULT[i]);
            // Gravado antes da contagem existir: relação longe da ficha já foi aprendida de verdade.
            int def = Math.abs(ratio[i] - DEFAULT[i]) / DEFAULT[i] > 0.01f ? TRUSTED : 0;
            samples[i] = prefs.getInt("n" + i, def);
        }
        recompute();
        Log.i(TAG, "marchas: " + describe());
    }

    /** Volta ao ponto de partida (carro trocado, aprendizado ruim). */
    synchronized void reset() {
        System.arraycopy(DEFAULT, 0, ratio, 0, ratio.length);
        Arrays.fill(samples, 0);
        recompute();
        last = -1;
        shown = -1;
        candidate = -1;
        dirty = true;
        save(true);
    }

    /** @return 1..5, 0 parado/embreagem/troca, -1 sem dado */
    synchronized int update(int rpm, int speed) {
        if (rpm < 0 || speed < 0) return confirm(-1);
        if (speed < 5 || rpm < 700) {
            last = -1;
            // Parado: mostra na hora, sem esperar a confirmação.
            candidate = shown = 0;
            return 0;
        }
        float r = rpm / (float) speed;
        int best = nearest(r);
        float diff = Math.abs(r - effective[best]) / effective[best];

        boolean stable = last > 0 && Math.abs(r - last) / last < STABLE;
        last = r;
        if (stable && speed >= 15 && rpm >= 1000 && diff < LEARN_TOLERANCE) {
            // Marcha nova começa do valor efetivo (já com o desvio das outras), não da ficha.
            if (samples[best] < TRUSTED) ratio[best] = effective[best];
            ratio[best] += (r - ratio[best]) * LEARN;
            if (samples[best] < Integer.MAX_VALUE) samples[best]++;
            keepOrder();
            recompute();
            dirty = true;
            save(false);
        }
        return confirm(diff <= SHOW_TOLERANCE ? best + 1 : 0);
    }

    /** A indicação só muda depois de {@link #CONFIRM} leituras seguidas com o mesmo resultado. */
    private int confirm(int g) {
        if (g == candidate) {
            candidateCount++;
        } else {
            candidate = g;
            candidateCount = 1;
        }
        if (candidateCount >= CONFIRM || shown == -1) shown = g;
        return shown;
    }

    /** Marcha mais próxima em escala logarítmica (12% acima e abaixo pesam igual). */
    private int nearest(float r) {
        double lr = Math.log(r);
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < effective.length; i++) {
            double d = Math.abs(lr - Math.log(effective[i]));
            if (d < bestDiff) {
                bestDiff = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Desvio médio (em log) das marchas já confiáveis em relação à ficha; as outras usam ficha ×
     * esse desvio. Sem nenhuma confiável, tudo segue o aprendido como está.
     */
    private void recompute() {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < ratio.length; i++) {
            if (samples[i] >= TRUSTED) {
                sum += Math.log(ratio[i] / DEFAULT[i]);
                n++;
            }
        }
        float scale = n == 0 ? 1f : (float) Math.exp(sum / n);
        for (int i = 0; i < ratio.length; i++) {
            if (samples[i] >= TRUSTED || n == 0) {
                effective[i] = ratio[i];
            } else {
                // Mistura: quanto mais leituras próprias, mais vale o aprendido.
                float own = samples[i] / (float) TRUSTED;
                effective[i] = own * ratio[i] + (1 - own) * DEFAULT[i] * scale;
            }
        }
    }

    /** 1ª > 2ª > ... com folga mínima entre vizinhas. */
    private void keepOrder() {
        for (int i = 1; i < ratio.length; i++) {
            if (ratio[i] * MIN_GAP > ratio[i - 1]) ratio[i] = ratio[i - 1] / MIN_GAP;
        }
    }

    private void save(boolean now) {
        if (prefs == null || !dirty) return;
        long t = System.currentTimeMillis();
        if (!now && t - lastSave < SAVE_EVERY_MS) return;
        lastSave = t;
        dirty = false;
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < ratio.length; i++) {
            e.putFloat("g" + i, ratio[i]);
            e.putInt("n" + i, samples[i]);
        }
        e.apply();
        Log.i(TAG, "aprendido: " + describe());
    }

    private String describe() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ratio.length; i++) {
            b.append(i + 1).append("ª ").append(String.format(Locale.US, "%.1f", effective[i]))
                .append(" (").append(samples[i]).append(") ");
        }
        return b.toString().trim();
    }
}
