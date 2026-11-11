package com.k4811.radio;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.k4811.common.audio.PlayerHandoff;
import com.k4811.common.radio.Band;
import com.k4811.common.radio.BandRange;
import com.k4811.common.radio.Preset;
import com.k4811.common.radio.PresetStore;
import com.k4811.common.radio.RadioClient;
import com.k4811.common.radio.RadioState;
import com.k4811.common.radio.StationArt;
import com.k4811.common.radio.StationLogos;
import com.k4811.common.system.DevMode;
import com.k4811.common.ui.IconView;
import com.k4811.common.ui.IconView.Icon;
import com.k4811.common.ui.IconView.Style;
import com.k4811.radio.ui.PresetCardView;
import com.k4811.radio.ui.TunerDialView;
import com.k4811.common.ui.DockView;
import com.k4811.common.ui.DockNavigator;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Radio screen. Opened by the "Rádio" button on the home screen: on open, the radio is turned on
 * (becomes the audio source). The user can turn it off with Power; "Sair" turns it off and goes home.
 */
public class RadioActivity extends Activity implements RadioClient.Listener {

    private static final int PRESET_SLOTS = 12;
    /** How long Band must be held to start the automatic search. */
    private static final int AMS_HOLD_MS = 2000;

    private RadioClient radio;
    private RadioState state = new RadioState();
    private Integer preview;

    private TextView bandText;
    private TextView freqValue, freqUnit, stationName, radioText;
    private View stationArtFrame;
    private ImageView stationArt;
    private IconView stationArtPlaceholder;
    /** Current station's art file (e.g. FM_104.5), to avoid reloading on every service update. */
    private String artKey;
    private int artGen;
    private final ExecutorService artLoader = Executors.newSingleThreadExecutor();
    /** Logos baixados da internet (StationLogos): fila própria, a rede não segura a arte local. */
    private final ExecutorService logoLoader = Executors.newSingleThreadExecutor();
    private final ExecutorService presetLogoLoader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TunerDialView dial;
    private View toggleSt, toggleLoc;
    private View toggleStDot, toggleLocDot;
    private TextView banner;
    private final PresetCardView[] cards = new PresetCardView[PRESET_SLOTS];
    private IconView powerIcon;
    private View powerDot;
    /** Radio on = it's the active audio source. Off = source 0 (silence), app stays open. */
    private boolean radioOn = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        Ui.applyFont(findViewById(android.R.id.content));
        DockNavigator.attachDock(this, DockView.Item.RADIO);
        bindViews();

        radio = new RadioClient(this);
        radio.setListener(this);
        if (getIntent().getBooleanExtra("demo", false)) radio.loadDemo();
        else radio.connect();
        Touch.install(findViewById(android.R.id.content));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Tapping "Rádio" again on the home screen always turns the radio on.
        if (!radioOn) setRadioOn(true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The system recomputes density on every configuration change; reapply ours.
        Ui.applyDesignDensity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        // When coming to the foreground, the radio becomes the audio source (unless the user
        // turned it off with the Power button, or it already is the source: nothing to restart).
        if (radioOn && !PlayerHandoff.radioIsSource(this)) {
            PlayerHandoff.switchToRadio(this, radio::activateSource);
        }
        radioText.setSelected(true); // required for the marquee to scroll
        warnBrokenConfig();
    }

