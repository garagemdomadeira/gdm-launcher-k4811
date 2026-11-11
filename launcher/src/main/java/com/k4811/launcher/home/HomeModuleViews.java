package com.k4811.launcher.home;

import com.k4811.common.home.HomeGrid;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.launcher.R;
import com.k4811.common.ui.Ui;

import java.util.EnumMap;
import java.util.Map;

/**
 * Places the home modules (views of activity_home) in their {@link HomeGrid} slots, laid out for
 * this screen (landscape columns or portrait rows), and sizes them for the slot's shape: tall,
 * large, wide or small. The call card covers the "now playing" slot,
 * or the left slot when "now playing" is hidden. Content (texts, icons, state) stays with
 * HomeActivity; this class only decides position, sizes and which parts fit.
 */
public final class HomeModuleViews {

    private final Activity activity;
    private HomeGrid.Layout grid;
    private Map<HomeGrid.Module, HomeGrid.Slot> layout;
    private boolean npLine2 = true;
    private boolean npProgress = true;
    private boolean npExtras = true;

    public HomeModuleViews(Activity activity) {
        this.activity = activity;
        this.grid = HomeGrid.layout(activity);
    }

    /** Recomputes the grid for the area the activity has now, and places everything again. */
    public void relayout() {
        grid = HomeGrid.layout(activity);
        Map<HomeGrid.Module, HomeGrid.Slot> current = layout;
        layout = null;
        if (current != null) apply(current);
    }

    /** Where {@code slot} sits on this screen. */
    public HomeGrid.Frame frame(HomeGrid.Slot slot) {
        return grid.frame(slot);
    }

    /** Applies {@code next} (module → slot; missing = hidden). Does nothing if it didn't change. */
    public void apply(Map<HomeGrid.Module, HomeGrid.Slot> next) {
        if (next.equals(layout)) return;
        layout = new EnumMap<>(next);
        for (HomeGrid.Module m : HomeGrid.Module.values()) {
            View root = root(m);
            HomeGrid.Slot at = layout.get(m);
            if (at == null) {
                root.setVisibility(View.GONE);
                if (m == HomeGrid.Module.NOW_PLAYING) activity.findViewById(R.id.home_clima).setVisibility(View.GONE);
                continue;
            }
            HomeGrid.Frame slot = grid.frame(at);
            if (m == HomeGrid.Module.PIP) slot = pipFrame(at, next);
            place(root, slot);
            switch (m) {
                case NOW_PLAYING:
                    if (grid.portrait) {
                        // Em pé: o tempo (clima do ambiente) ocupa a esquerda e o tocando-agora a direita,
                        // preenchendo o vão preto que ficava ao lado do player centralizado.
                        int climaW = Math.round(slot.width * 0.30f);
                        int gap = 16;
                        View clima = activity.findViewById(R.id.home_clima);
                        place(clima, HomeGrid.frameOf(slot.x, slot.y, climaW, slot.height, slot.size));
                        clima.setVisibility(View.VISIBLE);
                        HomeGrid.Frame np = HomeGrid.frameOf(slot.x + climaW + gap, slot.y,
                            slot.width - climaW - gap, slot.height, slot.size);
                        place(root, np);
                        fitNowPlaying(np);
                    } else {
                        activity.findViewById(R.id.home_clima).setVisibility(View.GONE);
                        fitNowPlaying(slot);
                    }
                    break;
                case CLOCK:
                    fitClock(slot);
                    break;
                case VOLUME:
                    fitVolume(slot);
                    break;
                case FAN:
                    fitStatus(slot, R.id.fan_status, R.id.fan_status_icon, R.id.fan_status_info,
                        R.id.fan_status_title, 30, R.id.fan_status_text, 22);
                    break;
                case BLUETOOTH:
                    fitStatus(slot, R.id.bt_status, R.id.bt_status_icon, R.id.bt_status_info,
                        R.id.bt_status_text, 26, R.id.bt_status_device, 20);
                    break;
                case IMAGE:
                    text(R.id.image_empty).setTextSize(TypedValue.COMPLEX_UNIT_SP,
                        slot.size == HomeGrid.Size.SMALL ? 16 : 20);
                    break;
                case SPEED:
                    fitSpeed(slot);
                    break;
                case GPS:
                    fitGps(slot);
                    break;
                case OBD:
                    fitObd(slot);
                    break;
                default:
                    break;
            }
            root.setVisibility(View.VISIBLE);
        }
        // Ligação: com a coluna da direita livre (padrão), o cartão aparece nela inteira e o "tocando
        // agora" continua à vista; senão, por cima do "tocando agora", como antes. Some sem ligação.
        HomeGrid.Frame callFrame;
        if (!grid.portrait && free(HomeGrid.Slot.RIGHT_TOP) && free(HomeGrid.Slot.RIGHT_MIDDLE)
            && free(HomeGrid.Slot.RIGHT_BOTTOM)) {
            callFrame = HomeGrid.span(grid.frame(HomeGrid.Slot.RIGHT_TOP), grid.frame(HomeGrid.Slot.RIGHT_BOTTOM));
        } else {
            HomeGrid.Slot callSlot = slotOf(HomeGrid.Module.NOW_PLAYING);
            if (callSlot == null) callSlot = HomeGrid.Slot.LEFT;
            callFrame = grid.frame(callSlot);
        }
        callOnRight = !grid.portrait && free(HomeGrid.Slot.RIGHT_TOP) && free(HomeGrid.Slot.RIGHT_MIDDLE)
            && free(HomeGrid.Slot.RIGHT_BOTTOM);
        View card = activity.findViewById(R.id.card_call);
        place(card, callFrame);
        fitCall(callFrame);
        card.bringToFront();
    }

