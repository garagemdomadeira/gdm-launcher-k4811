package com.k4811.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Button with an icon drawn on Canvas (no PNG or vector, works on any Android).
 * Icons are defined on a 24×24 grid and scaled to the view size.
 */
public class IconView extends View {

    public enum Icon {
        MENU, DIAG, POWER, EXIT, RADIO, CHEVRON_LEFT, CHEVRON_RIGHT, SKIP_PREV, SKIP_NEXT, KEYBOARD,
        MUSIC, BLUETOOTH, EQUALIZER, PLAY, PAUSE, PHONE, APPS, FAN, SETTINGS, HOME, BATTERY, VOLUME, VOLUME_MUTE, SCREEN_OFF, PLUS, MINUS,
        AUX, CAMERA_360, VIDEO, PHONE_LINK, CARPLAY, ANDROID_AUTO,
        STEREO, TOWER_FAR, SUN, MOON, HELP, NAVIGATION, WIFI, SIGNAL, GPS, CLOUD, RAIN
    }

    public enum Style { NONE, CIRCLE, SQUARE, FLAT_CIRCLE, OUTLINE_CIRCLE }

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private Icon icon = Icon.MENU;
    private Style style = Style.NONE;
    private int color = Ui.TEXT;
    private float iconScale = 0.55f;
    /** Força do sinal para WIFI e SIGNAL (0 a 4); as partes acima do nível ficam apagadas. */
    private int level = 4;

    public IconView(Context c) {
        this(c, null);
    }

