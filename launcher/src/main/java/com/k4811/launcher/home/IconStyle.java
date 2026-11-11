package com.k4811.launcher.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.k4811.launcher.R;

/**
 * Formato e tamanho dos ícones dos apps (ideia da LecoAuto): original, redondo ou quadrado arredondado, na gaveta, na
 * dock e no seletor de apps; o tamanho vale para a gaveta. Ícone adaptativo (Android 8+) é recortado pelas camadas, como
 * nos celulares; ícone antigo vai inteiro para dentro de um fundo com o formato escolhido.
 */
public final class IconStyle {
    private static final String PREFS = "gdm_home";
    public static final String KEY_SHAPE = "icon_shape";
    public static final String KEY_SIZE = "icon_size";
    public static final int ORIGINAL = 0;
    public static final int ROUND = 1;
    public static final int SQUIRCLE = 2;
    public static final int SMALL = 0;
    public static final int NORMAL = 1;
    public static final int LARGE = 2;
    private static final float[] SCALES = {0.82f, 1f, 1.18f};

    private IconStyle() {
    }

    public static int shape(Context c) {
        return com.k4811.common.config.LauncherConfig.iconShape(c);
    }

    public static int size(Context c) {
        return com.k4811.common.config.LauncherConfig.iconSize(c);
    }

    /** Multiplicador do tamanho do ícone na gaveta. */
    public static float scale(Context c) {
        return SCALES[size(c)];
    }

    /** O ícone desenhado num bitmap {@code px}×{@code px} no formato escolhido; null se não deu. */
    public static Bitmap render(Context c, Drawable d, int px) {
        return render(c, d, px, false);
    }

    /**
     * {@code full}: ícone já desenhado para ocupar tudo (os trocados pela pessoa, {@link CustomIcons}): só é recortado
     * pelo formato, sem o fundo e a margem que os ícones antigos recebem.
     */
    public static Bitmap render(Context c, Drawable d, int px, boolean full) {
        if (d == null || px <= 0) return null;
        try {
            Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            int shape = shape(c);
            if (shape == ORIGINAL) {
                d.setBounds(0, 0, px, px);
                d.draw(canvas);
                return b;
            }
            Path mask = new Path();
            RectF r = new RectF(0, 0, px, px);
            if (shape == ROUND) mask.addOval(r, Path.Direction.CW);
            else mask.addRoundRect(r, px * 0.26f, px * 0.26f, Path.Direction.CW);
            canvas.clipPath(mask);
            if (Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable) {
                // as camadas têm 108 dp com 72 dp à vista: sobra 1/6 de cada lado
                AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
                int extra = Math.round(px / 4f);
                for (Drawable layer : new Drawable[] {a.getBackground(), a.getForeground()}) {
                    if (layer == null) continue;
                    layer.setBounds(-extra, -extra, px + extra, px + extra);
                    layer.draw(canvas);
                }
            } else if (full) {
                d.setBounds(0, 0, px, px);
                d.draw(canvas);
            } else {
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                bg.setColor(com.k4811.common.ui.Ui.SURFACE_HIGH);
                canvas.drawPath(mask, bg);
                int inset = Math.round(px * 0.17f);
                d.setBounds(inset, inset, px - inset, px - inset);
                d.draw(canvas);
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