    /** Slot of {@code m}, or null when it's hidden. */
    private boolean callOnRight;

    /** O cartão da ligação aparece na coluna da direita (o "tocando agora" continua à vista). */
    public boolean callOnRight() {
        return callOnRight;
    }

    /**
     * O PIP na coluna da direita, com as outras áreas dela livres, ocupa a coluna inteira (paisagem); no retrato, a
     * linha de baixo inteira. Senão, só a área dele.
     */
    private HomeGrid.Frame pipFrame(HomeGrid.Slot at, Map<HomeGrid.Module, HomeGrid.Slot> next) {
        HomeGrid.Slot[] column = {HomeGrid.Slot.RIGHT_TOP, HomeGrid.Slot.RIGHT_MIDDLE, HomeGrid.Slot.RIGHT_BOTTOM};
        boolean inColumn = false;
        for (HomeGrid.Slot c : column) if (c == at) inColumn = true;
        if (!inColumn) return grid.frame(at);
        for (HomeGrid.Slot c : column) if (c != at && next.containsValue(c)) return grid.frame(at);
        HomeGrid.Frame top = grid.frame(HomeGrid.Slot.RIGHT_TOP);
        HomeGrid.Frame bottom = grid.frame(HomeGrid.Slot.RIGHT_BOTTOM);
        if (!grid.portrait) return HomeGrid.span(top, bottom);
        return HomeGrid.row(top, bottom);
    }

    /** O PIP está ocupando a coluna (ou linha) inteira. */
    public HomeGrid.Frame pipArea() {
        HomeGrid.Slot at = slotOf(HomeGrid.Module.PIP);
        return at == null ? null : pipFrame(at, layout);
    }

    /** Nenhum módulo ocupa {@code slot}. */
    private boolean free(HomeGrid.Slot slot) {
        return layout == null || !layout.containsValue(slot);
    }

    public HomeGrid.Slot slotOf(HomeGrid.Module m) {
        return layout == null ? null : layout.get(m);
    }

    /** "Now playing" has room for the second line (artist, band) in its slot. */
    public boolean nowPlayingLine2() {
        return npLine2;
    }

    /** "Tocando agora" tem lugar para o álbum/RDS, as etiquetas e os tempos. */
    public boolean nowPlayingExtras() {
        return npExtras;
    }

