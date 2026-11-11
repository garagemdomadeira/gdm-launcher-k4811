package com.k4811.common.home;

import android.app.Activity;
import android.graphics.Point;
import android.view.Display;
import android.view.View;

import com.k4811.common.R;
import com.k4811.common.ui.DockView;

/**
 * Invisible grid of the home screen, in dp, with six slots (A–F); each holds at most one
 * {@link Module}. Where the slots sit depends on the screen ({@link #layout}):
 * <ul>
 * <li>Landscape (1280×720 design): below the top bar a 1232×476 area split into three columns —
 * left with one tall slot (A), center with a large slot over a wide one (B, C), right with three
 * small slots (D, E, F).</li>
 * <li>Portrait (e.g. 768×1024 units): the columns become rows — A across the whole width, then B and
 * C side by side, then D, E and F.</li>
 * </ul>
 */
public final class HomeGrid {
    private HomeGrid() {
    }

    /** Top bar height (voltmeter, screen off and settings sit in it). */
    public static final int TOP_BAR = 100;
    /** Margin around the slot area and gap between slots. */
    private static final int GAP = 24;
    /** Space kept above the dock. */
    private static final int BOTTOM = 16;

    /** Shape of a slot: every module has a layout for each one. */
    public enum Size {
        /** Narrower than tall (landscape A: 380×476). */
        TALL,
        /** Big and wide (landscape B: 372×282; portrait A, B, C). */
        LARGE,
        /** Wide and short (landscape C: 372×170). */
        WIDE,
        /** The small ones (landscape D–F: 432×142; portrait D–F). */
        SMALL
    }

    public enum Slot {
        LEFT("left", "A"),
        CENTER_TOP("center_top", "B"),
        CENTER_BOTTOM("center_bottom", "C"),
        RIGHT_TOP("right_top", "D"),
        RIGHT_MIDDLE("right_middle", "E"),
        RIGHT_BOTTOM("right_bottom", "F");

        /** Id stored in launcher_prefs (home_layout); named after the landscape position. */
        public final String id;
        /** Name shown in Settings: a letter, in reading order in both orientations (A…F). */
        public final String label;

        Slot(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public static Slot byId(String id) {
            for (Slot s : values()) if (s.id.equals(id)) return s;
            return null;
        }
    }

    /** Where a slot sits on this screen, in dp from the top-left corner. */
    public static final class Frame {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final Size size;

        Frame(int x, int y, int width, int height, Size size) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.size = size;
        }
    }

    /** Área que vai do alto de {@code top} ao pé de {@code bottom} (mesma coluna), tratada como alta. */
    public static Frame span(Frame top, Frame bottom) {
        return new Frame(top.x, top.y, top.width, bottom.y + bottom.height - top.y, Size.TALL);
    }

    /** Linha inteira de {@code left} até o fim de {@code right} (mesma altura), tratada como grande. */
    public static Frame row(Frame left, Frame right) {
        return new Frame(left.x, left.y, right.x + right.width - left.x, left.height, Size.LARGE);
    }

    /** The grid for one screen: its size in dp and each slot's frame. */
    public static final class Layout {
        public final boolean portrait;
        public final int screenWidth;
        public final int screenHeight;
        /** Height above the dock. */
        public final int contentHeight;
        private final Frame[] frames;

        Layout(boolean portrait, int screenWidth, int screenHeight, Frame[] frames) {
            this.portrait = portrait;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.contentHeight = screenHeight - DockView.HEIGHT_DP;
            this.frames = frames;
        }

        public Frame frame(Slot slot) {
            return frames[slot.ordinal()];
        }

        /**
         * A mesma grade espelhada (Configurações › "Inverter os lados"): deitada, o que estava à esquerda vai para a
         * direita (o "tocando agora" à direita e o PIP à esquerda); em pé, o de cima vai para baixo.
         */
        Layout mirrored() {
            Frame[] out = new Frame[frames.length];
            int areaEnd = contentHeight - BOTTOM;
            for (int i = 0; i < frames.length; i++) {
                Frame f = frames[i];
                out[i] = portrait
                    ? new Frame(f.x, TOP_BAR + areaEnd - f.y - f.height, f.width, f.height, f.size)
                    : new Frame(screenWidth - f.x - f.width, f.y, f.width, f.height, f.size);
            }
            return new Layout(portrait, screenWidth, screenHeight, out);
        }
    }

