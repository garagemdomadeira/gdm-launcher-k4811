package com.k4811.clima;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.can.AirState;
import com.k4811.common.can.CanClient;
import com.k4811.common.can.CanProtocol;
import com.k4811.common.ui.IconView;
import com.k4811.common.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela de clima (ar-condicionado) da suíte. Lê o estado do A/C pelo CAN e controla A/C, AUTO, DUAL, MAX,
 * recirculação, desembaçadores, direção do ar, ventilação e temperatura das duas zonas. Substitui o
 * pedaço de clima da barra inferior da NWD.
 *
 * O comportamento não foi testado num carro (a bancada não tem CAN de clima): o estado chega vazio e os
 * comandos não têm efeito visível. Em um carro real, mostra os dados e controla de verdade.
 */
public class ClimaActivity extends Activity implements CanClient.Listener {

    private CanClient can;
    private TextView status;
    private TextView leftTemp;
    private TextView rightTemp;
    private LinearLayout fanDots;
    private final List<Toggle> toggles = new ArrayList<>();

    /** Botão que acende quando o estado está ligado. */
    private final class Toggle {
        final View view;
        final Getter on;
        Toggle(View v, Getter g) {
            view = v;
            on = g;
        }
    }

    private interface Getter {
        boolean get(AirState s);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        Ui.applyFont(findViewById(android.R.id.content));

        can = new CanClient(this);
        can.setListener(this);
        can.connect();
        render(can.state());
        Ui.hideSystemBars(getWindow());
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

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int p = dp(22);
        root.setPadding(p, dp(16), p, dp(16));

        root.addView(titleRow());
        root.addView(gap(16));
        root.addView(tempRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(gap(14));
        root.addView(toggleRow(new byte[]{CanProtocol.POWER_SWITCH, CanProtocol.AUTO, CanProtocol.DUAL, CanProtocol.AC_MAX},
            new String[]{"A/C", "AUTO", "DUAL", "MAX"},
            new Getter[]{s -> s.acSwitch, s -> s.auto, s -> s.dual, s -> s.maxAc}));
        root.addView(gap(12));
        root.addView(fanRow());
        root.addView(gap(12));
        root.addView(directionRow());
        root.addView(gap(12));
        root.addView(toggleRow(new byte[]{CanProtocol.FRONT_DEFOG, CanProtocol.REAR_DEFOG, CanProtocol.RECIRC},
            new String[]{getString(R.string.clima_defog_front), getString(R.string.clima_defog_rear),
                getString(R.string.clima_recirc)},
            new Getter[]{s -> s.frontWindowDefog, s -> s.backWindowDefog, s -> s.insideOrOutside}));
        root.addView(gap(14));

        status = new TextView(this);
        status.setTextColor(Ui.TEXT_MUTED);
        status.setTextSize(13);
        status.setText(getString(R.string.clima_status_connecting));
        root.addView(status);
        return root;
    }

    private View titleRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(getString(R.string.clima_title));
        t.setTextColor(Ui.TEXT);
        t.setTextSize(26);
        row.addView(t, lp(false, 0, 1f));
        IconView close = new IconView(this);
        close.set(IconView.Icon.EXIT, IconView.Style.FLAT_CIRCLE);
        int s = dp(44);
        close.setOnClickListener(v -> finish());
        row.addView(close, new LinearLayout.LayoutParams(s, s));
        return row;
    }

    // ---- temperaturas ----------------------------------------------------------------------------

    private View tempRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        leftTemp = new TextView(this);
        rightTemp = new TextView(this);
        row.addView(tempCard(getString(R.string.clima_zone_left), leftTemp, CanProtocol.LEFT_TEMP_DEC, CanProtocol.LEFT_TEMP_INC),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        row.addView(gap(14, false));
        row.addView(tempCard(getString(R.string.clima_zone_right), rightTemp, CanProtocol.RIGHT_TEMP_DEC, CanProtocol.RIGHT_TEMP_INC),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    private View tempCard(String label, TextView tempView, byte dec, byte inc) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(panel(Ui.SURFACE, dp(22)));
        int q = dp(16);
        card.setPadding(q, q, q, q);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Ui.TEXT_MUTED);
        lbl.setTextSize(14);
        lbl.setLetterSpacing(0.15f);
        card.addView(lbl);

        tempView.setText("-.--");
        tempView.setTextColor(Ui.TEXT);
        tempView.setTextSize(40);
        tempView.setGravity(Gravity.CENTER);
        card.addView(tempView, lp(true, 0, 1f));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        int b = dp(56);
        IconView minus = roundIcon(IconView.Icon.MINUS, () -> can.control(dec));
        IconView plus = roundIcon(IconView.Icon.PLUS, () -> can.control(inc));
        btns.addView(minus, new LinearLayout.LayoutParams(b, b));
        btns.addView(gap(16, false));
        btns.addView(plus, new LinearLayout.LayoutParams(b, b));
        card.addView(btns);
        return card;
    }