    /** radio.config.json edited by hand with a mistake: memories won't be saved until it's fixed. */
    private void warnBrokenConfig() {
        new Thread(() -> {
            if (!PresetStore.isBroken()) return;
            String path = String.valueOf(PresetStore.file());
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    Toast.makeText(this, getString(R.string.radio_config_invalid, path), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Returning from a dialog (or another app) brings the system bar back; hide it again.
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        radio.setListener(null);
        radio.release();
        artGen++;
        artLoader.shutdownNow();
        logoLoader.shutdownNow();
        presetLogoLoader.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ setup

    private void bindViews() {
        freqValue = text(R.id.freq_value);
        // Diagnostics without a button: hold the frequency for ~1 s (developer mode only).
        if (DevMode.enabled()) {
            freqValue.setOnLongClickListener(v -> {
                showDiagnostics();
                return true;
            });
        }
        freqUnit = text(R.id.freq_unit);
        stationName = text(R.id.station_name);
        stationArtFrame = findViewById(R.id.station_art_frame);
        stationArt = (ImageView) findViewById(R.id.station_art);
        stationArtPlaceholder = (IconView) findViewById(R.id.station_art_placeholder);
        stationArtPlaceholder.set(Icon.RADIO, Style.NONE).color(Ui.TEXT_DIM);
        radioText = text(R.id.radio_text);
        radioText.setTypeface(Ui.regular(this));

        dial = (TunerDialView) findViewById(R.id.dial);
        dial.setListener(new TunerDialView.Listener() {
            @Override
            public void onPreview(Integer frequency) {
                preview = frequency;
                renderFrequency();
            }

            @Override
            public void onTune(int frequency) {
                ensureOn();
                radio.tune(frequency);
            }
        });

        // ⏮ ⏭: previous/next saved station. ‹ ›: 1 frequency step (0.1 MHz on FM).
        icon(R.id.btn_seek_prev, Icon.SKIP_PREV, Style.NONE).setOnClickListener(v -> {
            ensureOn();
            radio.presetStep(false);
        });
        // ‹ › held: searches for the next station with signal in that direction (seek).
        IconView stepPrev = icon(R.id.btn_step_prev, Icon.CHEVRON_LEFT, Style.NONE);
        stepPrev.setOnClickListener(v -> {
            ensureOn();
            radio.step(false);
        });
        stepPrev.setOnLongClickListener(v -> {
            ensureOn();
            radio.seek(false);
            return true;
        });
        IconView stepNext = icon(R.id.btn_step_next, Icon.CHEVRON_RIGHT, Style.NONE);
        stepNext.setOnClickListener(v -> {
            ensureOn();
            radio.step(true);
        });
        stepNext.setOnLongClickListener(v -> {
            ensureOn();
            radio.seek(true);
            return true;
        });
        icon(R.id.btn_seek_next, Icon.SKIP_NEXT, Style.NONE).setOnClickListener(v -> {
            ensureOn();
            radio.presetStep(true);
        });
        // Reception toggles: fixed icon and label; the dot on the right lights up when the function is on.
        toggleSt = findViewById(R.id.toggle_st);
        toggleIcon(R.id.toggle_st_icon, Icon.STEREO);
        text(R.id.toggle_st_text).setText(R.string.radio_stereo);
        toggleStDot = findViewById(R.id.toggle_st_dot);
        toggleLoc = findViewById(R.id.toggle_loc);
        toggleIcon(R.id.toggle_loc_icon, Icon.TOWER_FAR);
        text(R.id.toggle_loc_text).setText(R.string.radio_distant);
        toggleLocDot = findViewById(R.id.toggle_loc_dot);
        toggleSt.setOnClickListener(v -> radio.setStereo(!state.stereoOn));
        toggleLoc.setOnClickListener(v -> radio.setLoc(!state.locOn));
        // Power (left of Stereo, same look): the dot lights up while the radio is on.
        powerIcon = (IconView) findViewById(R.id.btn_power_icon);
        toggleIcon(R.id.btn_power_icon, Icon.POWER);
        text(R.id.btn_power_text).setText(R.string.radio_power);
        powerDot = findViewById(R.id.btn_power_dot);
        findViewById(R.id.btn_power).setOnClickListener(v -> setRadioOn(!radioOn));
        // Band: each tap goes to the next one, back to FM1 after AM2.
        toggleIcon(R.id.btn_band_icon, Icon.RADIO);
        bandText = text(R.id.btn_band_text);
        bandText.setText(Band.label(state.band));
        findViewById(R.id.btn_band).setOnClickListener(v -> {
            ensureOn();
            int next = (Math.max(0, Math.min(Band.SELECTABLE.length - 1, state.band)) + 1) % Band.SELECTABLE.length;
            radio.selectBand(Band.SELECTABLE[next]);
        });
        // Hold for 2 s: searches the band and saves what it finds in the memories (AMS). The
        // system long click comes after ~0.5 s; the search only starts if the finger is still down.
        View band = findViewById(R.id.btn_band);
        band.setOnLongClickListener(v -> {
            handler.postDelayed(() -> {
                if (!v.isPressed()) return;
                Touch.haptic(v);
                ensureOn();
                radio.autoStore();
                Toast.makeText(this, getString(R.string.radio_ams_started, Band.label(state.band)), Toast.LENGTH_LONG).show();
            }, AMS_HOLD_MS - android.view.ViewConfiguration.getLongPressTimeout());
            return true; // no band change on release
        });
        // Procurar: varre a faixa e salva as estações encontradas nas memórias (AMS), igual a segurar Banda.
        toggleIcon(R.id.btn_search_icon, Icon.SIGNAL);
        text(R.id.btn_search_text).setText(R.string.radio_search);
        findViewById(R.id.btn_search).setOnClickListener(v -> {
            ensureOn();
            radio.autoStore();
            Toast.makeText(this, getString(R.string.radio_ams_started, Band.label(state.band)), Toast.LENGTH_LONG).show();
        });
        renderToggles();
        icon(R.id.btn_keypad, Icon.KEYBOARD, Style.NONE).setOnClickListener(v -> showKeypad());

        buildPresetGrid();

        banner = text(R.id.banner);
        renderPower();
    }

    /**
     * The 12 memories side by side, wider than tall, sized so that 6 and a bit of the 7th fit across (the
     * rest scroll sideways). Tap to listen, hold to save.
     */
    private void buildPresetGrid() {
        LinearLayout container = (LinearLayout) findViewById(R.id.presets_grid);
        int gap = Math.round(Ui.dp(this, 10));
        int width = getResources().getDisplayMetrics().widthPixels - Math.round(Ui.dp(this, 32));
        // Na vertical o presets_grid vem como LinearLayout vertical (layout-port): montamos uma grade
        // de 3 colunas (todas as 12 memorias visiveis, sem arrastar pro lado). Na horizontal fica a fila
        // unica que rola de lado (6 e um pedaco da 7a cabem).
        if (container.getOrientation() == LinearLayout.VERTICAL) {
            int cols = 3;
            int cardW = Math.round((width - (cols - 1) * gap) / (float) cols);
            int cardH = Math.round(cardW * 0.46f);
            LinearLayout rowView = null;
            for (int i = 0; i < PRESET_SLOTS; i++) {
                if (i % cols == 0) {
                    rowView = new LinearLayout(this);
                    rowView.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    if (i > 0) rlp.topMargin = gap;
                    container.addView(rowView, rlp);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
                if (i % cols != 0) lp.leftMargin = gap;
                rowView.addView(makePresetCard(i), lp);
            }
        } else {
            int cardW = Math.round((width - 6 * gap) / 6.4f);
            int cardH = Math.round(cardW * 0.62f);
            for (int i = 0; i < PRESET_SLOTS; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
                if (i > 0) lp.leftMargin = gap;
                container.addView(makePresetCard(i), lp);
            }
        }
    }

    /** Cria um card de memoria (indice 0..PRESET_SLOTS-1) com toque (tocar) e toque-longo (salvar). */
    private PresetCardView makePresetCard(final int index) {
        PresetCardView card = new PresetCardView(this);
        card.setOnClickListener(v -> {
            ensureOn();
            playPreset(index);
        });
        card.setOnLongClickListener(v -> {
            radio.savePreset(index);
            Toast.makeText(this, getString(R.string.radio_preset_saved, index + 1), Toast.LENGTH_SHORT).show();
            return true;
        });
        cards[index] = card;
        return card;
    }

    private IconView icon(int id, Icon icon, Style style) {
        return ((IconView) findViewById(id)).set(icon, style);
    }

    /** Icon inside a toggle: the toggle handles the tap. */
    private void toggleIcon(int id, Icon icon) {
        IconView v = (IconView) findViewById(id);
        v.set(icon, Style.NONE).color(Ui.TEXT);
        v.setClickable(false);
        v.setFocusable(false);
    }

    private TextView text(int id) {
        return (TextView) findViewById(id);
    }

    // ------------------------------------------------------------------ state → screen

    @Override
    public void onRadioState(RadioState s) {
        state = s;
        boolean fm = s.isFm();

        bandText.setText(Band.label(s.band));
        renderFrequency();
        bindArt(s.band, s.frequency);
        dial.setFrequency(s.frequency, s.range(), fm);

        if (radioOn) onRadioStateText();
        if (!TextUtils.equals(radioText.getText(), s.radioText)) radioText.setText(s.radioText);
        int rtVis = s.radioText.isEmpty() ? View.INVISIBLE : View.VISIBLE;
        findViewById(R.id.rt_left).setVisibility(rtVis);
        findViewById(R.id.rt_right).setVisibility(rtVis);

        renderToggles();
        renderPresets(s);
        renderBanner(s.connection);
    }

    private void renderToggles() {
        toggleStDot.setActivated(state.stereoOn);
        toggleLocDot.setActivated(!state.locOn); // "Distant" = LOC off
    }

    private void renderFrequency() {
        boolean fm = state.isFm();
        int f = preview != null ? preview : state.frequency;
        freqValue.setText(Band.formatValue(f, fm));
        freqValue.setTextColor(preview != null ? Ui.ACCENT : Ui.TEXT);
        freqUnit.setText(Band.unit(fm));
    }

    /** Looks for K4811/radio/<FM|AM>_<freq>.jpg for the tuned station; without a file, the radio placeholder. */
    private void bindArt(int band, int frequency) {
        String key = StationArt.baseName(band, frequency);
        if (key.equals(artKey)) return;
        artKey = key;
        int gen = ++artGen;
        int size = stationArtFrame.getLayoutParams().width;
        artLoader.execute(() -> {
            Bitmap bmp = StationArt.load(band, frequency, size);
            handler.post(() -> {
                if (gen != artGen) return;
                // The square always stays; without a picture, the radio icon shows through.
                stationArt.setImageBitmap(bmp);
                show(stationArtPlaceholder, bmp == null);
                if (bmp == null) fetchLogo(band, frequency, key);
            });
        });
    }

    /** Sem arquivo para a estação: tenta baixar o logo e, se chegar, mostra (se ainda for a mesma estação). */
    private void fetchLogo(int band, int frequency, String key) {
        String ps = state.psName;
        logoLoader.execute(() -> {
            if (!StationLogos.fetch(getApplicationContext(), band, frequency, ps)) return;
            handler.post(() -> {
                if (!key.equals(artKey)) return;
                artKey = null;
                bindArt(band, frequency);
            });
        });
    }

    private void renderPresets(RadioState s) {
        Preset[] presets = s.currentPresets();
        boolean fm = s.isFm();
        int logoSize = Math.round(Ui.dp(this, 96));
        for (int i = 0; i < PRESET_SLOTS; i++) {
            Preset p = i < presets.length ? presets[i] : null;
            boolean empty = p == null || p.isEmpty();
            cards[i].bind(i, empty ? "" : Band.formatValue(p.frequency, fm), empty, s.presetIndex == i + 1);
            if (empty) {
                cards[i].setLogo("", null);
            } else {
                // Mesma arte do quadro central (K4811/radio/<FM|AM>_<freq>): carrega do cache em background.
                // So mostra se o arquivo existe; nao dispara download (isso fica so para a estacao sintonizada).
                String key = StationArt.baseName(p.band, p.frequency);
                if (!key.equals(cards[i].logoKey())) {
                    final int slot = i;
                    final int band = p.band;
                    final int freq = p.frequency;
                    cards[i].setLogo(key, null);
                    presetLogoLoader.execute(() -> {
                        Bitmap bmp = StationArt.load(band, freq, logoSize);
                        if (bmp == null) return;
                        handler.post(() -> {
                            if (key.equals(cards[slot].logoKey())) cards[slot].setLogo(key, bmp);
                        });
                    });
                }
            }
        }
    }

    private void renderBanner(RadioState.Connection c) {
        String text;
        int color;
        switch (c) {
            case CONNECTING:
                text = getString(R.string.radio_banner_connecting);
                color = Ui.ACCENT;
                break;
            case NOT_FOUND:
                text = getString(R.string.radio_banner_not_found);
                color = Ui.DANGER;
                break;
            case DISCONNECTED:
                text = getString(R.string.radio_banner_disconnected);
                color = Ui.DANGER;
                break;
            default:
                banner.setVisibility(View.GONE);
                return;
        }
        banner.setText(text);
        banner.setTextColor(color);
        banner.setVisibility(View.VISIBLE);
    }

    private static void show(View v, boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        if (v.getVisibility() != vis) v.setVisibility(vis);
    }

    private void playPreset(int index) {
        Preset[] presets = state.currentPresets();
        if (index < presets.length) radio.playPreset(presets[index]);
    }

    // ------------------------------------------------------------------ actions

    // ------------------------------------------------------------------ on/off

    private void setRadioOn(boolean on) {
        radioOn = on;
        if (on) PlayerHandoff.switchToRadio(this, radio::activateSource);
        else radio.deactivateSource();
        renderPower();
    }

    /** Any tuning command turns the radio back on, like a real radio. */
    private void ensureOn() {
        if (!radioOn) setRadioOn(true);
    }

    private void renderPower() {
        powerDot.setActivated(radioOn);
        powerIcon.color(radioOn ? Ui.TEXT : Ui.TEXT_MUTED);
        float a = radioOn ? 1f : 0.35f;
        freqValue.setAlpha(a);
        freqUnit.setAlpha(a);
        radioText.setAlpha(a);
        dial.setAlpha(a);
        stationArtFrame.setAlpha(a);
        stationName.setAlpha(radioOn ? 1f : 0.6f);
        if (!radioOn) stationName.setText(R.string.radio_off);
        else onRadioStateText();
    }

    private void onRadioStateText() {
        stationName.setText(state.psName != null ? state.psName.toUpperCase() : Band.label(state.band));
    }

    private Dialog overlay(int layout) {
        Dialog d = new Dialog(this, R.style.OverlayDialog);
        View root = getLayoutInflater().inflate(layout, null);
        Ui.applyFont(root);
        d.setContentView(root);
        root.setOnClickListener(v -> d.dismiss()); // tapping outside the panel closes it
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        Ui.hideSystemBars(d.getWindow());
        d.show();
        return d;
    }

    private void showKeypad() {
        final Dialog d = overlay(R.layout.dialog_keypad);
        final boolean fm = state.isFm();
        final BandRange range = state.range();
        final StringBuilder input = new StringBuilder();
        final TextView display = (TextView) d.findViewById(R.id.keypad_input);
        final TextView ok = (TextView) d.findViewById(R.id.keypad_ok);
        ((TextView) d.findViewById(R.id.keypad_title)).setText(
            getString(R.string.radio_keypad_title, Band.label(state.band)));
        ((TextView) d.findViewById(R.id.keypad_range)).setText(
            Band.formatValue(range.min, fm) + " – " + Band.formatValue(range.max, fm) + " " + Band.unit(fm));

        final Runnable refresh = () -> {
            Integer v = parseInput(input.toString(), fm);
            boolean valid = v != null && v >= range.min && v <= range.max;
            display.setText(input.length() == 0 ? "—" : input);
            display.setTextColor(input.length() == 0 || valid ? Ui.TEXT : Ui.DANGER);
            ok.setActivated(valid);
        };

        LinearLayout keys = (LinearLayout) d.findViewById(R.id.keypad_keys);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {fm ? "." : "", "0", "⌫"}};
        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            for (String key : row) {
                TextView b = (TextView) getLayoutInflater().inflate(R.layout.keypad_key, line, false);
                b.setText(key);
                b.setTypeface(Ui.medium(this));
                if (key.isEmpty()) b.setVisibility(View.INVISIBLE);
                b.setOnClickListener(v -> {
                    if ("⌫".equals(key)) {
                        if (input.length() > 0) input.deleteCharAt(input.length() - 1);
                    } else if (".".equals(key)) {
                        if (input.indexOf(".") < 0) input.append('.');
                    } else if (input.length() < 6) {
                        input.append(key);
                    }
                    refresh.run();
                });
                line.addView(b);
            }
            keys.addView(line);
        }
        d.findViewById(R.id.keypad_cancel).setOnClickListener(v -> d.dismiss());
        ok.setOnClickListener(v -> {
            Integer value = parseInput(input.toString(), fm);
            if (value != null && value >= range.min && value <= range.max) {
                ensureOn();
                radio.tune(value);
                d.dismiss();
            }
        });
        refresh.run();
        Touch.install(d.getWindow().getDecorView());
    }

    private static Integer parseInput(String s, boolean fm) {
        if (s.isEmpty()) return null;
        try {
            double v = Double.parseDouble(s);
            return fm ? (int) Math.round(v * 100) : (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showDiagnostics() {
        final Dialog d = overlay(R.layout.dialog_diag);
        final TextView info = (TextView) d.findViewById(R.id.diag_info);
        final TextView log = (TextView) d.findViewById(R.id.diag_log);
        final ScrollView scroll = (ScrollView) d.findViewById(R.id.diag_scroll);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);

        final RadioClient.Listener stateListener = s -> {
            onRadioState(s);
            info.setText("Connection: " + s.connection + " · radio type: " + s.radioType + " · state: " + s.radioState
                + " · band: " + s.band + " · freq: " + s.frequency + " · range: " + s.range());
        };
        radio.setListener(stateListener);
        radio.setLogListener(lines -> {
            log.setText(TextUtils.join("\n", lines));
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
        d.setOnDismissListener(x -> {
            radio.setLogListener(null);
            radio.setListener(this);
        });

        d.findViewById(R.id.diag_close).setOnClickListener(v -> d.dismiss());
        d.findViewById(R.id.diag_seek_prev).setOnClickListener(v -> radio.seek(false));
        d.findViewById(R.id.diag_seek_next).setOnClickListener(v -> radio.seek(true));
        d.findViewById(R.id.diag_search_prev).setOnClickListener(v -> radio.search(false));
        d.findViewById(R.id.diag_search_next).setOnClickListener(v -> radio.search(true));
        d.findViewById(R.id.diag_intro).setOnClickListener(v -> radio.intro());
        d.findViewById(R.id.diag_next_band).setOnClickListener(v -> radio.nextBand());
        d.findViewById(R.id.diag_state).setOnClickListener(v -> radio.requestFullState());
        d.findViewById(R.id.diag_source).setOnClickListener(v -> radio.activateSource());
        d.findViewById(R.id.diag_reconnect).setOnClickListener(v -> radio.connect());
        Touch.install(d.getWindow().getDecorView());
    }

    private void hideSystemBars() {
        Ui.hideSystemBars(getWindow());
    }

}
