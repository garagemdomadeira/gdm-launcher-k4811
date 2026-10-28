package com.k4811.audioset.eq;

import com.k4811.common.eq.SettingClient;

import com.k4811.common.eq.EqModel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.nwd.setting.service.SettingFeature;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chooses MCU vs ARM DSP, keeps the state and translates each adjustment.
 * Binder/DSP run off the main thread.
 */
public final class EqController {

    public interface Listener {
        void onState(EqModel.State state);

        void onLog(List<String> lines);
    }

    private static final String TAG = "K4811Eq";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final EqStore store;
    private final ArmDsp arm;
    private final SettingClient mcu;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final EqModel.State state = new EqModel.State();
    private final List<String> logLines = new ArrayList<>();
    private Listener listener;
    private boolean released;
    /** Reaplicação do boot/ACC pendente (ver reapplySaved); null quando não há. */
    private Runnable bootDone;
    /** Detecção do caminho de áudio (ARM ou MCU) concluída. */
    private volatile boolean backendKnown;
    /** Boot/ACC: em ARM o DSP volta ao padrão; em MCU só se "aplicar ao ligar" estiver ligado. */
    private static final long BOOT_DELAY_MS = 3000;
    private static final long BOOT_TIMEOUT_MS = 20000;

    public EqController(Context context) {
        this.context = context.getApplicationContext();
        this.store = new EqStore(this.context);
        this.arm = new ArmDsp(this::appendLog);
        this.mcu = new SettingClient(this.context, new SettingClient.Callbacks() {
            @Override
            public void log(String msg) {
                appendLog(msg);
            }

            @Override
            public void onConnection(EqModel.Connection connection) {
                state.mcuConnection = connection;
                publish();
            }

            @Override
            public void onConnected(SettingFeature feature) {
                // No boot/ACC vale o que foi salvo: não lê o estado da MCU por cima dele.
                if (bootDone != null) {
                    main.post(EqController.this::maybeBootReapply);
                    return;
                }
                worker.execute(() -> syncFromMcu(feature));
            }

            @Override
            public void onAudioParam(int type, int value) {
                main.post(() -> {
                    switch (type) {
                        case SettingClient.AUDIO_EQ_PRESET:
                            state.preset = EqModel.Preset.fromMcuCode(value);
                            break;
                        case SettingClient.AUDIO_LOUDNESS:
                            state.loudness = value == 1;
                            break;
                        case SettingClient.AUDIO_FADER:
                            state.fader = clamp(value, 0, EqModel.SITE_MAX);
                            break;
                        case SettingClient.AUDIO_BALANCE:
                            state.balance = clamp(value, 0, EqModel.SITE_MAX);
                            break;
                        default:
                            return;
                    }
                    publish();
                });
            }

            @Override
            public void onDspAudio(int type, int value) {
                int band = type - 11;
                if (band < 0 || band > 9) return;
                main.post(() -> {
                    state.mcuBands[band] = clamp(value, 0, state.mcuMax);
                    publish();
                });
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) {
            listener.onState(state);
            listener.onLog(new ArrayList<>(logLines));
        }
    }

    public EqModel.State state() {
        return state;
    }

    public void start() {
        EqModel.State saved = store.load();
        copyState(saved);
        worker.execute(() -> {
            EqModel.HardwareInfo info = arm.detect();
            appendLog("Detection: chip=" + info.chipLabel() + ", DTS=" + info.dts
                + ", method=" + (info.hardwareMethod == null ? "—" : info.hardwareMethod)
                + ", config=" + info.configPath);
            int eqMax = arm.readEqMax(info.configPath);
            EqModel.Backend backend = resolveBackend(state.backendMode, info);
            appendLog("Audio path: " + backend.label);
            state.detection = info;
            state.backend = backend;
            state.armEqMax = eqMax;
            if (backend == EqModel.Backend.MCU) {
                int max = 6;
                try {
                    int v = Settings.System.getInt(context.getContentResolver(), "dsp_max_eq");
                    if (v > 0) max = v;
                } catch (Exception ignored) {
                }
                state.mcuMax = max;
            }
            backendKnown = true;
            publish();
            main.post(this::maybeBootReapply);
        });
        mcu.connect();
    }

    public void release() {
        released = true;
        listener = null;
        mcu.release();
        worker.shutdown();
    }

    public void selectPreset(EqModel.Preset preset) {
        if (state.isArm()) {
            ArmCurve curve = armCurveFor(preset);
            state.preset = preset;
            state.armGainsDb = curve.gains;
            state.armFrequencies = curve.frequencies;
            state.armQ = curve.q;
            persistAndPublish();
            sendArmEq();
        } else {
            int[] bands = preset == EqModel.Preset.CUSTOM
                ? store.loadMcuCustom(state.mcuMax)
                : EqModel.curve10(preset, state.mcuMax);
            state.preset = preset;
            if (bands != null) state.mcuBands = bands;
            persistAndPublish();
            mcuCall("setAudioParam(5, " + preset.mcuCode + ")", f ->
                f.setAudioParam(SettingClient.AUDIO_EQ_PRESET, preset.mcuCode));
            if (preset == EqModel.Preset.CUSTOM && bands != null) {
                for (int i = 0; i < bands.length; i++) {
                    final int band = i;
                    final int v = bands[i];
                    mcuCall("setDspAudio(" + EqModel.dspType(band) + ", " + v + ")", f ->
                        f.setDspAudio((byte) EqModel.dspType(band), (byte) v));
                }
            }
        }
    }

    public void setMcuBand(int band, int value) {
        int v = clamp(value, 0, state.mcuMax);
        if (state.mcuBands[band] == v && state.preset == EqModel.Preset.CUSTOM) return;
        state.mcuBands[band] = v;
        state.preset = EqModel.Preset.CUSTOM;
        store.saveMcuCustom(state.mcuBands);
        persistAndPublish();
        mcuCall("setDspAudio(" + EqModel.dspType(band) + ", " + v + ")", f ->
            f.setDspAudio((byte) EqModel.dspType(band), (byte) v));
    }

    public void setArmBand(int band, int db) {
        int g = clamp(db, EqModel.EQ32_MIN_DB, EqModel.EQ32_MAX_DB);
        if (state.armGainsDb[band] == g && state.preset == EqModel.Preset.CUSTOM) return;
        state.armGainsDb[band] = g;
        state.preset = EqModel.Preset.CUSTOM;
        store.saveArmCustom(state.armGainsDb, state.armFrequencies, state.armQ);
        persistAndPublish();
        int wire = DspFrames.gainToWire(g, state.armEqMax);
        armSend(DspFrames.oneEq(band, state.armFrequencies[band], state.armQ[band], wire), false);
    }

    public void resetEq() {
        selectPreset(EqModel.Preset.STANDARD);
    }

    public void setLoudness(boolean on) {
        state.loudness = on;
        persistAndPublish();
        if (state.isArm()) {
            armSend(DspFrames.loudness(on), false);
        } else {
            mcuCall("setAudioParam(17, " + (on ? 1 : 0) + ")", f ->
                f.setAudioParam(SettingClient.AUDIO_LOUDNESS, on ? 1 : 0));
        }
    }

    public void setSite(int fader, int balance) {
        int f = clamp(fader, 0, EqModel.SITE_MAX);
        int b = clamp(balance, 0, EqModel.SITE_MAX);
        if (state.fader == f && state.balance == b) return;
        int oldF = state.fader;
        int oldB = state.balance;
        state.fader = f;
        state.balance = b;
        persistAndPublish();
        if (state.isArm()) {
            armSend(DspFrames.site(f, b), false);
        } else {
            if (oldF != f) {
                mcuCall("setAudioParam(12, " + f + ")", feat ->
                    feat.setAudioParam(SettingClient.AUDIO_FADER, f));
            }
            if (oldB != b) {
                mcuCall("setAudioParam(13, " + b + ")", feat ->
                    feat.setAudioParam(SettingClient.AUDIO_BALANCE, b));
            }
        }
    }

    public void setChannelLevel(int ch, int level) {
        EqModel.Channel c = state.channels.get(ch);
        c.level = clamp(level, 0, 14);
        persistAndPublish();
        if (!c.muted) armSend(DspFrames.level(ch, 14 - c.level), false);
    }

    public void setChannelMute(int ch, boolean muted) {
        EqModel.Channel c = state.channels.get(ch);
        c.muted = muted;
        persistAndPublish();
        armSend(DspFrames.level(ch, muted ? 15 : 14 - c.level), false);
    }

    public void setChannelPhase(int ch, boolean inverted) {
        state.channels.get(ch).inverted = inverted;
        persistAndPublish();
        armSend(DspFrames.phase(ch, inverted), false);
    }

    public void setChannelDelay(int ch, int tenthsMs) {
        state.channels.get(ch).delay = clamp(tenthsMs, 0, 140);
        persistAndPublish();
        armSend(DspFrames.delay(ch, state.channels.get(ch).delay), false);
    }

    public void resetChannels() {
        state.channels = EqModel.defaultChannels();
        persistAndPublish();
        reapplyChannels();
    }

    /**
     * Boot ou o carro saindo do ACC: carrega o som salvo e reaplica (como o DspServices do app
     * original), 3 s depois de saber o caminho de áudio e, no caminho MCU, de conectar no
     * SettingService. No DSP ARM sempre reaplica (ele volta ao padrão); na MCU, só com "aplicar ao
     * ligar". Chama {@code done} no fim (ou após 20 s sem conseguir).
     */
    public void reapplySaved(Runnable done) {
        bootDone = done;
        start();
        main.postDelayed(() -> finishBoot("tempo esgotado"), BOOT_TIMEOUT_MS);
    }

    private void maybeBootReapply() {
        if (bootDone == null || !backendKnown) return;
        if (!state.isArm() && mcu.feature() == null) return; // espera o SettingService
        final boolean apply = state.isArm() || state.applyOnBoot;
        final Runnable done = bootDone;
        bootDone = null;
        main.postDelayed(() -> {
            if (apply) reapplyAll();
            else appendLog("Boot: caminho MCU sem \"aplicar ao ligar\"; nada reaplicado");
            // Dá tempo de os comandos saírem pelo worker antes de soltar a conexão.
            worker.execute(() -> main.post(() -> {
                mcu.release();
                done.run();
            }));
        }, BOOT_DELAY_MS);
    }

    private void finishBoot(String why) {
        if (bootDone == null) return;
        appendLog("Boot: " + why);
        Runnable done = bootDone;
        bootDone = null;
        mcu.release();
        done.run();
    }

    public void reapplyAll() {
        appendLog("Reapplying all sound settings (" + state.backend.label + ")");
        if (state.isArm()) {
            sendArmEq();
            armSend(DspFrames.loudness(state.loudness), false);
            armSend(DspFrames.site(state.fader, state.balance), false);
            reapplyChannels();
        } else {
            final EqModel.Preset p = state.preset;
            mcuCall("setAudioParam(5, " + p.mcuCode + ")", f ->
                f.setAudioParam(SettingClient.AUDIO_EQ_PRESET, p.mcuCode));
            if (p == EqModel.Preset.CUSTOM) {
                for (int i = 0; i < state.mcuBands.length; i++) {
                    final int band = i;
                    final int v = state.mcuBands[i];
                    mcuCall("setDspAudio(" + EqModel.dspType(band) + ", " + v + ")", f ->
                        f.setDspAudio((byte) EqModel.dspType(band), (byte) v));
                }
            }
            final boolean loud = state.loudness;
            final int fader = state.fader;
            final int balance = state.balance;
            mcuCall("setAudioParam(17)", f -> f.setAudioParam(SettingClient.AUDIO_LOUDNESS, loud ? 1 : 0));
            mcuCall("setAudioParam(12/13)", f -> {
                f.setAudioParam(SettingClient.AUDIO_FADER, fader);
                f.setAudioParam(SettingClient.AUDIO_BALANCE, balance);
            });
        }
    }

    public void setBackendMode(EqModel.BackendMode mode) {
        state.backendMode = mode;
        state.backend = resolveBackend(mode, state.detection);
        persistAndPublish();
        appendLog("Mode: " + mode.name() + " → " + state.backend.label);
        SettingFeature f = mcu.feature();
        if (f != null) worker.execute(() -> syncFromMcu(f));
    }

    /**
     * Several MCU bands at once (a band dragged with Influence on): saves once and sends only the
     * bands that changed.
     */
    public void setMcuBands(int[] values) {
        boolean changed = state.preset != EqModel.Preset.CUSTOM;
        int[] next = state.mcuBands.clone();
        for (int i = 0; i < next.length && i < values.length; i++) {
            int v = clamp(values[i], 0, state.mcuMax);
            if (next[i] != v) {
                next[i] = v;
                changed = true;
                final int band = i;
                mcuCall("setDspAudio(" + EqModel.dspType(band) + ", " + v + ")", f ->
                    f.setDspAudio((byte) EqModel.dspType(band), (byte) v));
            }
        }
        if (!changed) return;
        state.mcuBands = next;
        state.preset = EqModel.Preset.CUSTOM;
        store.saveMcuCustom(state.mcuBands);
        persistAndPublish();
    }

    /** Several DSP bands at once, like {@link #setMcuBands}. */
    public void setArmBands(int[] gainsDb) {
        boolean changed = state.preset != EqModel.Preset.CUSTOM;
        int[] next = state.armGainsDb.clone();
        for (int i = 0; i < next.length && i < gainsDb.length; i++) {
            int g = clamp(gainsDb[i], EqModel.EQ32_MIN_DB, EqModel.EQ32_MAX_DB);
            if (next[i] != g) {
                next[i] = g;
                changed = true;
                int wire = DspFrames.gainToWire(g, state.armEqMax);
                armSend(DspFrames.oneEq(i, state.armFrequencies[i], state.armQ[i], wire), false);
            }
        }
        if (!changed) return;
        state.armGainsDb = next;
        state.preset = EqModel.Preset.CUSTOM;
        store.saveArmCustom(state.armGainsDb, state.armFrequencies, state.armQ);
        persistAndPublish();
    }

    public void setInfluence(boolean on) {
        state.influence = on;
        persistAndPublish();
    }

    /** ARM DSP: switch between the simple 7-band view and the full 32-band view. */
    public void setArmSimple(boolean simple) {
        state.armSimple = simple;
        persistAndPublish();
    }

    /**
     * ARM DSP simple view: one of the 7 anchor bands moved to {@code db}. Rebuilds the 32-band gains
     * by linear interpolation between the anchors and sends only the bands that changed.
     */
    public void setArmAnchor(int anchor, int db) {
        int g = clamp(db, EqModel.EQ32_MIN_DB, EqModel.EQ32_MAX_DB);
        int[] gains7 = EqModel.anchorGains(state.armGainsDb);
        if (gains7[anchor] == g && state.preset == EqModel.Preset.CUSTOM) return;
        gains7[anchor] = g;
        setArmBands(EqModel.interp7to32(gains7));
    }

    public void setApplyOnBoot(boolean on) {
        state.applyOnBoot = on;
        persistAndPublish();
    }

    public void reconnectMcu() {
        mcu.connect();
    }

    public void dumpMcuParams() {
        mcuCall("dump", f -> {
            StringBuilder audio = new StringBuilder();
            for (int t = 0; t <= 25; t++) {
                audio.append(t).append('=');
                try {
                    audio.append(f.getAudioParam(t));
                } catch (Exception e) {
                    audio.append('x');
                }
                audio.append(' ');
            }
            StringBuilder dsp = new StringBuilder();
            for (int t = 1; t <= 32; t++) {
                dsp.append(t).append('=');
                try {
                    dsp.append(f.getDspAudio((byte) t));
                } catch (Exception e) {
                    dsp.append('x');
                }
                dsp.append(' ');
            }
            appendLog("getAudioParam: " + audio);
            appendLog("getDspAudio: " + dsp);
        });
    }

    public void testArm() {
        armSend(DspFrames.site(state.fader, state.balance), true);
    }

    private void reapplyChannels() {
        for (int i = 0; i < state.channels.size(); i++) {
            EqModel.Channel c = state.channels.get(i);
            armSend(DspFrames.level(i, c.muted ? 15 : 14 - c.level), false);
            armSend(DspFrames.phase(i, c.inverted), false);
            armSend(DspFrames.delay(i, c.delay), false);
        }
    }

    private void syncFromMcu(SettingFeature f) {
        if (state.backend != EqModel.Backend.MCU || released) return;
        try {
            EqModel.Preset preset = EqModel.Preset.fromMcuCode(f.getAudioParam(SettingClient.AUDIO_EQ_PRESET));
            boolean loud = f.getAudioParam(SettingClient.AUDIO_LOUDNESS) == 1;
            int fader = f.getAudioParam(SettingClient.AUDIO_FADER);
            int balance = f.getAudioParam(SettingClient.AUDIO_BALANCE);
            int[] bands = new int[10];
            for (int i = 0; i < 10; i++) {
                bands[i] = clamp(f.getDspAudio((byte) EqModel.dspType(i)), 0, state.mcuMax);
            }
            appendLog("MCU: preset=" + preset + " loud=" + loud + " fader=" + fader
                + " balance=" + balance + " bands=" + Arrays.toString(bands));
            main.post(() -> {
                state.preset = preset;
                state.loudness = loud;
                state.fader = clamp(fader, 0, EqModel.SITE_MAX);
                state.balance = clamp(balance, 0, EqModel.SITE_MAX);
                state.mcuBands = bands;
                publish();
            });
        } catch (Exception e) {
            appendLog("Error reading MCU state: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static final class ArmCurve {
        final int[] frequencies;
        final int[] q;
        final int[] gains;

        ArmCurve(int[] frequencies, int[] q, int[] gains) {
            this.frequencies = frequencies;
            this.q = q;
            this.gains = gains;
        }
    }

    private ArmCurve armCurveFor(EqModel.Preset preset) {
        if (preset == EqModel.Preset.CUSTOM) {
            EqStore.ArmCustom custom = store.loadArmCustom();
            if (custom != null) return new ArmCurve(custom.frequencies, custom.q, custom.gains);
        }
        ArmDsp.PresetFile file = arm.readPresetFile(state.detection.configPath, preset.fileKey);
        if (file != null) {
            appendLog("Curve '" + preset.fileKey + "' read from " + file.path);
            int[] q = file.q != null ? file.q : fillQ();
            int[] gains = new int[file.gainsWire.length];
            for (int i = 0; i < gains.length; i++) {
                gains[i] = DspFrames.wireToGain(file.gainsWire[i], state.armEqMax);
            }
            return new ArmCurve(file.frequencies, q, gains);
        }
        int[] freqs = Arrays.copyOf(EqModel.EQ32_FREQS, EqModel.EQ32_BANDS);
        int[] db10 = EqModel.curveDb10(preset);
        if (db10 == null) db10 = new int[10];
        appendLog("Curve '" + preset.fileKey + "' has no file on the unit; using 10-band approximation");
        return new ArmCurve(freqs, fillQ(), EqModel.from10to32(db10, freqs));
    }

    private int[] fillQ() {
        int[] q = new int[EqModel.EQ32_BANDS];
        Arrays.fill(q, EqModel.EQ32_DEFAULT_Q);
        return q;
    }

    private void sendArmEq() {
        int[] wire = new int[state.armGainsDb.length];
        for (int i = 0; i < wire.length; i++) {
            wire[i] = DspFrames.gainToWire(state.armGainsDb[i], state.armEqMax);
        }
        armSend(DspFrames.allEq(state.armFrequencies, state.armQ, wire), false);
    }

    private void armSend(byte[] frame, boolean force) {
        if (!force && !state.isArm()) return;
        worker.execute(() -> arm.send(frame));
    }

    private interface Remote {
        void run(SettingFeature f) throws Exception;
    }

    private void mcuCall(String name, Remote remote) {
        if (state.isArm() && !"dump".equals(name)) return;
        worker.execute(() -> {
            SettingFeature f = mcu.feature();
            if (f == null) {
                appendLog("No connection to SettingService, ignored: " + name);
                return;
            }
            if (!"dump".equals(name)) appendLog("MCU → " + name);
            try {
                remote.run(f);
            } catch (Exception e) {
                appendLog("Error in " + name + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private EqModel.Backend resolveBackend(EqModel.BackendMode mode, EqModel.HardwareInfo info) {
        switch (mode) {
            case FORCE_MCU: return EqModel.Backend.MCU;
            case FORCE_ARM: return EqModel.Backend.ARM;
            default: return info.hasArmDsp() ? EqModel.Backend.ARM : EqModel.Backend.MCU;
        }
    }

    private void copyState(EqModel.State s) {
        state.backendMode = s.backendMode;
        state.preset = s.preset;
        state.loudness = s.loudness;
        state.fader = s.fader;
        state.balance = s.balance;
        state.mcuBands = Arrays.copyOf(s.mcuBands, s.mcuBands.length);
        state.armGainsDb = Arrays.copyOf(s.armGainsDb, s.armGainsDb.length);
        state.armFrequencies = Arrays.copyOf(s.armFrequencies, s.armFrequencies.length);
        state.armQ = Arrays.copyOf(s.armQ, s.armQ.length);
        state.channels = new ArrayList<>();
        for (EqModel.Channel c : s.channels) state.channels.add(c.copy());
        state.applyOnBoot = s.applyOnBoot;
        state.influence = s.influence;
        state.armSimple = s.armSimple;
    }

    private void persistAndPublish() {
        store.save(state);
        publish();
    }

    private void publish() {
        if (released) return;
        main.post(() -> {
            if (listener != null) listener.onState(state);
        });
    }

    private void appendLog(String msg) {
        Log.d(TAG, msg);
        String line = timeFormat.format(new Date()) + "  " + msg;
        main.post(() -> {
            logLines.add(line);
            while (logLines.size() > 400) logLines.remove(0);
            if (listener != null) listener.onLog(new ArrayList<>(logLines));
        });
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