    // ---- ventilação ------------------------------------------------------------------------------

    private View fanRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(panel(Ui.SURFACE, dp(22)));
        int q = dp(14);
        row.setPadding(q, q, q, q);

        IconView fan = new IconView(this);
        fan.set(IconView.Icon.FAN, IconView.Style.NONE);
        fan.color(Ui.TEXT);
        int fs = dp(40);
        row.addView(fan, new LinearLayout.LayoutParams(fs, fs));
        row.addView(gap(12, false));

        fanDots = new LinearLayout(this);
        fanDots.setOrientation(LinearLayout.HORIZONTAL);
        fanDots.setGravity(Gravity.CENTER);
        row.addView(fanDots, lp(false, 0, 1f));

        int b = dp(56);
        row.addView(roundIcon(IconView.Icon.MINUS, () -> can.fanDec()), new LinearLayout.LayoutParams(b, b));
        row.addView(gap(12, false));
        row.addView(roundIcon(IconView.Icon.PLUS, () -> can.fanInc()), new LinearLayout.LayoutParams(b, b));
        return row;
    }

    private void renderFan(AirState s) {
        fanDots.removeAllViews();
        int level = s == null ? 0 : s.airSpeedLevel;
        int max = s == null ? 7 : s.fanMax();
        int d = dp(14);
        int m = dp(4);
        for (int i = 1; i <= max; i++) {
            View dot = new View(this);
            dot.setBackground(panel(i <= level ? Ui.ACCENT : Ui.SURFACE_TOP, d / 2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d, d);
            lp.rightMargin = m;
            fanDots.addView(dot, lp);
        }
    }

    // ---- direção do ar ---------------------------------------------------------------------------

    private View directionRow() {
        return toggleRow(new byte[]{CanProtocol.WIND_UP, CanProtocol.WIND_PARALLEL, CanProtocol.WIND_DOWN},
            new String[]{"↑", "↔", "↓"},
            new Getter[]{s -> s.airModeUp, s -> s.airModeParallel, s -> s.airModeDown});
    }

    // ---- botões de toggle --------------------------------------------------------------------------

    private View toggleRow(byte[] types, String[] labels, Getter[] getters) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < types.length; i++) {
            if (i > 0) row.addView(gap(10, false));
            byte type = types[i];
            TextView b = new TextView(this);
            b.setText(labels[i]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(18);
            b.setPadding(0, dp(16), 0, dp(16));
            b.setOnClickListener(v -> can.control(type));
            row.addView(b, lp(false, 0, 1f));
            toggles.add(new Toggle(b, getters[i]));
        }
        return row;
    }

    private void renderToggles(AirState s) {
        for (Toggle t : toggles) {
            boolean on = s != null && t.on.get(s);
            t.view.setBackground(panel(on ? Ui.ACCENT : Ui.SURFACE, dp(18)));
            if (t.view instanceof TextView) {
                ((TextView) t.view).setTextColor(on ? Ui.BG : Ui.TEXT);
            }
        }
    }

    // ---- render geral ----------------------------------------------------------------------------

    private void render(AirState s) {
        leftTemp.setText(s == null ? "-.--" : s.leftTemp);
        rightTemp.setText(s == null ? "-.--" : s.rightTemp);
        renderFan(s);
        renderToggles(s);
    }

    @Override
    public void onAir(AirState state) {
        render(state);
        if (state != null) status.setText("");
    }

    @Override
    public void onCanConnected(boolean connected) {
        if (!connected) {
            status.setText(getString(R.string.clima_status_offline));
        } else {
            status.setText(can.state() == null ? getString(R.string.clima_status_waiting) : "");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (can != null) can.disconnect();
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private IconView roundIcon(IconView.Icon icon, Runnable onTap) {
        IconView v = new IconView(this);
        v.set(icon, IconView.Style.FLAT_CIRCLE);
        v.setOnClickListener(x -> onTap.run());
        return v;
    }

    private GradientDrawable panel(int fill, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        return g;
    }

    private View gap(int h) {
        return gap(h, true);
    }

    private View gap(int size, boolean vertical) {
        View v = new View(this);
        v.setLayoutParams(vertical
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(size))
            : new LinearLayout.LayoutParams(dp(size), ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
    }

    private LinearLayout.LayoutParams lp(boolean matchW, int h, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            matchW ? ViewGroup.LayoutParams.MATCH_PARENT : 0, h == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(h), weight);
        return p;
    }

    private int dp(int v) {
        return Math.round(Ui.dp(this, v));
    }
}
