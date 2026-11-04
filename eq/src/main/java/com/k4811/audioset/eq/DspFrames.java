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

    /** Crossover filters sent neutral by the stock app (DspAdjust3 defaults: no cut on front/rear). */
    private static final int HZ_20 = 20;
    private static final int HZ_160 = 160;
    private static final int HZ_20K = 20000;
    private static final int FILTER_Q = 10;
    /** Bass boost areas: stock default 40 Hz with gain 0 (off). */
    private static final int BASS_HZ = 40;

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
     * of the 4 areas, fader/balance, bass boost areas and 3D. Without it the DSP keeps its power-on
     * crossover, which sounds muffled and low. Crossover, bass boost and 3D go neutral, as the stock
     * defaults; channels and seat come from the saved state.
     */
    public static byte[] otherAll(java.util.List<EqModel.Channel> channels, int fader, int balance) {
        java.io.ByteArrayOutputStream p = new java.io.ByteArrayOutputStream(105);
        for (int i = 0; i < EqModel.Speaker.COUNT; i++) {
            EqModel.Channel c = i < channels.size() ? channels.get(i) : new EqModel.Channel();
            int d = clamp(c.delay, 0, 200);
            put(p, 5, 14 - clamp(c.level, 0, 14), c.inverted ? 1 : 0, c.muted ? 1 : 0, d & 0xFF, (d >> 8) & 0xFF);
        }
        // Areas 1-2 (front/rear): full range. Areas 3-4 (center/sub): 20-160 Hz. LPF first, then HPF.
        int[] lpf = {HZ_20K, HZ_20K, HZ_160, HZ_160};
        for (int a = 0; a < 4; a++) {
            put(p, 4, lpf[a] & 0xFF, (lpf[a] >> 8) & 0xFF, FILTER_Q, 0);
            put(p, 4, HZ_20, 0, FILTER_Q, 0);
        }
        put(p, 2, clamp(fader, 0, EqModel.SITE_MAX), clamp(balance, 0, EqModel.SITE_MAX));
        put(p, 2, 10, 0);
        for (int a = 0; a < 3; a++) {
            put(p, 5, BASS_HZ, 0, 20, 0, 0);
        }
        put(p, 4, 3, 0, 44, 1);
        return frame(OTHER_ALL, p.toByteArray());
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