    /** "Now playing" has room for the progress bar in its slot. */
    public boolean nowPlayingProgress() {
        return npProgress;
    }

    private View root(HomeGrid.Module m) {
        switch (m) {
            case NOW_PLAYING:
                return activity.findViewById(R.id.now_playing);
            case CLOCK:
                return activity.findViewById(R.id.clock_block);
            case VOLUME:
                return activity.findViewById(R.id.volume_row);
            case FAN:
                return activity.findViewById(R.id.fan_status);
            case BLUETOOTH:
                return activity.findViewById(R.id.bt_status);
            case IMAGE:
                return activity.findViewById(R.id.image_block);
            case SPEED:
                return activity.findViewById(R.id.speed_block);
            case CAR:
                return activity.findViewById(R.id.car_block);
            case OBD:
                return activity.findViewById(R.id.obd_block);
            case PIP:
                return activity.findViewById(R.id.pip_block);
            default:
                return activity.findViewById(R.id.gps_block);
        }
    }

    // ------------------------------------------------------------------ modules

    private void fitClock(HomeGrid.Frame slot) {
        int time;
        int date;
        int gap;
        switch (slot.size) {
            case TALL:
                time = 130;
                date = 26;
                gap = 10;
                break;
            case LARGE:
                time = 158;
                date = 30;
                gap = 10;
                break;
            case WIDE:
                time = 96;
                date = 20;
                gap = 6;
                break;
            default:
                time = 72;
                date = 16;
                gap = 4;
                break;
        }
        TextView clockTime = text(R.id.clock_time);
        TextView clockDate = text(R.id.clock_date);
        Ui.autoFit(clockTime, sp(time), slot.width - 16);
        Ui.autoFit(clockDate, sp(date), slot.width - 16);
        margins(clockDate, 0, gap, 0, 0);
    }

    /** Speed: big number over "km/h" (or the GPS state). */
    private void fitSpeed(HomeGrid.Frame slot) {
        int value;
        int unit;
        switch (slot.size) {
            case TALL:
                value = 160;
                unit = 30;
                break;
            case LARGE:
                value = 150;
                unit = 30;
                break;
            case WIDE:
                value = 96;
                unit = 20;
                break;
            default:
                value = 64;
                unit = 16;
                break;
        }
        Ui.autoFit(text(R.id.speed_value), sp(value), slot.width - 16);
        Ui.autoFit(text(R.id.speed_unit), sp(unit), slot.width - 16);
    }

    /** GPS data: four "label value" rows, a column centered in the big slots. */
    private void fitGps(HomeGrid.Frame slot) {
        int size;
        int labelWidth;
        int gap;
        switch (slot.size) {
            case TALL:
                size = 24;
                labelWidth = 150;
                gap = 14;
                break;
            case LARGE:
                size = 22;
                labelWidth = 140;
                gap = 10;
                break;
            case WIDE:
                size = 18;
                labelWidth = 120;
                gap = 6;
                break;
            default:
                size = 15;
                labelWidth = 96;
                gap = 4;
                break;
        }
        ((LinearLayout) activity.findViewById(R.id.gps_block)).setGravity(slot.size == HomeGrid.Size.SMALL
            ? Gravity.CENTER_VERTICAL | Gravity.LEFT : Gravity.CENTER);
        int[][] rows = {
            {R.id.gps_lat_label, R.id.gps_lat_value},
            {R.id.gps_lon_label, R.id.gps_lon_value},
            {R.id.gps_alt_label, R.id.gps_alt_value},
            {R.id.gps_dir_label, R.id.gps_dir_value},
        };
        for (int i = 0; i < rows.length; i++) {
            TextView label = text(rows[i][0]);
            label.getLayoutParams().width = dp(labelWidth);
            Ui.autoFit(label, sp(size), labelWidth - 8);
            Ui.autoFit(text(rows[i][1]), sp(size), slot.width - 12 - labelWidth);
            margins((View) label.getParent(), 0, i == 0 ? 0 : gap, 0, 0);
        }
    }

