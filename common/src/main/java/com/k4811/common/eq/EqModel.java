package com.k4811.common.eq;

import com.k4811.common.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Equalizer constants, presets and state. */
public final class EqModel {

    public enum Backend {
        MCU("MCU (SettingService)"),
        ARM("DSP ARM (AK7604/AK7735)");

        public final String label;

        Backend(String label) {
            this.label = label;
        }
    }

    public enum BackendMode {
        AUTO(R.string.eq_mode_auto),
        FORCE_MCU(R.string.eq_mode_force_mcu),
        FORCE_ARM(R.string.eq_mode_force_arm);

        public final int labelRes;

        BackendMode(int labelRes) {
            this.labelRes = labelRes;
        }
    }

    public enum Connection {
        IDLE, CONNECTING, CONNECTED, NOT_FOUND, DISCONNECTED
    }

    public enum Preset {
        STANDARD(R.string.eq_preset_standard, 1, "standard"),
        POP(R.string.eq_preset_pop, 2, "pop"),
        ROCK(R.string.eq_preset_rock, 3, "rock"),
        CLASSIC(R.string.eq_preset_classic, 4, "classic"),
        JAZZ(R.string.eq_preset_jazz, 5, "jazz"),
        CUSTOM(R.string.eq_preset_custom, 0, "customize");

        public final int labelRes;
        public final int mcuCode;
        public final String fileKey;

        Preset(int labelRes, int mcuCode, String fileKey) {
            this.labelRes = labelRes;
            this.mcuCode = mcuCode;
            this.fileKey = fileKey;
        }

        public static Preset fromMcuCode(int code) {
            for (Preset p : values()) {
                if (p.mcuCode == code) return p;
            }
            return CUSTOM;
        }

        public static Preset fromKey(String key) {
            if (key == null) return STANDARD;
            for (Preset p : values()) {
                if (p.fileKey.equals(key)) return p;
            }
            return CUSTOM;
        }
    }

    public static final class Speaker {
        /** Short speaker names: front left/right, rear left/right, center, subwoofer. */
        public static final int[] SHORT = {
            R.string.eq_speaker_fl, R.string.eq_speaker_fr, R.string.eq_speaker_rl,
            R.string.eq_speaker_rr, R.string.eq_speaker_c, R.string.eq_speaker_sub
        };
        public static final int COUNT = SHORT.length;

        private Speaker() {
        }
    }

    public static final class Channel {
        public int level = 14;
        public boolean muted;
        public boolean inverted;
        public int delay;

        public Channel() {
        }

        public Channel(int level, boolean muted, boolean inverted, int delay) {
            this.level = level;
            this.muted = muted;
            this.inverted = inverted;
            this.delay = delay;
        }

        public Channel copy() {
            return new Channel(level, muted, inverted, delay);
        }
    }

    public static final class Seat {
        public final int labelRes;
        public final int fader;
        public final int balance;

        public Seat(int labelRes, int fader, int balance) {
            this.labelRes = labelRes;
            this.fader = fader;
            this.balance = balance;
        }
    }

    public static final int SITE_CENTER = 7;
    public static final int SITE_MAX = 14;

    public static final List<Seat> SEATS = Collections.unmodifiableList(Arrays.asList(
        new Seat(R.string.eq_seat_driver, 3, 3),
        new Seat(R.string.eq_seat_passenger, 3, 11),
        new Seat(R.string.eq_seat_rear, 12, 7),
        new Seat(R.string.eq_seat_center, 7, 7)
    ));

    public static final String[] EQ10_LABELS = {
        "32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"
    };
    public static final int[] EQ10_CENTERS = {
        32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
    };

    public static final int EQ32_BANDS = 32;
    public static final int EQ32_MIN_DB = -12;
    public static final int EQ32_MAX_DB = 12;
    public static final int EQ32_DEFAULT_Q = 20;
    public static final int[] EQ32_FREQS = {
        20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630,
        800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 18000, 20000
    };

    /**
     * Simple 7-band view over the 32-band ARM DSP. The 7 sliders sit on these 32-band indices
     * (the grid bands nearest 60/150/400/1k/2.5k/6k/15k Hz); the physical frequency shown is the one
     * at that index in {@code armFrequencies}. The 25 bands between anchors follow by linear
     * interpolation of the gains (see {@link #interp7to32}); the DSP keeps its full resolution.
     */
    public static final int EQ7_BANDS = 7;
    public static final int[] EQ7_ANCHORS = {5, 9, 13, 17, 21, 25, 29};

    private static final int[][] EQ10_CURVES = {
        {3, 3, 3, 3, 3, 3, 3, 3, 3, 3}, // standard
        {2, 3, 2, 3, 4, 4, 5, 3, 2, 2}, // pop
        {6, 4, 5, 6, 3, 2, 3, 4, 5, 4}, // rock
        {4, 5, 6, 5, 2, 3, 3, 4, 4, 5}, // classic
        {5, 4, 5, 6, 4, 1, 1, 3, 4, 5}, // jazz
    };

    public static int dspType(int band) {
        return 11 + band;
    }

