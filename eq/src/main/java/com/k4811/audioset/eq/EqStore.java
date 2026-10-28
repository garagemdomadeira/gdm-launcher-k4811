package com.k4811.audioset.eq;

import com.k4811.common.eq.EqModel;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Local EQ persistence (required on the ARM DSP). */
public final class EqStore {

    public static final class ArmCustom {
        public final int[] frequencies;
        public final int[] q;
        public final int[] gains;

        ArmCustom(int[] frequencies, int[] q, int[] gains) {
            this.frequencies = frequencies;
            this.q = q;
            this.gains = gains;
        }
    }

    private final SharedPreferences prefs;

    public EqStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("eq", Context.MODE_PRIVATE);
    }

    public EqModel.State load() {
        EqModel.State d = new EqModel.State();
        String mode = prefs.getString("backend_mode", null);
        if (mode != null) {
            try {
                d.backendMode = EqModel.BackendMode.valueOf(mode);
            } catch (Exception ignored) {
            }
        }
        d.preset = EqModel.Preset.fromKey(prefs.getString("preset", EqModel.Preset.STANDARD.fileKey));
        d.loudness = prefs.getBoolean("loudness", false);
        d.fader = prefs.getInt("fader", EqModel.SITE_CENTER);
        d.balance = prefs.getInt("balance", EqModel.SITE_CENTER);
        int[] mcu = ints("mcu_bands", 10);
        if (mcu != null) d.mcuBands = mcu;
        int[] gains = ints("arm_gains", EqModel.EQ32_BANDS);
        if (gains != null) d.armGainsDb = gains;
        int[] freqs = ints("arm_freqs", EqModel.EQ32_BANDS);
        if (freqs != null) d.armFrequencies = freqs;
        int[] q = ints("arm_q", EqModel.EQ32_BANDS);
        if (q != null) d.armQ = q;
        List<EqModel.Channel> channels = loadChannels();
        if (channels != null) d.channels = channels;
        d.applyOnBoot = prefs.getBoolean("apply_on_boot", false);
        d.influence = prefs.getBoolean("influence", true);
        d.armSimple = prefs.getBoolean("arm_simple", true);
        return d;
    }

    public void save(EqModel.State s) {
        prefs.edit()
            .putString("backend_mode", s.backendMode.name())
            .putString("preset", s.preset.fileKey)
            .putBoolean("loudness", s.loudness)
            .putInt("fader", s.fader)
            .putInt("balance", s.balance)
            .putString("mcu_bands", join(s.mcuBands))
            .putString("arm_gains", join(s.armGainsDb))
            .putString("arm_freqs", join(s.armFrequencies))
            .putString("arm_q", join(s.armQ))
            .putString("channels", joinChannels(s.channels))
            .putBoolean("apply_on_boot", s.applyOnBoot)
            .putBoolean("influence", s.influence)
            .putBoolean("arm_simple", s.armSimple)
            .apply();
    }

    public void saveMcuCustom(int[] bands) {
        prefs.edit().putString("mcu_custom", join(bands)).apply();
    }

    public int[] loadMcuCustom(int max) {
        int[] custom = ints("mcu_custom", 10);
        if (custom == null) return EqModel.flat10(max);
        for (int i = 0; i < custom.length; i++) {
            if (custom[i] < 0) custom[i] = 0;
            if (custom[i] > max) custom[i] = max;
        }
        return custom;
    }

    public void saveArmCustom(int[] gains, int[] freqs, int[] q) {
        prefs.edit()
            .putString("arm_custom_gains", join(gains))
            .putString("arm_custom_freqs", join(freqs))
            .putString("arm_custom_q", join(q))
            .apply();
    }

    public ArmCustom loadArmCustom() {
        int[] g = ints("arm_custom_gains", EqModel.EQ32_BANDS);
        if (g == null) return null;
        int[] f = ints("arm_custom_freqs", EqModel.EQ32_BANDS);
        if (f == null) f = Arrays.copyOf(EqModel.EQ32_FREQS, EqModel.EQ32_BANDS);
        int[] q = ints("arm_custom_q", EqModel.EQ32_BANDS);
        if (q == null) {
            q = new int[EqModel.EQ32_BANDS];
            Arrays.fill(q, EqModel.EQ32_DEFAULT_Q);
        }
        return new ArmCustom(f, q, g);
    }

    private int[] ints(String key, int size) {
        String raw = prefs.getString(key, null);
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length != size) return null;
        int[] out = new int[size];
        try {
            for (int i = 0; i < size; i++) out[i] = Integer.parseInt(parts[i].trim());
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private List<EqModel.Channel> loadChannels() {
        String raw = prefs.getString("channels", null);
        if (raw == null) return null;
        String[] parts = raw.split(";");
        if (parts.length != EqModel.Speaker.COUNT) return null;
        List<EqModel.Channel> list = new ArrayList<>(EqModel.Speaker.COUNT);
        for (String part : parts) {
            String[] p = part.split(",");
            if (p.length != 4) return null;
            try {
                list.add(new EqModel.Channel(
                    Integer.parseInt(p[0]),
                    Boolean.parseBoolean(p[1]),
                    Boolean.parseBoolean(p[2]),
                    Integer.parseInt(p[3])));
            } catch (Exception e) {
                return null;
            }
        }
        return list;
    }

    private static String join(int[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private static String joinChannels(List<EqModel.Channel> channels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < channels.size(); i++) {
            if (i > 0) sb.append(';');
            EqModel.Channel c = channels.get(i);
            sb.append(c.level).append(',').append(c.muted).append(',')
                .append(c.inverted).append(',').append(c.delay);
        }
        return sb.toString();
    }
}
