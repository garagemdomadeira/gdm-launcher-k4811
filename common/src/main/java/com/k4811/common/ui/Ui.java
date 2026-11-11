package com.k4811.common.ui;

import com.k4811.common.config.LauncherConfig;

import com.k4811.common.R;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

/** Layout colors, fonts and scale. The design is done at 1280×720 "dp". */
public final class Ui {
    private Ui() {
    }

    public static final int DESIGN_WIDTH = 1280;
    public static final int DESIGN_HEIGHT = 720;

    // Theme colors: set by applyTheme from the theme chosen in Settings (see Palette).
    // Not final on purpose: they change when the user picks another theme color.
    public static int BG;
    public static int BG_SOFT;
    public static int SURFACE;
    public static int SURFACE_HIGH;
    public static int SURFACE_TOP;
    public static int PRESSED;
    public static int HAIRLINE;
    public static int HAIRLINE_STRONG;
    public static int OUTLINE;
    public static int OUTLINE_STRONG;
    public static int TEXT_DIM;
    public static int TEXT_MUTED;
    public static int TEXT_SOFT;
    public static int TEXT;
    public static int ACCENT;
    public static int ACCENT_FILL;
    public static int ACCENT_DIM;

    // Semantic colors: the same in every theme.
    public static final int DANGER = Palette.DANGER;
    public static final int WARNING = Palette.WARNING;
    public static final int SUCCESS = Palette.SUCCESS;

    /** Palette in use; the default theme until an Activity applies the chosen one. */
    private static Palette palette;
    private static int paletteTheme = Integer.MIN_VALUE;
    private static boolean paletteDark = true;

    static {
        use(Palette.DEFAULT, true);
    }

    private static void use(int theme, boolean dark) {
        if (theme == paletteTheme && dark == paletteDark) return;
        paletteTheme = theme;
        paletteDark = dark;
        palette = Palette.of(theme, dark);
        BG = palette.bg;
        BG_SOFT = palette.bgSoft;
        SURFACE = palette.surface;
        SURFACE_HIGH = palette.surfaceHigh;
        SURFACE_TOP = palette.surfaceTop;
        PRESSED = palette.pressed;
        HAIRLINE = palette.hairline;
        HAIRLINE_STRONG = palette.hairlineStrong;
        OUTLINE = palette.outline;
        OUTLINE_STRONG = palette.outlineStrong;
        TEXT_DIM = palette.textDim;
        TEXT_MUTED = palette.textMuted;
        TEXT_SOFT = palette.textSoft;
        TEXT = palette.text;
        ACCENT = palette.accent;
        ACCENT_FILL = palette.accentFill;
        ACCENT_DIM = palette.accentDim;
    }

    /**
     * Loads the theme chosen in Settings and themes the Activity: window background and every
     * layout it inflates from now on ({@link ThemeInflater}). Must run before setContentView.
     */
    /** Só as cores do tema escolhido, para telas sem Activity (ex.: janela flutuante de um serviço). */
    public static void applyThemeColors(Context c) {
        use(LauncherConfig.theme(c), LauncherConfig.darkMode(c));
    }

    public static void applyTheme(Activity a) {
        use(LauncherConfig.theme(a), LauncherConfig.darkMode(a));
        ThemeInflater.install(a);
        a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
    }

    /**
     * Theme version of a literal color written with a default-theme value (layouts, styles and
     * views that still carry hex colors), or null if it isn't one of them.
     */
    public static Integer themedLiteral(int color) {
        switch (color) {
            case 0xFF050608: case 0xFF090A0C: return BG;
            case 0xFF0B0D10: case 0xFF0C0E11: case 0xFF0B0E13: return BG_SOFT;
            case 0xFF15181C: case 0xFF1A1D21: return SURFACE;
            case 0xFF14171B: case 0xFF101215: case 0xFF121417: case 0xFF131518: case 0xFF181B20:
                return PRESSED;
            case 0xFF22262B: case 0xFF23272D: case 0xFF24272C: case 0xFF262A30: return SURFACE_HIGH;
            case 0xFF2B3036: case 0xFF2A2E34: case 0xFF2B2E33: return SURFACE_TOP;
            case 0xFF1C1F24: return HAIRLINE;
            case 0xFF2C3036: return HAIRLINE_STRONG;
            case 0xFF353A41: case 0xFF343A41: case 0xFF3A4048: case 0xFF3A3F46: case 0xFF3A3E44:
                return OUTLINE;
            case 0xFF555B63: case 0xFF4A4F57: case 0xFF4A5058: return OUTLINE_STRONG;
            case 0xFF5D636B: case 0xFF5C626B: case 0xFF6A7079: return TEXT_DIM;
            case 0xFF8D949D: case 0xFF8E949C: case 0xFF7D838C: case 0xFF9AA0A8: return TEXT_MUTED;
            case 0xFFB4B7BC: case 0xFFC9CCD1: case 0xFFC6CCD4: return TEXT_SOFT;
            case 0xFFE9EBEE: return TEXT;
            default: return null;
        }
    }

    /** Font the cached typefaces below belong to; reloaded when the user picks another one. */
    private static String loadedFont;
    private static Typeface regular;
    private static Typeface medium;
    private static Typeface semiBold;

    public static Typeface regular(Context c) {
        ensureFont(c);
        if (regular == null) regular = load(c, "fonts/" + loadedFont + "-400.ttf");
        return regular;
    }