    public static int[] curve10(Preset preset, int max) {
        int idx = curveIndex(preset);
        if (idx < 0) return null;
        int[] out = new int[10];
        for (int i = 0; i < 10; i++) {
            out[i] = Math.round(EQ10_CURVES[idx][i] * max / 6f);
            if (out[i] < 0) out[i] = 0;
            if (out[i] > max) out[i] = max;
        }
        return out;
    }

    public static int[] curveDb10(Preset preset) {
        int idx = curveIndex(preset);
        if (idx < 0) return null;
        int[] out = new int[10];
        for (int i = 0; i < 10; i++) out[i] = (EQ10_CURVES[idx][i] - 3) * 4;
        return out;
    }

    private static int curveIndex(Preset preset) {
        switch (preset) {
            case STANDARD: return 0;
            case POP: return 1;
            case ROCK: return 2;
            case CLASSIC: return 3;
            case JAZZ: return 4;
            default: return -1;
        }
    }

    public static int[] flat10(int max) {
        int[] out = new int[10];
        Arrays.fill(out, max / 2);
        return out;
    }

    public static int[] flat32() {
        return new int[EQ32_BANDS];
    }

    public static int[] from10to32(int[] db10, int[] frequencies) {
        int[] out = new int[frequencies.length];
        for (int i = 0; i < frequencies.length; i++) {
            int f = frequencies[i];
            int nearest = 0;
            double best = Double.MAX_VALUE;
            for (int j = 0; j < EQ10_CENTERS.length; j++) {
                double d = Math.abs(Math.log((double) EQ10_CENTERS[j] / f));
                if (d < best) {
                    best = d;
                    nearest = j;
                }
            }
            out[i] = db10[nearest];
        }
        return out;
    }

    /** The gains of the 7 anchor bands, read from a full 32-band gain array. */
    public static int[] anchorGains(int[] gains32) {
        int[] out = new int[EQ7_BANDS];
        for (int i = 0; i < EQ7_BANDS; i++) out[i] = gains32[EQ7_ANCHORS[i]];
        return out;
    }

    /**
     * Spreads 7 anchor gains over the 32 DSP bands: each anchor keeps its value and the bands between
     * two anchors ramp linearly from one to the other (the midpoint is their average). Bands below the
     * first anchor and above the last repeat the end value.
     */
    public static int[] interp7to32(int[] gains7) {
        int[] out = new int[EQ32_BANDS];
        int first = EQ7_ANCHORS[0];
        int last = EQ7_ANCHORS[EQ7_BANDS - 1];
        for (int i = 0; i < EQ32_BANDS; i++) {
            if (i <= first) {
                out[i] = gains7[0];
            } else if (i >= last) {
                out[i] = gains7[EQ7_BANDS - 1];
            } else {
                int seg = 0;
                while (seg < EQ7_BANDS - 1 && EQ7_ANCHORS[seg + 1] < i) seg++;
                int a = EQ7_ANCHORS[seg];
                int b = EQ7_ANCHORS[seg + 1];
                int ga = gains7[seg];
                int gb = gains7[seg + 1];
                out[i] = Math.round(ga + (gb - ga) * (float) (i - a) / (b - a));
            }
        }
        return out;
    }

    public static String freqLabel(int freq) {
        if (freq >= 1000) {
            float k = freq / 1000f;
            if (k == (int) k) return ((int) k) + "k";
            return String.format(Locale.US, "%.1fk", k);
        }
        return String.valueOf(freq);
    }

    public static List<Channel> defaultChannels() {
        List<Channel> list = new ArrayList<>(Speaker.COUNT);
        for (int i = 0; i < Speaker.COUNT; i++) list.add(new Channel());
        return list;
    }

    public static final class HardwareInfo {
        public boolean ak7604;
        public boolean ak7735;
        public boolean dts;
        public String hardwareMethod;
        public String configPath = "/config";

        public boolean hasArmDsp() {
            return ak7604 || ak7735;
        }

        public String chipLabel() {
            if (ak7604) return "AK7604";
            if (ak7735) return "AK7735";
            return "none";
        }
    }

    public static final class State {
        public Backend backend = Backend.MCU;
        public BackendMode backendMode = BackendMode.AUTO;
        public HardwareInfo detection = new HardwareInfo();
        public Connection mcuConnection = Connection.IDLE;
        public Preset preset = Preset.STANDARD;
        public boolean loudness;
        public int fader = SITE_CENTER;
        public int balance = SITE_CENTER;
        public int mcuMax = 6;
        public int[] mcuBands = flat10(6);
        public int[] armFrequencies = Arrays.copyOf(EQ32_FREQS, EQ32_BANDS);
        public int[] armQ = new int[EQ32_BANDS];
        public int[] armGainsDb = flat32();
        public int armEqMax = 30;
        public List<Channel> channels = defaultChannels();
        public boolean applyOnBoot;
        /** Moving a band also moves its 2 neighbours on each side (50% and 25% of the change). */
        public boolean influence = true;
        /** ARM DSP only: show the simple 7-band view (default) instead of all 32 bands. */
        public boolean armSimple = true;

        public State() {
            Arrays.fill(armQ, EQ32_DEFAULT_Q);
        }

        public boolean isArm() {
            return backend == Backend.ARM;
        }
    }

    private EqModel() {
    }
}
