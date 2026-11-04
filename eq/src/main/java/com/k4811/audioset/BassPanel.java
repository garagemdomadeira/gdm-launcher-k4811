package com.k4811.audioset;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.k4811.audioset.eq.EqController;
import com.k4811.common.eq.EqModel;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Aba Graves, a aba Bass do DspAdjust3 original: divisor de frequência (passa-alta e passa-baixa
 * com inclinação por área: frente, traseira, centro, subwoofer), reforço de graves por área
 * (frente, traseira, sub/centro: frequência e intensidade) e o som 3D. Cada mudança vai na hora
 * para o DSP pelo {@link EqController}, com os mesmos quadros do original.
 */
final class BassPanel {

    private static final int[] CROSSOVER_LABELS = {
        R.string.eq_area_front, R.string.eq_area_rear, R.string.eq_area_center, R.string.eq_area_sub};
    private static final int[] DBB_LABELS = {
        R.string.eq_area_front, R.string.eq_area_rear, R.string.eq_area_sub_center};

    private final Activity activity;
    private final EqController controller;
    private final List<TextView> crossoverAreas = new ArrayList<>();
    private final List<TextView> dbbAreas = new ArrayList<>();
    private final List<TextView> dbbFreqs = new ArrayList<>();
    private Slider hpf;
    private Slider hpfQ;
    private Slider lpf;
    private Slider lpfQ;
    private Slider gain;
    private TextView round;
    private int crossoverArea;
    private int dbbArea;
    private EqModel.State state;

    BassPanel(Activity activity, EqController controller, LinearLayout root) {
        this.activity = activity;
        this.controller = controller;
        root.addView(buildCrossover(), column(1.25f, 0));
        root.addView(buildBassBoost(), column(1f, 24));
        Touch.install(root);
    }

    // ---- Montagem ----

    private View buildCrossover() {
        LinearLayout col = vertical();
        col.addView(title(R.string.eq_crossover));
        LinearLayout areas = row();
        for (int a = 0; a < CROSSOVER_LABELS.length; a++) {
            final int area = a;
            TextView chip = chip(activity.getString(CROSSOVER_LABELS[a]));
            chip.setOnClickListener(v -> {
                crossoverArea = area;
                render(state);
            });
            areas.addView(chip);
            crossoverAreas.add(chip);
        }
        col.addView(areas);
        hpf = new Slider(col, R.string.eq_hpf, EqModel.PF_MAX, v -> sendCrossover());
        hpfQ = new Slider(col, R.string.eq_slope, EqModel.PF_Q_MAX, v -> sendCrossover());
        lpf = new Slider(col, R.string.eq_lpf, EqModel.PF_MAX, v -> sendCrossover());
        lpfQ = new Slider(col, R.string.eq_slope, EqModel.PF_Q_MAX, v -> sendCrossover());
        TextView reset = chip(activity.getString(R.string.eq_reset_area));
        reset.setOnClickListener(v -> controller.resetCrossover(crossoverArea));
        col.addView(spaced(reset, 16));
        return col;
    }

    private View buildBassBoost() {
        LinearLayout col = vertical();
        col.addView(title(R.string.eq_bass_boost));
        LinearLayout areas = row();
        for (int a = 0; a < DBB_LABELS.length; a++) {
            final int area = a;
            TextView chip = chip(activity.getString(DBB_LABELS[a]));
            chip.setOnClickListener(v -> {
                dbbArea = area;
                render(state);
            });
            areas.addView(chip);
            dbbAreas.add(chip);
        }
        col.addView(areas);
        col.addView(label(activity.getString(R.string.eq_bass_freq), Ui.TEXT_MUTED, 15, 14));
        for (int r = 0; r < 2; r++) {
            LinearLayout freqs = row();
            for (int i = r * 4; i < r * 4 + 4; i++) {
                final int freq = i;
                TextView chip = chip(EqModel.DBB_HZ[i] + " Hz");
                chip.setOnClickListener(v -> {
                    EqModel.BassBoost b = state.bassBoosts.get(dbbArea);
                    controller.setBassBoost(dbbArea, freq, b.gain);
                });
                freqs.addView(chip);
                dbbFreqs.add(chip);
            }
            col.addView(freqs);
        }
        gain = new Slider(col, R.string.eq_bass_gain, EqModel.DBB_GAIN_MAX, v -> {
            EqModel.BassBoost b = state.bassBoosts.get(dbbArea);
            controller.setBassBoost(dbbArea, b.freq, v);
        });
        TextView reset = chip(activity.getString(R.string.eq_reset_area));
        reset.setOnClickListener(v -> controller.resetBassBoost(dbbArea));
        col.addView(spaced(reset, 16));

        round = chip(activity.getString(R.string.eq_3d));
        round.setOnClickListener(v -> controller.setRound3d(!state.round3d));
        col.addView(spaced(round, 28));
        col.addView(label(activity.getString(R.string.eq_3d_hint), Ui.TEXT_MUTED, 15, 6));
        return col;
    }