    /**
     * The grid for this activity, after Ui.applyDesignDensity: portrait when its area is taller than
     * wide (rows), otherwise the 1280×720 landscape design (columns). Uses the area the activity
     * really got once it's laid out (a visible navigation bar takes some), the whole screen before.
     */
    public static Layout layout(Activity a) {
        View content = a.findViewById(android.R.id.content);
        int wPx = content == null ? 0 : content.getWidth();
        int hPx = content == null ? 0 : content.getHeight();
        if (wPx <= 0 || hPx <= 0) {
            Display d = a.getWindowManager().getDefaultDisplay();
            Point size = new Point();
            d.getRealSize(size);
            wPx = size.x;
            hPx = size.y;
        }
        // barra da NWD na vertical (o dock fica acima dela): só a parte que cai sobre a área do app
        hPx -= com.k4811.common.system.BottomBar.reservedPx(a, content != null && content.getHeight() > 0 ? content : null);
        float density = a.getResources().getDisplayMetrics().density;
        int w = Math.round(wPx / density);
        int h = Math.round(hPx / density);
        // Tela física em pé = sempre a grade em linhas: a horizontal (1280 de largura) não cabe numa tela de 768.
        Display d = a.getWindowManager().getDefaultDisplay();
        Point real = new Point();
        d.getRealSize(real);
        boolean screenPortrait = real.y > real.x;
        Layout l = h > w || screenPortrait ? portrait(w, h) : landscape();
        return com.k4811.common.config.LauncherConfig.homeMirrored(a) ? l.mirrored() : l;
    }

    private static Layout landscape() {
        return new Layout(false, 1280, 720, new Frame[]{
            // dock de 128: a área vai de 100 a 576 (476 de altura). Centro mais estreito (372) e a coluna da
            // direita mais larga (432), para o PIP caber nela inteira.
            new Frame(24, 100, 380, 476, Size.TALL),
            new Frame(428, 100, 372, 282, Size.LARGE),
            new Frame(428, 406, 372, 170, Size.WIDE),
            new Frame(824, 100, 432, 142, Size.SMALL),
            new Frame(824, 267, 432, 142, Size.SMALL),
            new Frame(824, 434, 432, 142, Size.SMALL),
        });
    }

    /** Rows of 1, 2 and 3 slots sharing the height about 46 / 31 / 23 %. */
    private static Layout portrait(int w, int h) {
        int content = h - DockView.HEIGHT_DP;
        int available = content - BOTTOM - TOP_BAR - 2 * GAP;
        // Vertical mais equilibrado: o PIP (linha D, que com o espelho vai para o topo) deixa de ser uma faixa
        // fina (era 23%) e ganha altura; o "tocando agora" (linha A) continua grande, mas sem dominar a tela.
        // D = PIP (topo) é o maior (metade da tela); B = carro + relógio/OBD2 (meio) desce com o PIP maior;
        // A = "tocando agora" (embaixo, compacto, colado no dock). Tela em pé bem aproveitada.
        int rowA = Math.round(available * 0.20f);
        int rowB = Math.round(available * 0.30f);
        int rowD = available - rowA - rowB;
        int inner = w - 2 * GAP;
        int half = (inner - GAP) / 2;
        int third = (inner - 2 * GAP) / 3;
        int yB = TOP_BAR + rowA + GAP;
        int yD = yB + rowB + GAP;
        return new Layout(true, w, h, new Frame[]{
            new Frame(GAP, TOP_BAR, inner, rowA, Size.LARGE),
            new Frame(GAP, yB, half, rowB, Size.LARGE),
            new Frame(GAP + half + GAP, yB, half, rowB, Size.LARGE),
            new Frame(GAP, yD, third, rowD, Size.SMALL),
            new Frame(GAP + third + GAP, yD, third, rowD, Size.SMALL),
            new Frame(GAP + 2 * (third + GAP), yD, third, rowD, Size.SMALL),
        });
    }

    public enum Module {
        NOW_PLAYING("now_playing", R.string.settings_home_module_now_playing),
        CLOCK("clock", R.string.settings_home_module_clock),
        VOLUME("volume", R.string.settings_home_module_volume),
        FAN("fan", R.string.settings_home_module_fan),
        BLUETOOTH("bluetooth", R.string.settings_home_module_bluetooth),
        /** A picture the driver chose (see {@link HomeImage}). */
        IMAGE("image", R.string.settings_home_module_image),
        /** GPS speed (see {@link GpsClient}). */
        SPEED("speed", R.string.settings_home_module_speed),
        /** Latitude, longitude, altitude and heading. */
        GPS("gps", R.string.settings_home_module_gps),
        /** O carro em 3D (modelo, cor e placa) ou uma foto dele. */
        CAR("car", R.string.settings_home_module_car),
        /** Velocidade e vitais do motor pelo OBD2 (ELM327 Bluetooth); sem ele, a velocidade do GPS. */
        OBD("obd", R.string.settings_home_module_obd),
        /**
         * Um app rodando dentro da tela inicial (Waze, Maps…). Na coluna da direita com as outras áreas dela livres,
         * ocupa a coluna inteira.
         */
        PIP("pip", R.string.settings_home_module_pip);

        /** Id stored in launcher_prefs (home_layout). */
        public final String id;
        public final int titleRes;

        Module(String id, int titleRes) {
            this.id = id;
            this.titleRes = titleRes;
        }

        public static Module byId(String id) {
            for (Module m : values()) if (m.id.equals(id)) return m;
            return null;
        }
    }
}