    public static Typeface medium(Context c) {
        ensureFont(c);
        if (medium == null) medium = load(c, "fonts/" + loadedFont + "-500.ttf");
        return medium;
    }

    public static Typeface semiBold(Context c) {
        ensureFont(c);
        if (semiBold == null) semiBold = load(c, "fonts/" + loadedFont + "-600.ttf");
        return semiBold;
    }

    private static void ensureFont(Context c) {
        String font = com.k4811.common.config.LauncherConfig.font(c);
        if (font.equals(loadedFont)) return;
        loadedFont = font;
        regular = null;
        medium = null;
        semiBold = null;
    }

    private static Typeface load(Context c, String path) {
        try {
            return Typeface.createFromAsset(c.getApplicationContext().getAssets(), path);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    /** Applies the medium font to every TextView in the tree (specific ones are adjusted later). */
    public static void applyFont(View v) {
        if (v instanceof TextView) {
            ((TextView) v).setTypeface(medium(v.getContext()));
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i));
        }
    }

    /**
     * Applies the language chosen in Settings to the context and app resources. English lives in
     * values/ (the default); every other language in values-<tag>/.
     */
    public static void applyLanguage(Context c) {
        String tag = com.k4811.common.config.LauncherConfig.language(c);
        Locale locale;
        if ("pt".equals(tag)) locale = new Locale("pt", "BR");
        else if ("zh".equals(tag)) locale = new Locale("zh", "CN");
        else locale = new Locale(tag);
        Locale.setDefault(locale);
        setLocale(c.getResources(), locale);
        setLocale(c.getApplicationContext().getResources(), locale);
    }

    @SuppressWarnings("deprecation")
    private static void setLocale(Resources res, Locale locale) {
        Configuration config = new Configuration(res.getConfiguration());
        if (locale.equals(config.locale)) return;
        config.locale = locale;
        if (android.os.Build.VERSION.SDK_INT >= 17) config.setLayoutDirection(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    /**
     * Makes 1 dp = 1 pixel of a 1280×720 layout, whatever the head unit's density.
     * Also ignores the system font scale.
     */
    public static void applyDesignDensity(Activity a) {
        // Every screen calls this before setContentView: the right place to apply the language
        // and the theme too.
        applyLanguage(a);
        applyTheme(a);
        Display d = a.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        d.getRealSize(size);
        int w = Math.max(size.x, size.y);
        int h = Math.min(size.x, size.y);
        float density = Math.min(w / (float) DESIGN_WIDTH, h / (float) DESIGN_HEIGHT);
        set(a.getResources().getDisplayMetrics(), density);
        set(a.getApplicationContext().getResources().getDisplayMetrics(), density);
    }

    private static void set(DisplayMetrics m, float density) {
        m.density = density;
        m.scaledDensity = density;
        m.densityDpi = Math.round(density * DisplayMetrics.DENSITY_DEFAULT);
    }

    /** Fullscreen: hides the status and navigation bars and draws beneath them. */
    @SuppressWarnings("deprecation")
    public static void hideSystemBars(android.view.Window window) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        window.getDecorView().setSystemUiVisibility(flags);
        // FLAG_FULLSCREEN esconde a barra de status no nível da janela, de forma persistente: o SYSTEM_UI_FLAG
        // sozinho cai quando a janela perde o foco (ex.: um app abrindo no display virtual do PIP), e a barra do
        // Android voltava por cima da barra da suíte.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * Keeps a single-line TextView within {@code maxWidthDp}: shrinks the text size (down to 60% of
     * the original) whenever the text doesn't fit, e.g. long translations or wider fonts.
     */
    public static void autoFit(TextView tv, int maxWidthDp) {
        autoFit(tv, tv.getTextSize(), maxWidthDp);
    }

    /**
     * Same, with the full text size given in px. Calling it again on the same view replaces the size
     * and width (the home screen does it when a module moves to a slot of another size).
     */
    public static void autoFit(final TextView tv, float maxPx, int maxWidthDp) {
        float[] spec = (float[]) tv.getTag(com.k4811.common.R.id.auto_fit);
        boolean installed = spec != null;
        if (!installed) {
            spec = new float[2];
            tv.setTag(com.k4811.common.R.id.auto_fit, spec);
        }
        spec[0] = maxPx;
        spec[1] = dp(tv.getContext(), maxWidthDp);
        tv.setSingleLine(true);
        if (!installed) {
            tv.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    fit(tv);
                }
            });
        }
        fit(tv);
    }

    private static void fit(TextView tv) {
        float[] spec = (float[]) tv.getTag(com.k4811.common.R.id.auto_fit);
        String text = tv.getText().toString();
        if (tv.getTransformationMethod() != null) {
            CharSequence shown = tv.getTransformationMethod().getTransformation(text, tv);
            if (shown != null) text = shown.toString();
        }
        android.text.TextPaint paint = new android.text.TextPaint(tv.getPaint());
        float max = spec[0];
        float size = max;
        paint.setTextSize(size);
        while (size > max * 0.6f && paint.measureText(text) > spec[1]) {
            size -= 1f;
            paint.setTextSize(size);
        }
        if (size != tv.getTextSize()) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }

    /** Screen taller than wide (768×1024 head units). */
    public static boolean isPortrait(Context c) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        return m.heightPixels > m.widthPixels;
    }

    public static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }
}