    public IconView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        setFocusable(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dp(c, 1.5f));
    }

    public IconView set(Icon icon, Style style) {
        this.icon = icon;
        this.style = style;
        iconScale = style == Style.CIRCLE ? 0.42f : style == Style.SQUARE ? 0.5f : style == Style.FLAT_CIRCLE ? 0.45f : style == Style.OUTLINE_CIRCLE ? 0.38f : 0.62f;
        invalidate();
        return this;
    }

    /** Nível do sinal (0 a 4) dos ícones WIFI e SIGNAL. */
    public IconView level(int level) {
        int l = Math.max(0, Math.min(4, level));
        if (l != this.level) {
            this.level = l;
            invalidate();
        }
        return this;
    }

    public IconView color(int color) {
        this.color = color;
        invalidate();
        return this;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        boolean pressed = isPressed();
        drawBackground(c, w, h, pressed);

        // Icon box: 24×24 (or 56×24 for the double diagnostics icon).
        float boxW = icon == Icon.DIAG ? 56f : 24f;
        float size = Math.min(w, h) * iconScale;
        float unit = size / 24f;
        float left = (w - boxW * unit) / 2f;
        float top = (h - 24f * unit) / 2f;

        int col = !isEnabled() ? Ui.TEXT_DIM : (pressed && style == Style.NONE ? Ui.TEXT_MUTED : color);
        stroke.setColor(col);
        fill.setColor(col);
        stroke.setStrokeWidth(2f * unit);

        c.save();
        c.translate(left, top);
        c.scale(unit, unit);
        stroke.setStrokeWidth(2f);
        drawIcon(c);
        c.restore();
    }

    private void drawBackground(Canvas c, float w, float h, boolean pressed) {
        if (style == Style.CIRCLE) {
            float r = Math.min(w, h) / 2f - border.getStrokeWidth();
            float cx = w / 2f;
            float cy = h / 2f;
            int inner = pressed ? Ui.OUTLINE : Ui.SURFACE_TOP;
            bg.setShader(new RadialGradient(cx, cy - r * 0.3f, r * 1.2f, inner, Ui.PRESSED, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, bg);
            border.setColor(pressed ? Ui.ACCENT : Ui.OUTLINE);
            c.drawCircle(cx, cy, r, border);
        } else if (style == Style.OUTLINE_CIRCLE) {
            // Thin-line circle, no fill (minimalist main button).
            float r = Math.min(w, h) / 2f - Ui.dp(getContext(), 1);
            if (pressed) {
                bg.setShader(null);
                bg.setColor(Ui.PRESSED);
                c.drawCircle(w / 2f, h / 2f, r, bg);
            }
            border.setColor(pressed ? Ui.TEXT_DIM : Ui.OUTLINE);
            c.drawCircle(w / 2f, h / 2f, r, border);
        } else if (style == Style.FLAT_CIRCLE) {
            // Plain gray circle, no border (home screen mute button).
            bg.setShader(null);
            bg.setColor(pressed ? Ui.OUTLINE : Ui.SURFACE_TOP);
            c.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, bg);
        } else if (style == Style.SQUARE) {
            float r = Ui.dp(getContext(), 10);
            rect.set(0, 0, w, h);
            bg.setShader(null);
            bg.setColor(pressed ? Ui.OUTLINE : Ui.SURFACE_TOP);
            c.drawRoundRect(rect, r, r, bg);
        }
    }

    private void drawIcon(Canvas c) {
        switch (icon) {
            case MENU:
                c.drawLine(3, 6, 21, 6, stroke);
                c.drawLine(3, 12, 21, 12, stroke);
                c.drawLine(3, 18, 21, 18, stroke);
                break;
            case DIAG:
                drawBug(c);
                c.drawLine(31, 3, 27, 21, stroke);
                c.save();
                c.translate(32, 0);
                drawGear(c);
                c.restore();
                break;
            case POWER:
                rect.set(4, 5, 20, 21);
                c.drawArc(rect, -60, 300, false, stroke);
                c.drawLine(12, 2.5f, 12, 11, stroke);
                break;
            case RADIO:
                // Tabletop radio: antenna, body, speaker, display and buttons.
                c.drawLine(6, 8.5f, 17, 3, stroke);
                rect.set(2.5f, 8.5f, 21.5f, 20.5f);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                c.drawCircle(8.5f, 14.5f, 3.2f, stroke);
                c.drawCircle(8.5f, 14.5f, 0.9f, fill);
                rect.set(13.5f, 11.5f, 18.5f, 14.5f);
                c.drawRect(rect, fill);
                c.drawCircle(14.5f, 17.5f, 0.9f, fill);
                c.drawCircle(17.5f, 17.5f, 0.9f, fill);
                break;
            case SUN:
                c.drawCircle(12, 12, 4, stroke);
                for (int k = 0; k < 8; k++) {
                    double a = Math.toRadians(k * 45);
                    float cos = (float) Math.cos(a);
                    float sin = (float) Math.sin(a);
                    c.drawLine(12 + 7 * cos, 12 + 7 * sin, 12 + 9.5f * cos, 12 + 9.5f * sin, stroke);
                }
                break;
            case MOON:
                // Crescent: arc of the full moon (r 9) closed by the "bite" (r 7, top right).
                path.reset();
                path.moveTo(21, 12.79f);
                rect.set(3, 3, 21, 21);
                path.arcTo(rect, 5, 260);
                rect.set(9.84f, 0.16f, 23.84f, 14.16f);
                path.arcTo(rect, 216.5f, -163);
                path.close();
                c.drawPath(path, stroke);
                break;
            case STEREO:
                // Two channels: the classic pair of overlapping rings.
                c.drawCircle(8.5f, 12, 5.5f, stroke);
                c.drawCircle(15.5f, 12, 5.5f, stroke);
                break;
            case TOWER_FAR:
                // Radio mast sending short and long waves.
                c.drawCircle(12, 8.5f, 1.4f, fill);
                c.drawLine(12, 10.5f, 8, 21.5f, stroke);
                c.drawLine(12, 10.5f, 16, 21.5f, stroke);
                c.drawLine(9.3f, 17.5f, 14.7f, 17.5f, stroke);
                rect.set(8, 4.5f, 16, 12.5f);
                c.drawArc(rect, 140, 80, false, stroke);
                c.drawArc(rect, -40, 80, false, stroke);
                rect.set(4, 0.5f, 20, 16.5f);
                c.drawArc(rect, 145, 70, false, stroke);
                c.drawArc(rect, -35, 70, false, stroke);
                break;
            case EXIT:
                // Open door frame on the left + arrow exiting to the right.
                polyline(c, 13, 4, 4, 4, 4, 20, 13, 20);
                c.drawLine(9, 12, 21, 12, stroke);
                polyline(c, 17, 8, 21, 12, 17, 16);
                break;
            case CHEVRON_LEFT:
                polyline(c, 15, 5, 8, 12, 15, 19);
                break;
            case CHEVRON_RIGHT:
                polyline(c, 9, 5, 16, 12, 9, 19);
                break;
            case SKIP_PREV:
                c.drawLine(4, 6, 4, 18, stroke);
                triangle(c, 13, 6, 13, 18, 5.5f, 12);
                triangle(c, 21, 6, 21, 18, 13.5f, 12);
                break;
            case SKIP_NEXT:
                c.drawLine(20, 6, 20, 18, stroke);
                triangle(c, 11, 6, 11, 18, 18.5f, 12);
                triangle(c, 3, 6, 3, 18, 10.5f, 12);
                break;
            case KEYBOARD:
                rect.set(1.5f, 5, 22.5f, 19);
                stroke.setStrokeWidth(1.6f);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 5; col++) {
                        float x = 4.5f + col * 3.75f;
                        float y = 8 + row * 3.5f;
                        c.drawRect(x - 0.9f, y - 0.9f, x + 0.9f, y + 0.9f, fill);
                    }
                }
                c.drawLine(7.5f, 15.5f, 16.5f, 15.5f, stroke);
                break;
            case MUSIC:
                c.drawLine(14.5f, 5, 14.5f, 16, stroke);
                polyline(c, 14.5f, 5, 20, 7.2f, 20, 10.4f, 14.5f, 8.2f);
                rect.set(6.5f, 14, 14.5f, 20);
                c.drawOval(rect, fill);
                break;
            case BLUETOOTH:
                polyline(c, 7, 7, 17, 17, 12, 22, 12, 2, 17, 7, 7, 17);
                break;
            case EQUALIZER:
                slider(c, 5.5f, 9);
                slider(c, 12, 15);
                slider(c, 18.5f, 8);
                break;
            case PLAY:
                triangle(c, 8, 4.5f, 8, 19.5f, 19, 12);
                break;
            case PAUSE:
                rect.set(6, 4, 10, 20);
                c.drawRoundRect(rect, 1, 1, fill);
                rect.set(14, 4, 18, 20);
                c.drawRoundRect(rect, 1, 1, fill);
                break;
            case PHONE:
                c.save();
                c.rotate(-38, 12, 12);
                rect.set(8, 2.5f, 16, 21.5f);
                c.drawRoundRect(rect, 4, 4, stroke);
                c.drawLine(8, 7, 16, 7, stroke);
                c.drawLine(8, 17, 16, 17, stroke);
                c.restore();
                break;
            case APPS:
                rect.set(3, 3, 10, 10);
                c.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                rect.set(14, 3, 21, 10);
                c.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                rect.set(3, 14, 10, 21);
                c.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                rect.set(14, 14, 21, 21);
                c.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                break;
            case SETTINGS:
                drawGear(c);
                break;
            case BATTERY:
                // Car battery: box with two terminals, + and −.
                rect.set(2.5f, 7, 21.5f, 20);
                c.drawRoundRect(rect, 2, 2, stroke);
                c.drawLine(6, 7, 6, 4.5f, stroke);
                c.drawLine(18, 7, 18, 4.5f, stroke);
                c.drawLine(5, 13.5f, 9, 13.5f, stroke);
                c.drawLine(7, 11.5f, 7, 15.5f, stroke);
                c.drawLine(15, 13.5f, 19, 13.5f, stroke);
                break;
            case CLOUD:
                cloud(c, 0);
                break;
            case RAIN:
                // Nuvem mais alta com três gotas inclinadas embaixo.
                cloud(c, -3);
                c.drawLine(8.5f, 17.5f, 7.5f, 20.5f, stroke);
                c.drawLine(12.5f, 17.5f, 11.5f, 20.5f, stroke);
                c.drawLine(16.5f, 17.5f, 15.5f, 20.5f, stroke);
                break;
            case WIFI: {
                // Ponto e três arcos; os arcos acima da força do sinal ficam apagados.
                int base = stroke.getAlpha();
                c.drawCircle(12, 19.5f, 1.7f, fill);
                float[] radii = {5.5f, 10f, 14.5f};
                for (int i = 0; i < radii.length; i++) {
                    stroke.setAlpha(level >= i + 2 ? base : base * 30 / 100);
                    rect.set(12 - radii[i], 19.5f - radii[i], 12 + radii[i], 19.5f + radii[i]);
                    c.drawArc(rect, 225, 90, false, stroke);
                }
                stroke.setAlpha(base);
                break;
            }
            case SIGNAL: {
                // Quatro barras crescentes; as acima da força do sinal ficam só no contorno apagado.
                int base = fill.getAlpha();
                for (int i = 0; i < 4; i++) {
                    float x = 3.5f + i * 5f;
                    float top = 18.5f - (i + 1) * 3.6f;
                    rect.set(x, top, x + 3f, 20.5f);
                    fill.setAlpha(level > i ? base : base * 25 / 100);
                    c.drawRoundRect(rect, 0.8f, 0.8f, fill);
                }
                fill.setAlpha(base);
                break;
            }
            case GPS:
                // Alfinete de localização com o miolo vazado.
                path.reset();
                path.moveTo(12, 21.5f);
                path.cubicTo(7.5f, 16.5f, 4.5f, 13, 4.5f, 9.5f);
                rect.set(4.5f, 2, 19.5f, 17);
                path.arcTo(rect, 180, 180, false);
                path.cubicTo(19.5f, 13, 16.5f, 16.5f, 12, 21.5f);
                path.close();
                c.drawPath(path, stroke);
                c.drawCircle(12, 9.5f, 2.6f, stroke);
                break;
            case NAVIGATION:
                // Seta de navegação (GPS): ponta para cima com o recorte embaixo.
                path.reset();
                path.moveTo(12, 2.5f);
                path.lineTo(19.5f, 21);
                path.lineTo(12, 16.8f);
                path.lineTo(4.5f, 21);
                path.close();
                c.drawPath(path, stroke);
                break;
            case HOME:
                // Outlined house: roof, body and door.
                polyline(c, 2.5f, 11.5f, 12, 3, 21.5f, 11.5f);
                polyline(c, 5, 9.5f, 5, 20.5f, 19, 20.5f, 19, 9.5f);
                polyline(c, 10, 20.5f, 10, 14.5f, 14, 14.5f, 14, 20.5f);
                break;
            case PLUS:
                c.drawLine(4, 12, 20, 12, stroke);
                c.drawLine(12, 4, 12, 20, stroke);
                break;
            case MINUS:
                c.drawLine(4, 12, 20, 12, stroke);
                break;
            case HELP:
                // Círculo com ponto de interrogação (botão de ajuda nas configurações).
                c.drawCircle(12, 12, 9.5f, stroke);
                path.reset();
                path.moveTo(8.5f, 9.2f);
                path.quadTo(8.5f, 6.2f, 12, 6.2f);
                path.quadTo(15.5f, 6.2f, 15.5f, 9.2f);
                path.quadTo(15.5f, 11.2f, 12.5f, 12.2f);
                path.lineTo(12.5f, 14.5f);
                c.drawPath(path, stroke);
                c.drawCircle(12.5f, 17.5f, 1.1f, fill);
                break;
            case SCREEN_OFF:
                // Monitor with stand and a diagonal slash (screen off).
                rect.set(2.5f, 4, 21.5f, 16.5f);
                c.drawRoundRect(rect, 2, 2, stroke);
                c.drawLine(12, 16.5f, 12, 20, stroke);
                c.drawLine(8, 20.5f, 16, 20.5f, stroke);
                c.drawLine(4, 2.5f, 20, 18.5f, stroke);
                break;
            case VOLUME:
            case VOLUME_MUTE:
                // Filled speaker.
                path.reset();
                path.moveTo(3, 9);
                path.lineTo(7, 9);
                path.lineTo(12, 4.5f);
                path.lineTo(12, 19.5f);
                path.lineTo(7, 15);
                path.lineTo(3, 15);
                path.close();
                c.drawPath(path, fill);
                if (icon == Icon.VOLUME) {
                    rect.set(9.5f, 8, 17.5f, 16);
                    c.drawArc(rect, -50, 100, false, stroke);
                    rect.set(9, 4.5f, 22, 19.5f);
                    c.drawArc(rect, -50, 100, false, stroke);
                } else {
                    c.drawLine(15, 9, 21, 15, stroke);
                    c.drawLine(21, 9, 15, 15, stroke);
                }
                break;
            case AUX:
                // 3.5 mm (P2) plug: tip, rings and body, with the cable coming out the bottom.
                c.drawLine(12, 2.5f, 12, 5, stroke);
                rect.set(10, 5, 14, 13);
                c.drawRoundRect(rect, 1, 1, stroke);
                c.drawLine(10, 8, 14, 8, stroke);
                rect.set(8.5f, 13, 15.5f, 18);
                c.drawRoundRect(rect, 1.5f, 1.5f, fill);
                c.drawLine(12, 18, 12, 21.5f, stroke);
                break;
            case CAMERA_360:
                // Top-down car inside a ring of arrows (surround view).
                rect.set(9, 6.5f, 15, 17.5f);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                c.drawLine(9.5f, 10, 14.5f, 10, stroke);
                rect.set(2.5f, 2.5f, 21.5f, 21.5f);
                c.drawArc(rect, -60, 120, false, stroke);
                c.drawArc(rect, 120, 120, false, stroke);
                break;
            case PHONE_LINK:
                // Phone mirrored on the car screen (Z-Link: CarPlay / Android Auto).
                rect.set(2.5f, 5.5f, 8.5f, 18.5f);
                c.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                c.drawLine(4.8f, 16, 6.2f, 16, stroke);
                rect.set(11.5f, 7, 21.5f, 15);
                c.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                c.drawLine(16.5f, 15, 16.5f, 18.5f, stroke);
                c.drawLine(14, 18.5f, 19, 18.5f, stroke);
                c.drawLine(8.5f, 11, 11.5f, 11, stroke);
                break;
            case CARPLAY:
                // Rounded square with a play triangle (in the spirit of CarPlay, not its logo).
                rect.set(3, 3, 21, 21);
                c.drawRoundRect(rect, 5.5f, 5.5f, stroke);
                triangle(c, 9.5f, 7.8f, 9.5f, 16.2f, 16.5f, 12);
                break;
            case ANDROID_AUTO:
                // Navigation arrow shaped like an "A" (in the spirit of Android Auto, not its logo).
                path.reset();
                path.moveTo(12, 3);
                path.lineTo(20.5f, 20.5f);
                path.lineTo(12, 16);
                path.lineTo(3.5f, 20.5f);
                path.close();
                c.drawPath(path, stroke);
                break;
            case VIDEO:
                rect.set(2.5f, 5, 21.5f, 19);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                triangle(c, 10, 8.5f, 10, 15.5f, 15.5f, 12);
                break;
            case FAN:
                c.drawCircle(12, 12, 2.2f, fill);
                for (int i = 0; i < 3; i++) {
                    c.save();
                    c.rotate(i * 120, 12, 12);
                    path.reset();
                    path.moveTo(12, 12);
                    path.quadTo(16, 5, 12, 3);
                    path.quadTo(8, 5, 12, 12);
                    c.drawPath(path, fill);
                    c.restore();
                }
                c.drawCircle(12, 12, 9.5f, stroke);
                break;
        }
    }

    /** Equalizer slider with the knob in the middle, without the line crossing the circle. */
    private void slider(Canvas c, float x, float knobY) {
        c.drawLine(x, 3, x, knobY - 3.2f, stroke);
        c.drawLine(x, knobY + 3.2f, x, 21, stroke);
        c.drawCircle(x, knobY, 2.5f, stroke);
    }

    /** Contorno de nuvem (base reta, três bolhas), deslocado {@code dy} na vertical. */
    private void cloud(Canvas c, float dy) {
        path.reset();
        path.moveTo(6.5f, 18);
        path.cubicTo(3.5f, 18, 2.5f, 15.5f, 3.5f, 13.5f);
        path.cubicTo(4.3f, 12, 5.8f, 11.4f, 7.2f, 11.8f);
        path.cubicTo(7.6f, 8.5f, 10.2f, 6.5f, 13, 6.8f);
        path.cubicTo(15.8f, 7.1f, 17.6f, 9.2f, 17.8f, 11.6f);
        path.cubicTo(20.2f, 11.6f, 21.5f, 13.5f, 21, 15.6f);
        path.cubicTo(20.6f, 17.2f, 19.4f, 18, 17.8f, 18);
        path.close();
        path.offset(0, dy);
        c.drawPath(path, stroke);
    }

    private void drawBug(Canvas c) {
        rect.set(8, 9, 16, 21);
        c.drawOval(rect, fill);
        c.drawCircle(12, 6.5f, 2.6f, fill);
        stroke.setStrokeWidth(1.6f);
        c.drawLine(10.8f, 4.5f, 9, 2, stroke);
        c.drawLine(13.2f, 4.5f, 15, 2, stroke);
        c.drawLine(8.5f, 12, 4.5f, 10, stroke);
        c.drawLine(8, 15, 4, 15, stroke);
        c.drawLine(8.5f, 18, 4.5f, 20.5f, stroke);
        c.drawLine(15.5f, 12, 19.5f, 10, stroke);
        c.drawLine(16, 15, 20, 15, stroke);
        c.drawLine(15.5f, 18, 19.5f, 20.5f, stroke);
        stroke.setStrokeWidth(2f);
    }

    private void drawGear(Canvas c) {
        stroke.setStrokeWidth(2.6f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float x1 = 12 + (float) Math.cos(a) * 6.5f;
            float y1 = 12 + (float) Math.sin(a) * 6.5f;
            float x2 = 12 + (float) Math.cos(a) * 9.5f;
            float y2 = 12 + (float) Math.sin(a) * 9.5f;
            c.drawLine(x1, y1, x2, y2, stroke);
        }
        stroke.setStrokeWidth(2f);
        c.drawCircle(12, 12, 6.2f, stroke);
        c.drawCircle(12, 12, 2.2f, stroke);
    }

    private void polyline(Canvas c, float... p) {
        path.reset();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        c.drawPath(path, stroke);
    }

    private void triangle(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        c.drawPath(path, fill);
    }
}
