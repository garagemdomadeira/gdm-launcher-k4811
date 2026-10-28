package com.k4811.audioset;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.audioset.eq.EqController;
import com.k4811.common.ui.ButtonIcon;
import com.k4811.common.eq.EqModel;
import com.k4811.common.system.DevMode;
import com.k4811.common.ui.IconView;
import com.k4811.audioset.ui.SitePadView;
import com.k4811.common.ui.ThemeDrawables;
import com.k4811.common.ui.DockView;
import com.k4811.common.ui.DockNavigator;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;
import com.k4811.audioset.ui.VerticalSlider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The unit's equalizer and sound field.
 */
public class EqActivity extends Activity {

    private static final int TAB_EQ = 0;
    private static final int TAB_SITE = 1;
    private static final int TAB_CHANNELS = 2;
    private static final int TAB_DIAG = 3;
    /** Height of the band sliders (shorter than the column: the rest is for dragging sideways). */
    private static final float SLIDER_HEIGHT_DP = 250;
    /** Share of a band's change that goes to its neighbours: [itself, ±1, ±2]. */
    private static final float[] INFLUENCE = {1f, 0.5f, 0.25f};

    private EqController controller;
    private int tab = TAB_EQ;
    private boolean bandsBuilt;
    private boolean channelsBuilt;
    private boolean diagBuilt;

    private TextView tabEq;
    private TextView tabSite;
    private TextView tabChannels;
    private TextView tabDiag;
    private TextView badge;
    private View panelEq;
    private View panelSite;
    private View panelChannels;
    private View panelDiag;
    private LinearLayout presetsRow;
    private LinearLayout optionsRow;
    private LinearLayout bandsRow;
    private LinearLayout seatsRow;
    private LinearLayout channelsRow;
    private LinearLayout diagControls;
    private TextView diagLog;
    private SitePadView sitePad;
    private TextView siteFaderText;
    private TextView siteBalanceText;

    private final List<TextView> presetChips = new ArrayList<>();
    private final List<TextView> bandTops = new ArrayList<>();
    private final List<TextView> bandBottoms = new ArrayList<>();
    private final List<VerticalSlider> bandSliders = new ArrayList<>();
    private final List<TextView> seatChips = new ArrayList<>();
    private final List<ChannelViews> channelViews = new ArrayList<>();
    private TextView loudChip;
    private TextView influenceChip;
    private TextView modeChip;
    /** Band values when the finger touched a slider: Influence spreads the change from these. */
    private int[] dragBase;
    private final List<TextView> modeChips = new ArrayList<>();
    private TextView bootChip;
    private TextView infoPath;
    private TextView infoChip;
    private TextView infoMcu;
    private TextView infoMethod;

