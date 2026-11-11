package com.k4811.audioset;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.k4811.audioset.eq.FactoryGains;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;

import static com.k4811.audioset.PanelParts.chip;
import static com.k4811.audioset.PanelParts.label;
import static com.k4811.audioset.PanelParts.lp;
import static com.k4811.audioset.PanelParts.row;
import static com.k4811.audioset.PanelParts.spaced;
import static com.k4811.audioset.PanelParts.title;
import static com.k4811.audioset.PanelParts.vertical;

/**
 * Aba Volume por fonte: o ganho de cada fonte no DSP ({@link FactoryGains}), para igualar rádio, Android e SYNC/BT
 * (o rádio vinha no máximo, bem mais alto que o resto). Só as três posições que o serviço da NWD usa. O serviço lê os
 * valores ao ligar: salvar avisa que vale depois de reiniciar a central.
 */
final class SourceGainPanel {

    private static final int[] POSITIONS = {FactoryGains.RADIO, FactoryGains.ANDROID, FactoryGains.OTHERS};
    private static final int[] LABELS = {R.string.eq_gain_radio, R.string.eq_gain_android, R.string.eq_gain_others};

    private final Activity activity;
    private final SeekBar[] bars = new SeekBar[POSITIONS.length];
    private final TextView[] values = new TextView[POSITIONS.length];
    private final TextView status;
    private int[] gains;

    SourceGainPanel(Activity activity, LinearLayout root) {
        this.activity = activity;
        LinearLayout col = vertical(activity);
        col.addView(title(activity, R.string.eq_tab_gain));
        TextView hint = label(activity, activity.getString(R.string.eq_gain_hint), Ui.TEXT_MUTED, 15, 6);
        col.addView(hint);
        for (int i = 0; i < POSITIONS.length; i++) addSlider(col, i);

        LinearLayout buttons = row(activity);
        TextView save = chip(activity, activity.getString(R.string.eq_gain_save));
        save.setOnClickListener(v -> save());
        buttons.addView(save);
        TextView reset = chip(activity, activity.getString(R.string.eq_gain_reset));
        reset.setOnClickListener(v -> {
            for (int i = 0; i < POSITIONS.length; i++) gains[POSITIONS[i] - 1] = FactoryGains.MAX;
            render();
        });
        buttons.addView(reset);
        col.addView(spaced(buttons, 20));
        status = label(activity, "", Ui.ACCENT, 16, 12);
        col.addView(status);

        root.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Touch.install(root);
        refresh();
    }

    /** Relê o arquivo (outro app ou o menu de fábrica pode ter mudado). */
    void refresh() {
        gains = FactoryGains.read();
        if (gains == null) gains = FactoryGains.defaults();
        status.setText("");
        render();
    }

    private void addSlider(LinearLayout parent, int i) {
        LinearLayout head = row(activity);
        head.addView(label(activity, activity.getString(LABELS[i]), Ui.TEXT, 17, 0),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        values[i] = label(activity, "", Ui.ACCENT, 17, 0);
        head.addView(values[i]);
        parent.addView(head, lp(18));
        SeekBar bar = new SeekBar(activity);
        bar.setMax(FactoryGains.MAX - FactoryGains.MIN);
        bar.setProgressDrawable(activity.getResources().getDrawable(R.drawable.seek_line));
        bar.setThumb(activity.getResources().getDrawable(R.drawable.seek_thumb));
        bar.setSplitTrack(false);
        bar.setPadding(14, 0, 14, 0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) return;
                gains[POSITIONS[i] - 1] = progress + FactoryGains.MIN;
                status.setText("");
                render();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        bars[i] = bar;
        parent.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44));
    }

    private void render() {
        for (int i = 0; i < POSITIONS.length; i++) {
            int g = gains[POSITIONS[i] - 1];
            int p = Math.max(0, Math.min(FactoryGains.MAX - FactoryGains.MIN, g - FactoryGains.MIN));
            if (bars[i].getProgress() != p) bars[i].setProgress(p);
            values[i].setText(g + " / " + FactoryGains.MAX);
        }
    }

    private void save() {
        boolean ok = FactoryGains.write(gains);
        status.setTextColor(ok ? Ui.ACCENT : 0xFFFF6E6E);
        status.setText(ok ? R.string.eq_gain_saved : R.string.eq_gain_failed);
    }
}