    private void fitVolume(HomeGrid.Frame slot) {
        int gap = slot.size == HomeGrid.Size.SMALL ? 28 : 44;
        margins(activity.findViewById(R.id.volume_mute), gap, 0, gap, 0);
    }

    /** Fan and Bluetooth: icon beside two lines; bigger and centered in the large slots. */
    private void fitStatus(HomeGrid.Frame slot, int rootId, int iconId, int infoId,
                           int titleId, int titleSp, int subId, int subSp) {
        boolean big = slot.size == HomeGrid.Size.TALL || slot.size == HomeGrid.Size.LARGE;
        int icon = big ? 72 : 52;
        int gap = big ? 24 : 18;
        float scale = big ? 1.3f : 1f;
        ((LinearLayout) activity.findViewById(rootId)).setGravity(slot.size == HomeGrid.Size.SMALL
            ? Gravity.CENTER_VERTICAL | Gravity.LEFT : Gravity.CENTER);
        size(activity.findViewById(iconId), icon, icon);
        margins(activity.findViewById(infoId), gap, 0, 0, 0);
        int width = slot.width - 12 - icon - gap;
        Ui.autoFit(text(titleId), sp(Math.round(titleSp * scale)), width);
        TextView sub = text(subId);
        if (sub.getEllipsize() != null) {
            // Device name: long names are cut, not shrunk.
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.round(subSp * scale));
            sub.setMaxWidth(dp(width));
        } else {
            Ui.autoFit(sub, sp(Math.round(subSp * scale)), width - 22);
        }
    }

    /**
     * Tall slot: cover above, then label, title, subtitle, progress and controls. The other slots
     * put the cover beside the text and drop what doesn't fit.
     */
    private void fitNowPlaying(HomeGrid.Frame slot) {
        int art;
        int line1;
        int line2;
        int button;
        int buttonGap;
        boolean label;
        switch (slot.size) {
            case TALL:
                art = 180;
                line1 = 34;
                line2 = 24;
                button = 60;
                buttonGap = 14;
                label = true;
                npLine2 = true;
                npProgress = true;
                break;
            case LARGE:
                // Capa compacta: ~128 no slot horizontal, até 150 nos verticais mais largos (antes ia a 300 e
                // ficava enorme na tela inicial). O mini play (prev/play/next) fica ao lado, estilo HTG Dash.
                art = Math.max(128, Math.min(150, Math.min(slot.height - 28, slot.width * 9 / 20)));
                line1 = 30;
                line2 = 20;
                button = 56;
                buttonGap = 10;
                label = true;
                npLine2 = true;
                npProgress = true;
                break;
            case WIDE:
                art = 132;
                line1 = 28;
                line2 = 18;
                button = 48;
                buttonGap = 10;
                label = false;
                npLine2 = true;
                npProgress = true;
                break;
            default:
                art = 96;
                line1 = 22;
                line2 = 18;
                button = 44;
                buttonGap = 4;
                label = false;
                npLine2 = false;
                npProgress = false;
                break;
        }
        boolean tall = slot.size == HomeGrid.Size.TALL;
        npExtras = tall || slot.size == HomeGrid.Size.LARGE;
        LinearLayout root = (LinearLayout) activity.findViewById(R.id.now_playing);
        root.setOrientation(tall ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        // Na vertical o "tocando agora" fica embaixo (perto do dock): alinha o conteúdo ao rodapé do seu espaço
        // em vez de centralizar, para descer mais. Em paisagem continua centralizado.
        root.setGravity(tall ? Gravity.LEFT
            : (Ui.isPortrait(activity) ? (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL) : Gravity.CENTER_VERTICAL));

        View frame = activity.findViewById(R.id.np_art_frame);
        size(frame, art, art);
        margins(frame, 0, 0, 0, tall ? 18 : 0);
        int pad = dp(art / 3f);
        activity.findViewById(R.id.np_art_icon).setPadding(pad, pad, pad, pad);

        // Na vertical o "tocando agora" fica centralizado: o info usa a largura do conteúdo (sem esticar), para o
        // conjunto capa+texto centralizar de verdade (o gravity do root é CENTER_HORIZONTAL). Em paisagem estica.
        boolean center = !tall && Ui.isPortrait(activity);
        View info = activity.findViewById(R.id.np_info);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
            tall || center ? LinearLayout.LayoutParams.WRAP_CONTENT : 0, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (!tall) {
            if (!center) infoLp.weight = 1;
            infoLp.leftMargin = dp(slot.size == HomeGrid.Size.SMALL ? 14 : 20);
        }
        info.setLayoutParams(infoLp);

        // A fonte (com a setinha) aparece em todos os tamanhos: é por ela que se escolhe o que toca.
        TextView npLabel = text(R.id.np_label);
        npLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, tall ? 16 : label ? 14 : 13);
        npLabel.setMaxWidth(dp(Math.max(80, (tall ? slot.width : slot.width - art - 20) - 90)));
        TextView npLine1 = text(R.id.np_line1);
        npLine1.setTextSize(TypedValue.COMPLEX_UNIT_SP, line1);
        margins(npLine1, 0, tall ? 10 : label ? 8 : 2, 0, 0);
        // No modo centralizado (vertical) o título/artista não esticam o bloco: largura limitada, corta com "…".
        if (center) {
            int tw = dp(Math.round(slot.width * 0.52f));
            npLine1.setMaxWidth(tw);
            npLine1.setSingleLine(true);
            npLine1.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView l2 = text(R.id.np_line2);
            l2.setMaxWidth(tw);
            l2.setSingleLine(true);
            l2.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        if (!npExtras) {
            text(R.id.np_extra).setVisibility(View.GONE);
            text(R.id.np_tags).setVisibility(View.GONE);
            activity.findViewById(R.id.np_times).setVisibility(View.GONE);
        }
        int barWidth = tall ? slot.width - 28
            : center ? Math.round(slot.width * 0.52f) : Math.max(150, slot.width - art - 60);
        activity.findViewById(R.id.np_progress_track).getLayoutParams().width = dp(barWidth);
        activity.findViewById(R.id.np_times).getLayoutParams().width = dp(barWidth);
        text(R.id.np_line2).setTextSize(TypedValue.COMPLEX_UNIT_SP, line2);
        if (!npLine2) text(R.id.np_line2).setVisibility(View.GONE);
        View track = activity.findViewById(R.id.np_progress_track);
        margins(track, 0, tall ? 16 : 10, 0, 0);
        if (!npProgress) track.setVisibility(View.GONE);

        View controls = activity.findViewById(R.id.np_controls);
        margins(controls, tall ? -10 : -8, tall ? 8 : 4, 0, 0);
        size(activity.findViewById(R.id.np_prev), button, button);
        size(activity.findViewById(R.id.np_next), button, button);
        View play = activity.findViewById(R.id.np_play);
        size(play, button, button);
        margins(play, buttonGap, 0, buttonGap, 0);
    }

    /**
     * OBD2 com o relógio: hora, data e injeção à esquerda; seis vitais (ícone e valor) em duas linhas de três.
     * Alto ou retrato largo: o relógio em cima. Pequeno: só a hora e a primeira linha.
     */
    private void fitObd(HomeGrid.Frame slot) {
        boolean small = slot.size == HomeGrid.Size.SMALL;
        // Relógio em cima dos vitais só onde há altura (área alta); nas faixas do retrato fica ao lado, como na paisagem.
        boolean stacked = slot.size == HomeGrid.Size.TALL || slot.height >= 300;
        LinearLayout root = (LinearLayout) activity.findViewById(R.id.obd_block);
        root.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        root.setGravity(stacked ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        View divider = activity.findViewById(R.id.obd_divider);
        LinearLayout.LayoutParams dl = stacked
            ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            : new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT);
        int gap = small ? 10 : 14;
        if (stacked) dl.setMargins(0, dp(gap), 0, dp(gap));
        else dl.setMargins(dp(gap), 0, dp(gap), 0);
        divider.setLayoutParams(dl);
        View cells = activity.findViewById(R.id.obd_cells);
        cells.setLayoutParams(stacked
            ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        int timeSp = stacked ? 84 : slot.size == HomeGrid.Size.LARGE ? 64 : small ? 40 : 48;
        int clockWidth = stacked ? slot.width - 20 : Math.min(150, slot.width / 3);
        Ui.autoFit(text(R.id.obd_time), sp(timeSp), clockWidth);
        Ui.autoFit(text(R.id.obd_date), sp(stacked ? 18 : 13), clockWidth);
        text(R.id.obd_date).setVisibility(small ? View.GONE : View.VISIBLE);
        activity.findViewById(R.id.obd_mil_cell).setVisibility(small ? View.GONE : View.VISIBLE);
        activity.findViewById(R.id.obd_row2).setVisibility(small ? View.GONE : View.VISIBLE);
        activity.findViewById(R.id.obd_row3).setVisibility(small ? View.GONE : View.VISIBLE);
        int value = stacked ? 28 : small ? 16 : 18;
        int icon = stacked ? 30 : small ? 18 : 20;
        for (String id : new String[] {"speed", "gear", "rpm", "temp", "volts", "fuel"}) {
            text(id("obd_" + id)).setTextSize(TypedValue.COMPLEX_UNIT_SP, value);
            size(activity.findViewById(id("obd_" + id + "_icon")), icon, icon);
        }
        margins(activity.findViewById(R.id.obd_row2), 0, stacked ? 20 : 10, 0, 0);
        margins(activity.findViewById(R.id.obd_row3), 0, stacked ? 20 : 10, 0, 0);
    }

    private int id(String name) {
        return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
    }

    /** The call card fills its slot; the short slots keep only the name and the buttons. */
    private void fitCall(HomeGrid.Frame slot) {
        boolean small = slot.size == HomeGrid.Size.SMALL;
        boolean details = slot.size == HomeGrid.Size.TALL || slot.size == HomeGrid.Size.LARGE;
        int pad = dp(small ? 10 : 16);
        activity.findViewById(R.id.card_call).setPadding(pad, pad, pad, pad);
        text(R.id.call_state).setVisibility(details ? View.VISIBLE : View.GONE);
        text(R.id.call_number).setVisibility(details ? View.VISIBLE : View.GONE);
        Ui.autoFit(text(R.id.call_name), sp(small ? 24 : details ? 34 : 30), slot.width - 2 * (small ? 10 : 16));
        int button = small ? 48 : 60;
        int buttonText = small ? 20 : 24;
        // Two pills side by side: half the width minus their margins (6 + 6) and side padding (20 + 20).
        int textWidth = (slot.width - 2 * (small ? 10 : 16)) / 2 - 12 - 40;
        for (int id : new int[]{R.id.call_answer, R.id.call_hangup}) {
            TextView b = text(id);
            b.getLayoutParams().height = dp(button);
            Ui.autoFit(b, sp(buttonText), textWidth);
        }
    }

    // ------------------------------------------------------------------ util

    private void place(View v, HomeGrid.Frame slot) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(slot.width), dp(slot.height));
        lp.leftMargin = dp(slot.x);
        lp.topMargin = dp(slot.y);
        v.setLayoutParams(lp);
    }

    private void size(View v, int widthDp, int heightDp) {
        v.getLayoutParams().width = dp(widthDp);
        v.getLayoutParams().height = dp(heightDp);
        v.requestLayout();
    }

    private void margins(View v, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        v.setLayoutParams(lp);
    }

    private TextView text(int id) {
        return (TextView) activity.findViewById(id);
    }

    private int dp(float v) {
        return Math.round(Ui.dp(activity, v));
    }

    /** Text sizes: scaledDensity equals density (Ui.applyDesignDensity), so sp = dp here. */
    private float sp(int v) {
        return Ui.dp(activity, v);
    }
}
