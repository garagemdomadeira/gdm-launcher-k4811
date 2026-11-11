package com.k4811.audioset.eq;

import com.k4811.common.eq.EqModel;

/** Binary frames for the ARM DSP. */
public final class DspFrames {

    private static final byte TYPE_AUDIO = 0x0D;
    private static final int DATA_OFFSET = 5;

    public static final int LOUDNESS = 0x01;
    public static final int ONE_EQ = 0x07;
    public static final int PHASE = 0x09;
    public static final int LEVEL = 0x0A;
    public static final int DELAY = 0x0B;
    public static final int ALL_EQ = 0xF1;
    public static final int OTHER_ALL = 0xF2;
    public static final int SITE = 0xF9;
    /** High/low-pass filter of one area (stock sendHpfData/sendLpfData). */
    public static final int FILTER = 0x0D;
    /** Bass boost (DBB) of one area (stock sendAreayData). */
    public static final int BASS_BOOST = 0x1B;
    /** 3D surround on/off (stock sendRound3DDate). */
    public static final int ROUND_3D = 0x1C;

    private DspFrames() {
    }

    private static byte[] frame(int dataType, byte[] payload) {
        int len = payload.length + 3;
        byte[] out = new byte[len + 3];
        out[0] = (byte) 0xF0;
        out[1] = (byte) len;
        out[2] = TYPE_AUDIO;
        out[3] = (byte) dataType;
        System.arraycopy(payload, 0, out, DATA_OFFSET, payload.length);
        int sum = 0;
        for (int i = 1; i < out.length - 1; i++) sum += out[i];
        out[out.length - 1] = (byte) sum;
        return out;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    public static byte[] loudness(boolean on) {
        return frame(LOUDNESS, bytes(17, on ? 1 : 0));
    }

    public static byte[] site(int fader, int balance) {
        return frame(SITE, bytes(
            clamp(fader, 0, EqModel.SITE_MAX),
            clamp(balance, 0, EqModel.SITE_MAX)));
    }

    public static byte[] oneEq(int band, int freq, int q, int gainWire) {
        int qq = q == 0 ? 10 : q;
        return frame(ONE_EQ, bytes(
            band, freq & 0xFF, (freq >> 8) & 0xFF, qq & 0xFF, (qq >> 8) & 0xFF, gainWire));
    }

    public static byte[] allEq(int[] freqs, int[] qs, int[] gainsWire) {
        int n = freqs.length;
        byte[] payload = new byte[1 + n * 5];
        payload[0] = (byte) n;
        for (int i = 0; i < n; i++) {
            int q = qs[i] == 0 ? EqModel.EQ32_DEFAULT_Q : qs[i];
            int base = 1 + i * 5;
            payload[base] = (byte) (freqs[i] & 0xFF);
            payload[base + 1] = (byte) ((freqs[i] >> 8) & 0xFF);
            payload[base + 2] = (byte) (q & 0xFF);
            payload[base + 3] = (byte) ((q >> 8) & 0xFF);
            payload[base + 4] = (byte) gainsWire[i];
        }
        return frame(ALL_EQ, payload);
    }

    /**
     * Full state frame (0xF2) the stock app (DspAdjust3 sendF2Data) sends at boot: channels, crossover
     * of the 4 areas, fader/balance, bass boost areas and 3D, all from the saved state. Without it
     * the DSP keeps its power-on crossover, which sounds muffled and low.
     */
    public static byte[] otherAll(EqModel.State st) {
        java.io.ByteArrayOutputStream p = new java.io.ByteArrayOutputStream(105);
        for (int i = 0; i < EqModel.Speaker.COUNT; i++) {
            EqModel.Channel c = i < st.channels.size() ? st.channels.get(i) : new EqModel.Channel();
            int d = clamp(c.delay, 0, 200);
            put(p, 5, 14 - clamp(c.level, 0, 14), c.inverted ? 1 : 0, c.muted ? 1 : 0, d & 0xFF, (d >> 8) & 0xFF);
        }
        // Per area: low-pass first, then high-pass (frequency in Hz, slope).
        for (int a = 0; a < EqModel.CROSSOVER_AREAS; a++) {
            EqModel.Crossover x = a < st.crossovers.size() ? st.crossovers.get(a) : EqModel.defaultCrossover(a);
            int lpf = pfHz(x.lpf);
            int hpf = pfHz(x.hpf);
            put(p, 4, lpf & 0xFF, (lpf >> 8) & 0xFF, x.lpfQ & 0xFF, (x.lpfQ >> 8) & 0xFF);
            put(p, 4, hpf & 0xFF, (hpf >> 8) & 0xFF, x.hpfQ & 0xFF, (x.hpfQ >> 8) & 0xFF);
        }
        put(p, 2, clamp(st.fader, 0, EqModel.SITE_MAX), clamp(st.balance, 0, EqModel.SITE_MAX));
        put(p, 2, 10, 0);
        for (int a = 0; a < EqModel.DBB_AREAS; a++) {
            EqModel.BassBoost b = a < st.bassBoosts.size() ? st.bassBoosts.get(a) : new EqModel.BassBoost();
            int hz = dbbHz(b.freq);
            put(p, 5, hz & 0xFF, (hz >> 8) & 0xFF, 20, 0, clamp(b.gain, 0, EqModel.DBB_GAIN_MAX));
        }
        put(p, 4, 3, st.round3d ? 1 : 0, 44, 1);
        return frame(OTHER_ALL, p.toByteArray());
    }

    /** High-pass of one crossover area (0 FL/FR, 1 RL/RR, 2 center, 3 sub). */
    public static byte[] highPass(int area, int index, int q) {
        int hz = pfHz(index);
        return frame(FILTER, bytes(area, 1, hz & 0xFF, (hz >> 8) & 0xFF, q & 0xFF, (q >> 8) & 0xFF));
    }

    /** Low-pass of one crossover area. */
    public static byte[] lowPass(int area, int index, int q) {
        int hz = pfHz(index);
        return frame(FILTER, bytes(area, 0, hz & 0xFF, (hz >> 8) & 0xFF, q & 0xFF, (q >> 8) & 0xFF));
    }

    /** Bass boost of one area (0 FL/FR, 1 RL/RR, 2 sub/center); gain 0 = off. */
    public static byte[] bassBoost(int area, int freqIndex, int gain) {
        int hz = dbbHz(freqIndex);
        return frame(BASS_BOOST, bytes(area, hz & 0xFF, (hz >> 8) & 0xFF, 20, 0,
            clamp(gain, 0, EqModel.DBB_GAIN_MAX)));
    }

    public static byte[] round3d(boolean on) {
        return frame(ROUND_3D, bytes(on ? 1 : 0, 44, 1));
    }

    private static int pfHz(int index) {
        return EqModel.PF_HZ[clamp(index, 0, EqModel.PF_MAX)];
    }

    private static int dbbHz(int index) {
        return EqModel.DBB_HZ[clamp(index, 0, EqModel.DBB_HZ.length - 1)];
    }

    private static void put(java.io.ByteArrayOutputStream out, int... values) {
        for (int v : values) out.write(v);
    }

    public static byte[] level(int channel, int attenuation) {
        return frame(LEVEL, bytes(channel, clamp(attenuation, 0, 15)));
    }

    public static byte[] phase(int channel, boolean inverted) {
        return frame(PHASE, bytes(channel, inverted ? 1 : 0));
    }

    public static byte[] delay(int channel, int tenthsMs) {
        int d = clamp(tenthsMs, 0, 200);
        return frame(DELAY, bytes(channel, d & 0xFF, (d >> 8) & 0xFF));
    }

    public static int gainToWire(int db, int eqMax) {
        int g = clamp(db, EqModel.EQ32_MIN_DB, EqModel.EQ32_MAX_DB);
        return clamp(Math.round((g + 12) * eqMax / 24f), 0, eqMax);
    }

    public static int wireToGain(int wire, int eqMax) {
        return clamp((int) Math.ceil(wire * 24.0 / eqMax) - 12, EqModel.EQ32_MIN_DB, EqModel.EQ32_MAX_DB);
    }

    public static String toHex(byte[] frame) {
        StringBuilder sb = new StringBuilder(frame.length * 2);
        for (byte b : frame) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