    private void sendCrossover() {
        controller.setCrossover(crossoverArea, hpf.value(), hpfQ.value(), lpf.value(), lpfQ.value());
    }

    // ---- Estado ----

    void render(EqModel.State s) {
        state = s;
        if (s == null) return;
        for (int i = 0; i < crossoverAreas.size(); i++) crossoverAreas.get(i).setActivated(i == crossoverArea);
        EqModel.Crossover x = s.crossovers.get(crossoverArea);
        hpf.set(x.hpf, hz(EqModel.PF_HZ[x.hpf]));
        hpfQ.set(x.hpfQ, String.valueOf(x.hpfQ));
        lpf.set(x.lpf, hz(EqModel.PF_HZ[x.lpf]));
        lpfQ.set(x.lpfQ, String.valueOf(x.lpfQ));

        for (int i = 0; i < dbbAreas.size(); i++) dbbAreas.get(i).setActivated(i == dbbArea);
        EqModel.BassBoost b = s.bassBoosts.get(dbbArea);
        for (int i = 0; i < dbbFreqs.size(); i++) dbbFreqs.get(i).setActivated(i == b.freq);
        gain.set(b.gain, b.gain == 0 ? activity.getString(R.string.eq_bass_off) : "+" + b.gain);
        round.setActivated(s.round3d);
    }

    private static String hz(int hz) {
        return hz >= 1000 ? (hz % 1000 == 0 ? hz / 1000 + " kHz" : String.format("%.1f kHz", hz / 1000f)) : hz + " Hz";
    }

    // ---- Peças (mesmo visual das outras abas do EQ) ----

    /** Nome, valor e um controle deslizante; avisa a cada mudança feita pelo dedo. */
    private final class Slider {
        private final TextView value;
        private final SeekBar bar;

        Slider(LinearLayout parent, int labelRes, int max, IntListener onChange) {
            LinearLayout head = row();
            head.addView(label(activity.getString(labelRes), Ui.TEXT, 17, 0),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            value = label("", Ui.ACCENT, 17, 0);
            head.addView(value);
            parent.addView(head, lp(14));
            bar = new SeekBar(activity);
            bar.setMax(max);
            bar.setProgressDrawable(activity.getResources().getDrawable(R.drawable.seek_line));
            bar.setThumb(activity.getResources().getDrawable(R.drawable.seek_thumb));
            bar.setSplitTrack(false);
            bar.setPadding(14, 0, 14, 0);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                    if (fromUser) onChange.on(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            parent.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44));
        }

        int value() {
            return bar.getProgress();
        }

        void set(int progress, String text) {
            if (bar.getProgress() != progress) bar.setProgress(progress);
            value.setText(text);
        }
    }

    private interface IntListener {
        void on(int value);
    }

    private LinearLayout vertical() {
        return PanelParts.vertical(activity);
    }

    private LinearLayout row() {
        return PanelParts.row(activity);
    }

    private TextView title(int res) {
        return PanelParts.title(activity, res);
    }

    private TextView label(String text, int color, float size, int top) {
        return PanelParts.label(activity, text, color, size, top);
    }

    private TextView chip(String text) {
        return PanelParts.chip(activity, text);
    }

    private static View spaced(View v, int top) {
        return PanelParts.spaced(v, top);
    }

    private static LinearLayout.LayoutParams lp(int top) {
        return PanelParts.lp(top);
    }

    private static LinearLayout.LayoutParams column(float weight, int left) {
        return PanelParts.column(weight, left);
    }
}