    private static final class ChannelViews {
        TextView title;
        TextView level;
        VerticalSlider slider;
        TextView mute;
        TextView phase;
        TextView delay;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eq);
        Ui.applyFont(findViewById(R.id.eq_root));
        DockNavigator.attachDock(this, DockView.Item.EQ);
        bindViews();
        controller = new EqController(this);
        controller.setListener(new EqController.Listener() {
            @Override
            public void onState(EqModel.State state) {
                render(state);
            }

            @Override
            public void onLog(List<String> lines) {
                if (diagLog == null) return;
                StringBuilder sb = new StringBuilder();
                for (String line : lines) sb.append(line).append('\n');
                diagLog.setText(sb.toString());
            }
        });
        controller.start();
        showTab(TAB_EQ);
        Touch.install(findViewById(android.R.id.content));
    }

    private void bindViews() {
        IconView back = (IconView) findViewById(R.id.eq_back);
        back.set(IconView.Icon.CHEVRON_LEFT, IconView.Style.NONE);
        back.setOnClickListener(v -> finish());

        tabEq = (TextView) findViewById(R.id.tab_eq);
        tabSite = (TextView) findViewById(R.id.tab_site);
        tabChannels = (TextView) findViewById(R.id.tab_channels);
        tabDiag = (TextView) findViewById(R.id.tab_diag);
        badge = (TextView) findViewById(R.id.eq_badge);
        panelEq = findViewById(R.id.panel_eq);
        panelSite = findViewById(R.id.panel_site);
        panelChannels = findViewById(R.id.panel_channels);
        panelDiag = findViewById(R.id.panel_diag);
        presetsRow = (LinearLayout) findViewById(R.id.eq_presets);
        optionsRow = (LinearLayout) findViewById(R.id.eq_options);
        bandsRow = (LinearLayout) findViewById(R.id.eq_bands);
        seatsRow = (LinearLayout) findViewById(R.id.site_seats);
        channelsRow = (LinearLayout) findViewById(R.id.channels_row);
        diagControls = (LinearLayout) findViewById(R.id.diag_controls);
        diagLog = (TextView) findViewById(R.id.diag_log);
        sitePad = (SitePadView) findViewById(R.id.site_pad);
        siteFaderText = (TextView) findViewById(R.id.site_fader_text);
        siteBalanceText = (TextView) findViewById(R.id.site_balance_text);

        tabEq.setOnClickListener(v -> showTab(TAB_EQ));
        tabSite.setOnClickListener(v -> showTab(TAB_SITE));
        tabChannels.setOnClickListener(v -> showTab(TAB_CHANNELS));
        tabDiag.setOnClickListener(v -> showTab(TAB_DIAG));
        // Diagnostics tab and backend badge are developer-mode only.
        boolean dev = DevMode.enabled();
        tabDiag.setVisibility(dev ? View.VISIBLE : View.GONE);
        badge.setVisibility(dev ? View.VISIBLE : View.INVISIBLE);

        sitePad.setListener((fader, balance) -> controller.setSite(fader, balance));
        findViewById(R.id.site_up).setOnClickListener(v ->
            controller.setSite(controller.state().fader - 1, controller.state().balance));
        findViewById(R.id.site_down).setOnClickListener(v ->
            controller.setSite(controller.state().fader + 1, controller.state().balance));
        findViewById(R.id.site_left).setOnClickListener(v ->
            controller.setSite(controller.state().fader, controller.state().balance - 1));
        findViewById(R.id.site_right).setOnClickListener(v ->
            controller.setSite(controller.state().fader, controller.state().balance + 1));

        buildPresets();
        buildSeats();
    }

    private void buildPresets() {
        presetsRow.removeAllViews();
        optionsRow.removeAllViews();
        presetChips.clear();
        for (final EqModel.Preset p : EqModel.Preset.values()) {
            TextView chip = chip(getString(p.labelRes));
            chip.setOnClickListener(v -> controller.selectPreset(p));
            presetsRow.addView(chip);
            presetChips.add(chip);
        }
        loudChip = chip(getString(R.string.eq_loudness));
        ButtonIcon.attach(loudChip, ButtonIcon.Icon.LOUDNESS);
        loudChip.setOnClickListener(v -> controller.setLoudness(!controller.state().loudness));
        optionsRow.addView(loudChip);
        influenceChip = chip(getString(R.string.eq_influence));
        ButtonIcon.attach(influenceChip, ButtonIcon.Icon.INFLUENCE);
        influenceChip.setOnClickListener(v -> controller.setInfluence(!controller.state().influence));
        optionsRow.addView(influenceChip);
        // DSP only: toggle between the simple 7-band view and all 32 bands.
        modeChip = chip(getString(R.string.eq_advanced));
        modeChip.setOnClickListener(v -> controller.setArmSimple(!controller.state().armSimple));
        optionsRow.addView(modeChip);
        TextView reset = chip(getString(R.string.eq_reset));
        ButtonIcon.attach(reset, ButtonIcon.Icon.RESET);
        reset.setOnClickListener(v -> controller.resetEq());
        optionsRow.addView(reset);
        bootChip = chip(getString(R.string.eq_apply_on_boot));
        ButtonIcon.attach(bootChip, ButtonIcon.Icon.POWER);
        bootChip.setOnClickListener(v ->
            controller.setApplyOnBoot(!controller.state().applyOnBoot));
        optionsRow.addView(bootChip);
    }

    private void buildSeats() {
        seatsRow.removeAllViews();
        seatChips.clear();
        for (final EqModel.Seat seat : EqModel.SEATS) {
            TextView chip = chip(getString(seat.labelRes));
            chip.setOnClickListener(v -> controller.setSite(seat.fader, seat.balance));
            seatsRow.addView(chip);
            seatChips.add(chip);
        }
    }

    private void ensureBands(EqModel.State s) {
        int count = s.isArm() ? (s.armSimple ? EqModel.EQ7_BANDS : EqModel.EQ32_BANDS) : 10;
        if (bandsBuilt && bandSliders.size() == count) return;
        bandsBuilt = true;
        bandsRow.removeAllViews();
        bandTops.clear();
        bandBottoms.clear();
        bandSliders.clear();
        int width = (s.isArm() && !s.armSimple) ? 48 : 96;
        for (int i = 0; i < count; i++) {
            final int band = i;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = 4;
            lp.rightMargin = 4;
            col.setLayoutParams(lp);

            // The slider has a fixed, shorter height; the labels above and below take the rest of
            // the column, so there is room to touch and drag the bands sideways.
            TextView top = new TextView(this);

            top.setTypeface(Ui.medium(this));
            top.setTextColor(Ui.ACCENT);
            top.setTextSize(14);
            top.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            col.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            VerticalSlider slider = new VerticalSlider(this);
            if (s.isArm()) {
                slider.configure(EqModel.EQ32_MIN_DB, EqModel.EQ32_MAX_DB, 0);
            } else {
                slider.configure(0, s.mcuMax, s.mcuMax / 2);
            }
            slider.setOnDragStart(() -> {
                EqModel.State now = controller.state();
                dragBase = (now.isArm() ? now.armGainsDb : now.mcuBands).clone();
            });
            slider.setListener(v -> moveBand(band, v));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(Ui.dp(this, SLIDER_HEIGHT_DP)));
            slp.topMargin = 6;
            slp.bottomMargin = 6;
            col.addView(slider, slp);

            TextView bottom = new TextView(this);

            bottom.setTypeface(Ui.medium(this));
            bottom.setTextColor(Ui.TEXT_MUTED);
            bottom.setTextSize(13);
            bottom.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            col.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            bandsRow.addView(col);
            bandTops.add(top);
            bandBottoms.add(bottom);
            bandSliders.add(slider);
        }
    }

    /**
     * A band slider moved to {@code value}. With Influence on, the 2 neighbours on each side follow:
     * the nearest by 50% of the change and the next by 25%, counted from where they were when the
     * finger touched the slider (no rounding drift while dragging).
     */
    private void moveBand(int band, int value) {
        EqModel.State s = controller.state();
        // Simple 7-band view: the band is an anchor; interpolation fills the rest (no Influence).
        if (s.isArm() && s.armSimple) {
            controller.setArmAnchor(band, value);
            return;
        }
        int[] base = dragBase;
        if (!s.influence || base == null || band >= base.length) {
            if (s.isArm()) controller.setArmBand(band, value);
            else controller.setMcuBand(band, value);
            return;
        }
        int delta = value - base[band];
        int[] next = base.clone();
        next[band] = value;
        for (int d = 1; d < INFLUENCE.length; d++) {
            int shift = Integer.signum(delta) * Math.round(Math.abs(delta) * INFLUENCE[d]); // same up and down
            if (band - d >= 0) next[band - d] = base[band - d] + shift;
            if (band + d < next.length) next[band + d] = base[band + d] + shift;
        }
        if (s.isArm()) controller.setArmBands(next);
        else controller.setMcuBands(next);
    }

    private void ensureChannels() {
        if (channelsBuilt) return;
        channelsBuilt = true;
        channelsRow.removeAllViews();
        channelViews.clear();
        TextView reset = chip(getString(R.string.eq_reset_channels));
        reset.setOnClickListener(v -> controller.resetChannels());
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.rightMargin = 12;
        channelsRow.addView(reset, resetLp);

        for (int i = 0; i < EqModel.Speaker.COUNT; i++) {
            final int ch = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            ThemeDrawables.setBackground(card, R.drawable.pill);
            card.setPadding(10, 10, 10, 10);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(150, ViewGroup.LayoutParams.MATCH_PARENT);
            clp.rightMargin = 10;
            card.setLayoutParams(clp);

            ChannelViews cv = new ChannelViews();
            cv.title = label(getString(EqModel.Speaker.SHORT[i]), Ui.ACCENT, 18);
            cv.level = label("14/14", Ui.TEXT_MUTED, 13);
            cv.slider = new VerticalSlider(this);
            cv.slider.configure(0, 14, null);
            cv.slider.setListener(v -> controller.setChannelLevel(ch, v));
            cv.mute = chip(getString(R.string.eq_mute));
            cv.mute.setOnClickListener(v ->
                controller.setChannelMute(ch, !controller.state().channels.get(ch).muted));
            cv.phase = chip(getString(R.string.eq_phase));
            cv.phase.setOnClickListener(v ->
                controller.setChannelPhase(ch, !controller.state().channels.get(ch).inverted));
            cv.delay = label("0.0 ms", Ui.TEXT_MUTED, 12);
            cv.delay.setOnClickListener(v -> {
                int d = controller.state().channels.get(ch).delay;
                controller.setChannelDelay(ch, (d + 10) % 150);
            });

            card.addView(cv.title);
            card.addView(cv.level);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            slp.topMargin = 6;
            slp.bottomMargin = 6;
            card.addView(cv.slider, slp);
            card.addView(cv.mute);
            card.addView(cv.phase);
            card.addView(cv.delay);
            channelsRow.addView(card);
            channelViews.add(cv);
        }
    }

    private void ensureDiag() {
        if (diagBuilt) return;
        diagBuilt = true;
        diagControls.removeAllViews();
        diagControls.addView(label(getString(R.string.eq_hardware), Ui.TEXT, 20));
        infoPath = label("", Ui.TEXT, 15);
        infoChip = label("", Ui.TEXT, 15);
        infoMcu = label("", Ui.TEXT, 15);
        infoMethod = label("", Ui.TEXT, 15);
        diagControls.addView(infoPath);
        diagControls.addView(infoChip);
        diagControls.addView(infoMcu);
        diagControls.addView(infoMethod);

        diagControls.addView(spacer(12));
        diagControls.addView(label(getString(R.string.eq_audio_path), Ui.TEXT_MUTED, 14));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modeChips.clear();
        for (final EqModel.BackendMode mode : EqModel.BackendMode.values()) {
            TextView chip = chip(getString(mode.labelRes));
            chip.setOnClickListener(v -> controller.setBackendMode(mode));
            modes.addView(chip);
            modeChips.add(chip);
        }
        diagControls.addView(modes);

        diagControls.addView(spacer(12));
        diagControls.addView(label(getString(R.string.eq_tests), Ui.TEXT_MUTED, 14));
        LinearLayout tests = new LinearLayout(this);
        tests.setOrientation(LinearLayout.HORIZONTAL);
        TextView dump = chip(getString(R.string.eq_read_mcu));
        dump.setOnClickListener(v -> controller.dumpMcuParams());
        TextView test = chip(getString(R.string.eq_test_dsp));
        test.setOnClickListener(v -> controller.testArm());
        TextView reapply = chip(getString(R.string.eq_reapply));
        reapply.setOnClickListener(v -> controller.reapplyAll());
        TextView reconnect = chip(getString(R.string.eq_reconnect));
        reconnect.setOnClickListener(v -> controller.reconnectMcu());
        tests.addView(dump);
        tests.addView(test);
        tests.addView(reapply);
        tests.addView(reconnect);
        diagControls.addView(tests);

    }

    private void showTab(int next) {
        tab = next;
        panelEq.setVisibility(tab == TAB_EQ ? View.VISIBLE : View.GONE);
        panelSite.setVisibility(tab == TAB_SITE ? View.VISIBLE : View.GONE);
        panelChannels.setVisibility(tab == TAB_CHANNELS ? View.VISIBLE : View.GONE);
        panelDiag.setVisibility(tab == TAB_DIAG ? View.VISIBLE : View.GONE);
        Touch.fadeIn(tab == TAB_EQ ? panelEq : tab == TAB_SITE ? panelSite
            : tab == TAB_CHANNELS ? panelChannels : panelDiag);
        tabEq.setActivated(tab == TAB_EQ);
        tabSite.setActivated(tab == TAB_SITE);
        tabChannels.setActivated(tab == TAB_CHANNELS);
        tabDiag.setActivated(tab == TAB_DIAG);
        if (tab == TAB_CHANNELS) ensureChannels();
        if (tab == TAB_DIAG) ensureDiag();
        render(controller.state());
    }

    private void render(EqModel.State s) {
        tabChannels.setVisibility(s.isArm() ? View.VISIBLE : View.GONE);
        if (!s.isArm() && tab == TAB_CHANNELS) showTab(TAB_EQ);

        if (s.isArm()) {
            badge.setText("DSP " + s.detection.chipLabel());
            badge.setTextColor(Ui.ACCENT);
        } else if (s.mcuConnection == EqModel.Connection.CONNECTED) {
            badge.setText("MCU");
            badge.setTextColor(Ui.ACCENT);
        } else if (s.mcuConnection == EqModel.Connection.CONNECTING) {
            badge.setText("…");
            badge.setTextColor(Ui.TEXT_MUTED);
        } else {
            badge.setText(R.string.eq_no_connection);
            badge.setTextColor(Ui.DANGER);
        }

        for (int i = 0; i < presetChips.size(); i++) {
            presetChips.get(i).setActivated(EqModel.Preset.values()[i] == s.preset);
        }
        if (loudChip != null) loudChip.setActivated(s.loudness);
        if (influenceChip != null) {
            // Influence is per-band smoothing; the simple view already smooths by interpolation.
            influenceChip.setVisibility(s.isArm() && s.armSimple ? View.GONE : View.VISIBLE);
            influenceChip.setActivated(s.influence);
        }
        if (modeChip != null) {
            modeChip.setVisibility(s.isArm() ? View.VISIBLE : View.GONE);
            modeChip.setActivated(!s.armSimple);
        }

        ensureBands(s);
        if (s.isArm() && s.armSimple) {
            for (int i = 0; i < bandSliders.size(); i++) {
                int anchor = EqModel.EQ7_ANCHORS[i];
                bandSliders.get(i).configure(EqModel.EQ32_MIN_DB, EqModel.EQ32_MAX_DB, 0);
                bandSliders.get(i).setValue(s.armGainsDb[anchor]);
                bandTops.get(i).setText(signed(s.armGainsDb[anchor]));
                bandBottoms.get(i).setText(EqModel.freqLabel(s.armFrequencies[anchor]));
            }
        } else if (s.isArm()) {
            for (int i = 0; i < bandSliders.size(); i++) {
                bandSliders.get(i).configure(EqModel.EQ32_MIN_DB, EqModel.EQ32_MAX_DB, 0);
                bandSliders.get(i).setValue(s.armGainsDb[i]);
                bandTops.get(i).setText(signed(s.armGainsDb[i]));
                bandBottoms.get(i).setText(EqModel.freqLabel(s.armFrequencies[i]));
            }
        } else {
            int mid = s.mcuMax / 2;
            for (int i = 0; i < bandSliders.size(); i++) {
                bandSliders.get(i).configure(0, s.mcuMax, mid);
                bandSliders.get(i).setValue(s.mcuBands[i]);
                bandTops.get(i).setText(signed(s.mcuBands[i] - mid));
                bandBottoms.get(i).setText(EqModel.EQ10_LABELS[i]);
            }
        }

        sitePad.setSite(s.fader, s.balance);
        siteFaderText.setText(getString(R.string.eq_site_fader,
            describe(s.fader, R.string.eq_dir_front, R.string.eq_dir_rear)));
        siteBalanceText.setText(getString(R.string.eq_site_balance,
            describe(s.balance, R.string.eq_dir_left, R.string.eq_dir_right)));
        for (int i = 0; i < seatChips.size(); i++) {
            EqModel.Seat seat = EqModel.SEATS.get(i);
            seatChips.get(i).setActivated(s.fader == seat.fader && s.balance == seat.balance);
        }

        if (channelsBuilt) {
            for (int i = 0; i < channelViews.size(); i++) {
                EqModel.Channel c = s.channels.get(i);
                ChannelViews cv = channelViews.get(i);
                cv.level.setText(c.muted ? getString(R.string.eq_muted) : (c.level + "/14"));
                cv.slider.setEnabled(!c.muted);
                cv.slider.setValue(c.level);
                cv.mute.setActivated(c.muted);
                cv.phase.setActivated(c.inverted);
                cv.delay.setText(String.format(Locale.US, "%.1f ms", c.delay / 10f));
            }
        }

        if (diagBuilt) {
            infoPath.setText(getString(R.string.eq_info_path, s.backend.label));
            infoChip.setText(getString(R.string.eq_info_chip, chipText(s.detection),
                getString(s.detection.dts ? R.string.eq_yes : R.string.eq_no)));
            infoMcu.setText("SettingService: " + s.mcuConnection.name());
            infoMethod.setText(getString(R.string.eq_info_method,
                s.detection.hardwareMethod == null ? "—" : s.detection.hardwareMethod, s.mcuMax, s.armEqMax));
            for (int i = 0; i < modeChips.size(); i++) {
                modeChips.get(i).setActivated(EqModel.BackendMode.values()[i] == s.backendMode);
            }
        }
        if (bootChip != null) bootChip.setActivated(s.applyOnBoot);
    }

    private TextView chip(String text) {
        TextView tv = new TextView(this);
        tv.setTypeface(Ui.medium(this));
        tv.setText(text);
        tv.setTextColor(Ui.TEXT);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        ThemeDrawables.setBackground(tv, R.drawable.pill);
        tv.setPadding(18, 12, 18, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = 8;
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView label(String text, int color, float size) {
        TextView tv = new TextView(this);
        tv.setTypeface(Ui.medium(this));
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        tv.setPadding(0, 4, 0, 4);
        return tv;
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp, dp));
        return v;
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    private String describe(int v, int lowRes, int highRes) {
        if (v == EqModel.SITE_CENTER) return getString(R.string.eq_site_center_value, v, EqModel.SITE_MAX);
        if (v < EqModel.SITE_CENTER) {
            return getString(R.string.eq_site_toward, EqModel.SITE_CENTER - v, getString(lowRes), v, EqModel.SITE_MAX);
        }
        return getString(R.string.eq_site_toward, v - EqModel.SITE_CENTER, getString(highRes), v, EqModel.SITE_MAX);
    }

    private String chipText(EqModel.HardwareInfo info) {
        return info.hasArmDsp() ? info.chipLabel() : getString(R.string.eq_chip_none);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.hideSystemBars(getWindow());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(getWindow());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Ui.applyDesignDensity(this);
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.setListener(null);
            controller.release();
        }
        super.onDestroy();
    }
}
