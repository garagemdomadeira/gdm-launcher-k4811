package com.k4811.radio.ui;

import com.k4811.common.ui.Ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

/**
 * Preset card, a rounded rectangle: the frequency large in the middle and the memory number tiny
 * in the top-left corner. Selected = glowing cyan border.
 */
public class PresetCardView extends View {

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint indexPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint freqPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Path logoClip = new Path();
    private final RectF rect = new RectF();
    private final RectF logoRect = new RectF();
    private final Rect logoSrc = new Rect();

    private int index;
    private String freq = "";
    private boolean empty = true;
    private boolean selectedCard;
    private Bitmap logo;
    private String logoKey = "";

    public PresetCardView(Context c) {
        this(c, null);
    }

    public PresetCardView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        setLongClickable(true);
        // The glow's BlurMaskFilter/shadowLayer require a software layer.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        borderPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setColor(Ui.ACCENT);
        indexPaint.setTypeface(Ui.medium(c));
        indexPaint.setTextSize(Ui.dp(c, 15));
        freqPaint.setTypeface(Ui.semiBold(c));
        freqPaint.setTextAlign(Paint.Align.CENTER);
        bitmapPaint.setFilterBitmap(true);
    }

    /** Logo da estacao desta memoria (ou null). A key identifica a estacao (ex.: FM_98.5) e evita recarregar. */
    public void setLogo(String key, Bitmap bmp) {
        this.logoKey = key == null ? "" : key;
        if (this.logo == bmp) return;
        this.logo = bmp;
        invalidate();
    }

    /** Estacao cujo logo esta (ou esta sendo) carregado, para o chamador nao recarregar o mesmo. */
    public String logoKey() {
        return logoKey;
    }

    /** @param freqText frequency text ("98.5") or empty if the preset is unused */
    public void bind(int index, String freqText, boolean empty, boolean selected) {
        String f = empty ? "—" : freqText;
        boolean changed = this.index != index || !TextUtils.equals(freq, f)
            || this.empty != empty || selectedCard != selected;
        this.index = index;
        this.freq = f;
        this.empty = empty;
        this.selectedCard = selected;
        if (changed) invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        Context ctx = getContext();
        float inset = Ui.dp(ctx, 6); // room for the glow
        float l = inset;
        float t = inset;
        float r = getWidth() - inset;
        float b = getHeight() - inset;
        float round = Ui.dp(ctx, 12);

        rect.set(l, t, r, b);
        shape.reset();
        shape.addRoundRect(rect, round, round, Path.Direction.CW);

        bodyPaint.setColor(isPressed() ? Ui.OUTLINE : Ui.SURFACE);
        c.drawPath(shape, bodyPaint);

        // Border (+ glow when selected).
        if (selectedCard) {
            glowPaint.setStrokeWidth(Ui.dp(ctx, 6));
            glowPaint.setShadowLayer(Ui.dp(ctx, 10), 0, 0, Ui.ACCENT);
            glowPaint.setAlpha(110);
            c.drawPath(shape, glowPaint);
            borderPaint.setColor(Ui.ACCENT);
            borderPaint.setStrokeWidth(Ui.dp(ctx, 2.5f));
        } else {
            borderPaint.setColor(Ui.OUTLINE);
            borderPaint.setStrokeWidth(Ui.dp(ctx, 1.5f));
        }
        c.drawPath(shape, borderPaint);

        float pad = Ui.dp(ctx, 12);

        // Logo da estacao (se houver): quadrado arredondado a esquerda; a frequencia vai para a direita.
        boolean hasLogo = logo != null && !empty;
        float freqL = l;
        if (hasLogo) {
            float m = Ui.dp(ctx, 9);
            float side = (b - t) - m * 2;
            float lx = l + m;
            float ly = t + m;
            logoRect.set(lx, ly, lx + side, ly + side);
            logoClip.reset();
            logoClip.addRoundRect(logoRect, round * 0.7f, round * 0.7f, Path.Direction.CW);
            int bw = logo.getWidth(), bh = logo.getHeight(), s = Math.min(bw, bh);
            logoSrc.set((bw - s) / 2, (bh - s) / 2, (bw + s) / 2, (bh + s) / 2);
            c.save();
            c.clipPath(logoClip);
            c.drawBitmap(logo, logoSrc, logoRect, bitmapPaint);
            c.restore();
            freqL = lx + side;
        }

        // Canto superior esquerdo: o numero da memoria, pequeno (com sombra quando sobre o logo).
        indexPaint.setColor(selectedCard ? Ui.ACCENT : Ui.TEXT_DIM);
        indexPaint.setShadowLayer(hasLogo ? Ui.dp(ctx, 3) : 0, 0, 0, 0xFF000000);
        c.drawText(String.valueOf(index + 1), l + pad, t + pad - indexPaint.ascent(), indexPaint);
        indexPaint.clearShadowLayer();

        // Frequencia, a maior que couber na regiao (toda a largura, ou so a direita do logo).
        float width = r - freqL - pad * 2;
        if (width <= 0) return;
        float size = Math.min((b - t) * 0.48f, Ui.dp(ctx, 52));
        freqPaint.setTextSize(size);
        float fw = freqPaint.measureText(freq);
        if (fw > width) freqPaint.setTextSize(size * width / fw);
        freqPaint.setColor(selectedCard ? Ui.ACCENT : empty ? Ui.TEXT_MUTED : Ui.TEXT);
        float base = (t + b) / 2f - (freqPaint.descent() + freqPaint.ascent()) / 2f;
        c.drawText(freq, (freqL + r) / 2f, base, freqPaint);
    }
}
