package com.k4811.launcher.car;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Traseira de carro desenhada em vetor (vista de trás, um pouco acima), em coordenadas de base: centro em (0, 0),
 * chão em y = 100, largura de uns 190. Cada {@link Model} é um conjunto de medidas ({@link Spec}); as curvas são as
 * mesmas para todos, então um modelo novo é só uma linha de medidas.
 *
 * <p>Separado em duas camadas para a animação ficar leve: {@link #drawStatic} (carroceria, vidro, lanternas
 * apagadas, para-choque, placa, estepe), que o {@link Car3DView} guarda num bitmap, e {@link #drawTires} /
 * {@link #drawLights}, desenhados a cada quadro (pneus girando, lanternas e luz de freio).
 */
final class CarPainter {

    enum Model { ECOSPORT, SUV, HATCH, SEDAN, PICKUP }

    /** Cores de pintura oferecidas no menu. */
    static final int[] COLORS = {
        0xFFF1F3F5, // branco
        0xFFB9C0C8, // prata
        0xFF5F666E, // cinza
        0xFF1B1E23, // preto
        0xFFA8141F, // vermelho
        0xFF1D4C93, // azul
    };

    /** Lanternas: nos cantos (SUV/hatch), largas com faixa no meio (SUV novo, sedã) ou em pé (picape). */
    private static final int LIGHT_CORNER = 0;
    private static final int LIGHT_BAR = 1;
    private static final int LIGHT_TALL = 2;

    /** Medidas de um modelo (y para baixo; meias-larguras). */
    static final class Spec {
        float roofY, roofHalf, roofR;
        float beltY, shoulderHalf, bodyHalf, lightY;
        float bumperTop, bumperBottom;
        float trackHalf, tireHalf;
        float glassTop, glassTopHalf, glassBottom, glassBottomHalf;
        /** Picape: altura da borda da caçamba e meia-largura da cabine. */
        float bedY, cabHalf;
        boolean spoiler, rails, mast, spare, darkBumper, plateOnBumper, glassFrame, wiper;
        int lights;
    }

    static Spec spec(Model m) {
        Spec s = new Spec();
        switch (m) {
            case ECOSPORT:
                // SUV alto e estreito no teto, vidro grande com moldura preta, estepe na tampa, para-choque preto
                set(s, -80, 58, 10, -16, 83, 93, 2, 50, 84, 73, 18);
                glass(s, -72, 54, -20, 73);
                s.spoiler = s.rails = s.mast = s.spare = s.darkBumper = s.plateOnBumper = s.glassFrame = s.wiper = true;
                s.lights = LIGHT_CORNER;
                break;
            case SUV:
                set(s, -76, 62, 12, -18, 86, 95, -6, 46, 84, 75, 19);
                glass(s, -68, 58, -24, 72);
                s.spoiler = s.rails = s.glassFrame = s.wiper = true;
                s.lights = LIGHT_BAR;
                break;
            case HATCH:
                set(s, -64, 58, 12, -12, 84, 92, 0, 44, 82, 72, 17);
                glass(s, -57, 55, -16, 70);
                s.spoiler = s.glassFrame = s.wiper = true;
                s.lights = LIGHT_CORNER;
                break;
            case SEDAN:
                set(s, -54, 52, 14, -14, 86, 94, 8, 46, 80, 73, 17);
                glass(s, -49, 49, -18, 70);
                s.lights = LIGHT_BAR;
                break;
            default: // PICKUP
                set(s, -86, 56, 10, -24, 96, 96, 10, 52, 86, 78, 21);
                glass(s, -78, 46, -36, 58);
                s.bedY = -24;
                s.cabHalf = 70;
                s.plateOnBumper = true;
                s.lights = LIGHT_TALL;
                break;
        }
        return s;
    }

    private static void set(Spec s, float roofY, float roofHalf, float roofR, float beltY, float shoulderHalf,
                            float bodyHalf, float lightY, float bumperTop, float bumperBottom, float trackHalf,
                            float tireHalf) {
        s.roofY = roofY;
        s.roofHalf = roofHalf;
        s.roofR = roofR;
        s.beltY = beltY;
        s.shoulderHalf = shoulderHalf;
        s.bodyHalf = bodyHalf;
        s.lightY = lightY;
        s.bumperTop = bumperTop;
        s.bumperBottom = bumperBottom;
        s.trackHalf = trackHalf;
        s.tireHalf = tireHalf;
    }

    private static void glass(Spec s, float top, float topHalf, float bottom, float bottomHalf) {
        s.glassTop = top;
        s.glassTopHalf = topHalf;
        s.glassBottom = bottom;
        s.glassBottomHalf = bottomHalf;
    }

    // ------------------------------------------------------------------ contorno simétrico

    /**
     * Metade direita de um contorno, do meio de cima ao meio de baixo, em curvas cúbicas; {@link #build} espelha a
     * outra metade (sem Path.op, que só existe a partir do API 19).
     */
    private static final class Outline {
        private final float[] seg = new float[6 * 24];
        private int n;
        private final float sx;
        private final float sy;
        private float lx;
        private float ly;

        Outline(float x, float y) {
            sx = lx = x;
            sy = ly = y;
        }

        Outline line(float x, float y) {
            return cubic(lx + (x - lx) / 3f, ly + (y - ly) / 3f, lx + (x - lx) * 2f / 3f, ly + (y - ly) * 2f / 3f, x, y);
        }

        Outline quad(float cx, float cy, float x, float y) {
            return cubic(lx + (cx - lx) * 2f / 3f, ly + (cy - ly) * 2f / 3f,
                x + (cx - x) * 2f / 3f, y + (cy - y) * 2f / 3f, x, y);
        }

        Outline cubic(float c1x, float c1y, float c2x, float c2y, float x, float y) {
            int i = n * 6;
            seg[i] = c1x;
            seg[i + 1] = c1y;
            seg[i + 2] = c2x;
            seg[i + 3] = c2y;
            seg[i + 4] = x;
            seg[i + 5] = y;
            n++;
            lx = x;
            ly = y;
            return this;
        }

        Path build() {
            Path p = new Path();
            p.moveTo(sx, sy);
            for (int k = 0; k < n; k++) {
                int i = k * 6;
                p.cubicTo(seg[i], seg[i + 1], seg[i + 2], seg[i + 3], seg[i + 4], seg[i + 5]);
            }
            for (int k = n - 1; k >= 0; k--) {
                int i = k * 6;
                float px = k == 0 ? sx : seg[i - 2];
                float py = k == 0 ? sy : seg[i - 1];
                p.cubicTo(-seg[i + 2], seg[i + 3], -seg[i], seg[i + 1], -px, py);
            }
            p.close();
            return p;
        }
    }

    // ------------------------------------------------------------------ estado

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    final Spec s;
    final Model model;
    private final int paint;
    private final Path body;
    private final Path cab;
    private final Path glass;
    private final Path bumper;
    /** Lanterna da direita (a esquerda é espelhada). */
    final Path light;
    private float lightCx;
    private float lightCy;

    CarPainter(Model model, int color) {
        this.model = model;
        this.s = spec(model);
        this.paint = color;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        if (model == Model.PICKUP) {
            cab = new Outline(0, s.roofY)
                .line(s.roofHalf - s.roofR, s.roofY)
                .quad(s.roofHalf, s.roofY, s.roofHalf + 1.5f, s.roofY + s.roofR)
                .cubic(s.roofHalf + 4, s.roofY + 22, s.cabHalf - 3, s.bedY - 16, s.cabHalf, s.bedY + 2)
                .line(0, s.bedY + 2)
                .build();
            body = new Outline(0, s.bedY)
                .line(s.bodyHalf - 5, s.bedY)
                .quad(s.bodyHalf, s.bedY, s.bodyHalf, s.bedY + 5)
                .line(s.bodyHalf - 0.5f, s.bumperTop)
                .cubic(s.bodyHalf, s.bumperBottom - 10, s.bodyHalf - 3, s.bumperBottom, s.bodyHalf - 12, s.bumperBottom)
                .line(0, s.bumperBottom)
                .build();
        } else {
            cab = null;
            body = new Outline(0, s.roofY)
                .line(s.roofHalf - s.roofR, s.roofY)
                .quad(s.roofHalf, s.roofY, s.roofHalf + 1.5f, s.roofY + s.roofR)
                .cubic(s.roofHalf + 5, s.roofY + (s.beltY - s.roofY) * 0.45f, s.shoulderHalf - 6, s.beltY - 10,
                    s.shoulderHalf, s.beltY)
                .cubic(s.bodyHalf - 2, s.beltY + 4, s.bodyHalf + 0.5f, s.lightY - 8, s.bodyHalf, s.lightY)
                .cubic(s.bodyHalf - 0.5f, s.lightY + 14, s.bodyHalf - 1, s.bumperTop - 10, s.bodyHalf - 1.5f, s.bumperTop)
                .cubic(s.bodyHalf - 1, s.bumperBottom - 10, s.bodyHalf - 4, s.bumperBottom, s.bodyHalf - 12, s.bumperBottom)
                .line(0, s.bumperBottom)
                .build();
        }
        glass = glassPath(0);
        bumper = new Outline(0, s.bumperTop)
            .line(s.bodyHalf - 1.5f, s.bumperTop)
            .cubic(s.bodyHalf - 1, s.bumperBottom - 10, s.bodyHalf - 4, s.bumperBottom, s.bodyHalf - 12, s.bumperBottom)
            .line(0, s.bumperBottom)
            .build();
        light = lightPath();
    }

    private Path glassPath(float grow) {
        float t = s.glassTop - grow;
        float b = s.glassBottom + grow;
        float th = s.glassTopHalf + grow;
        float bh = s.glassBottomHalf + grow;
        return new Outline(0, t)
            .line(th - 8, t)
            .quad(th, t, th + 1, t + 7)
            .line(bh - 0.5f, b - 7)
            .quad(bh, b, bh - 9, b)
            .line(0, b)
            .build();
    }

    private Path lightPath() {
        Path p = new Path();
        float bh = s.bodyHalf;
        if (s.lights == LIGHT_TALL) {
            float top = s.bedY + 4;
            float bot = s.bumperTop - 3;
            r.set(bh - 15, top, bh - 1.5f, bot);
            p.addRoundRect(r, 3.5f, 3.5f, Path.Direction.CW);
            lightCx = bh - 8;
            lightCy = (top + bot) / 2f;
            return p;
        }
        if (s.lights == LIGHT_BAR) {
            float top = s.lightY - 9;
            float bot = s.lightY + 7;
            p.moveTo(bh - 50, top + 3);
            p.lineTo(bh - 14, top);
            p.cubicTo(bh - 4, top, bh - 0.5f, top + 4, bh - 1.2f, s.lightY);
            p.cubicTo(bh - 1.5f, bot - 2, bh - 4, bot, bh - 12, bot);
            p.lineTo(bh - 30, bot - 2);
            p.lineTo(bh - 50, top + 8);
            p.close();
            lightCx = bh - 16;
            lightCy = s.lightY - 1;
            return p;
        }
        float top = s.beltY + 3;
        float inner = bh - 35;
        p.moveTo(inner, top);
        p.lineTo(s.shoulderHalf + 1, top - 0.5f);
        p.cubicTo(bh - 1.5f, top + 4, bh - 1, s.lightY + 4, bh - 2.5f, s.lightY + 11);
        p.lineTo(bh - 25, s.lightY + 13);
        p.cubicTo(bh - 30, s.lightY + 8, bh - 34, top + 7, inner, top);
        p.close();
        lightCx = bh - 15;
        lightCy = (top + s.lightY + 12) / 2f;
        return p;
    }

    // ------------------------------------------------------------------ cores

    private static int shade(int c, float f) {
        return Color.argb(Color.alpha(c), clamp(Color.red(c) * f), clamp(Color.green(c) * f), clamp(Color.blue(c) * f));
    }

    private static int tint(int c, float f) {
        return Color.argb(Color.alpha(c), clamp(Color.red(c) + (255 - Color.red(c)) * f),
            clamp(Color.green(c) + (255 - Color.green(c)) * f), clamp(Color.blue(c) + (255 - Color.blue(c)) * f));
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private boolean darkPaint() {
        return Color.red(paint) + Color.green(paint) + Color.blue(paint) < 200;
    }

    // ------------------------------------------------------------------ camada fixa

    /** Tudo o que não se mexe (vai para o bitmap do {@link Car3DView}). */
    void drawStatic(Canvas c, String plate) {
        if (s.rails) drawRails(c);
        if (s.mast) drawMast(c);
        if (cab != null) {
            paintBody(c, cab, 0.9f);
            drawGlass(c);
            paintBody(c, body, 1f);
            drawBed(c);
        } else {
            paintBody(c, body, 1f);
            if (s.glassFrame) {
                fill.setShader(null);
                fill.setColor(0xFF0C0E11);
                c.drawPath(glassPath(3.2f), fill);
            }
            drawGlass(c);
            if (s.spoiler) drawSpoiler(c);
            if (model == Model.SEDAN) drawTrunkLip(c);
            if (model != Model.SEDAN && !s.glassFrame) drawGlassShadow(c);
        }
        drawShutLines(c);
        drawBumper(c);
        drawLightsOff(c);
        if (s.plateOnBumper) drawPlate(c, s.bumperTop + 5.5f, plate, true);
        else drawPlate(c, plateY(), plate, false);
        if (!s.plateOnBumper) drawGarnish(c);
        if (s.spare) drawSpare(c);
        else if (model != Model.PICKUP) drawEmblem(c, 0, plateY() - 23, 1f);
        if (model == Model.ECOSPORT) drawEmblem(c, -54, s.lightY + 22, 0.9f);
    }

    private float plateY() {
        return (s.lightY + s.bumperTop) / 2f - 8f;
    }

    /** Pintura: luz do céu no alto, a cor no meio, mais escura embaixo; bordas escurecem (curvatura). */
    private void paintBody(Canvas c, Path p, float light) {
        int base = shade(paint, light);
        float top = cab != null && p == body ? s.bedY : s.roofY;
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, top, 0, s.bumperBottom,
            new int[] {tint(base, darkPaint() ? 0.28f : 0.45f), tint(base, 0.12f), base, shade(base, 0.8f), shade(base, 0.55f)},
            new float[] {0f, 0.25f, 0.5f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(p, fill);
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(-s.bodyHalf, 0, s.bodyHalf, 0,
            new int[] {0x80000000, 0x22000000, 0x00000000, 0x00000000, 0x22000000, 0x80000000},
            new float[] {0f, 0.07f, 0.24f, 0.76f, 0.93f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(p, fill);
        fill.setShader(null);
        // brilho no vinco do ombro (curva do lado até a lanterna)
        if (cab == null) {
            line.setShader(null);
            line.setColor(darkPaint() ? 0x55FFFFFF : 0x99FFFFFF);
            line.setStrokeWidth(1.3f);
            Path h = new Path();
            for (int side = -1; side <= 1; side += 2) {
                h.reset();
                h.moveTo(side * (s.shoulderHalf - 14), s.beltY + 1.5f);
                h.cubicTo(side * (s.shoulderHalf - 2), s.beltY + 2, side * (s.bodyHalf - 4), s.lightY - 10,
                    side * (s.bodyHalf - 3.5f), s.lightY - 2);
                c.drawPath(h, line);
            }
        }
    }

    private void drawGlass(Canvas c) {
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, s.glassTop, 0, s.glassBottom,
            new int[] {0xFF465363, 0xFF1A2129, 0xFF0B0E12}, new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(glass, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(glass);
        // reflexo do céu em diagonal
        Path ref = new Path();
        ref.moveTo(-s.glassTopHalf * 0.15f, s.glassTop);
        ref.lineTo(s.glassTopHalf * 0.4f, s.glassTop);
        ref.lineTo(s.glassBottomHalf * 0.05f, s.glassBottom);
        ref.lineTo(-s.glassBottomHalf * 0.5f, s.glassBottom);
        ref.close();
        fill.setColor(0x1CFFFFFF);
        c.drawPath(ref, fill);
        ref.reset();
        ref.moveTo(s.glassTopHalf * 0.5f, s.glassTop);
        ref.lineTo(s.glassTopHalf * 0.62f, s.glassTop);
        ref.lineTo(s.glassBottomHalf * 0.3f, s.glassBottom);
        ref.lineTo(s.glassBottomHalf * 0.16f, s.glassBottom);
        ref.close();
        fill.setColor(0x12FFFFFF);
        c.drawPath(ref, fill);
        // sombra do teto no alto do vidro
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, s.glassTop, 0, s.glassTop + 6, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(-s.bodyHalf, s.glassTop, s.bodyHalf, s.glassTop + 6, fill);
        fill.setShader(null);
        // desembaçador
        line.setShader(null);
        line.setColor(0x16FFFFFF);
        line.setStrokeWidth(0.6f);
        float h = s.glassBottom - s.glassTop;
        for (int i = 1; i <= 6; i++) {
            float y = s.glassTop + h * (0.3f + i * 0.09f);
            float half = s.glassTopHalf + (s.glassBottomHalf - s.glassTopHalf) * ((y - s.glassTop) / h) - 9;
            c.drawLine(-half, y, half, y, line);
        }
        if (model == Model.PICKUP) {
            // janela de correr no meio
            line.setColor(0xFF07090B);
            line.setStrokeWidth(1.4f);
            c.drawLine(-s.glassTopHalf * 0.36f, s.glassTop, -s.glassBottomHalf * 0.36f, s.glassBottom, line);
            c.drawLine(s.glassTopHalf * 0.36f, s.glassTop, s.glassBottomHalf * 0.36f, s.glassBottom, line);
        }
        c.restore();
        if (s.wiper) {
            line.setColor(0xFF050607);
            line.setStrokeWidth(1.7f);
            c.drawLine(2, s.glassBottom - 4, -s.glassBottomHalf * 0.62f, s.glassBottom - 11, line);
            fill.setColor(0xFF111316);
            c.drawCircle(2, s.glassBottom - 3.5f, 2.6f, fill);
        }
    }

    private void drawGlassShadow(Canvas c) {
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, s.glassBottom, 0, s.glassBottom + 5, 0x55000000, 0x00000000,
            Shader.TileMode.CLAMP));
        c.drawRect(-s.glassBottomHalf, s.glassBottom, s.glassBottomHalf, s.glassBottom + 5, fill);
        fill.setShader(null);
    }

    /** Aerofólio sobre o vidro, da cor do carro, com a luz de freio alta (apagada; acesa em drawLights). */
    private void drawSpoiler(Canvas c) {
        float th = s.glassTopHalf;
        Path p = new Outline(0, s.roofY + 0.5f)
            .line(th + 1, s.roofY + 0.5f)
            .quad(th + 8, s.roofY + 1, th + 6, s.glassTop - 1)
            .quad(th + 1, s.glassTop - 4.5f, th - 6, s.glassTop - 4.5f)
            .line(0, s.glassTop - 4.5f)
            .build();
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, s.roofY, 0, s.glassTop - 3,
            new int[] {tint(paint, 0.35f), paint, shade(paint, 0.62f)}, null, Shader.TileMode.CLAMP));
        c.drawPath(p, fill);
        fill.setShader(null);
        r.set(-15, s.glassTop - 7.5f, 15, s.glassTop - 5);
        fill.setColor(0xFF5A0A0F);
        c.drawRoundRect(r, 1.2f, 1.2f, fill);
    }

    private void drawTrunkLip(Canvas c) {
        line.setShader(null);
        line.setColor(0x66FFFFFF);
        line.setStrokeWidth(1.2f);
        Path p = new Path();
        p.moveTo(-(s.shoulderHalf - 4), s.beltY + 3);
        p.quadTo(0, s.beltY + 0.5f, s.shoulderHalf - 4, s.beltY + 3);
        c.drawPath(p, line);
        line.setColor(0x33000000);
        p.offset(0, 1.6f);
        c.drawPath(p, line);
    }

    private void drawRails(Canvas c) {
        float y = s.roofY;
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, y - 9, 0, y + 1, 0xFF2A2E34, 0xFF08090B, Shader.TileMode.CLAMP));
        for (int side = -1; side <= 1; side += 2) {
            r.set(side > 0 ? s.roofHalf - 13 : -s.roofHalf + 6, y - 8, side > 0 ? s.roofHalf - 6 : -s.roofHalf + 13, y + 1);
            c.drawRoundRect(r, 2f, 2f, fill);
        }
        // barra transversal
        r.set(-s.roofHalf - 3, y - 9.5f, s.roofHalf + 3, y - 5.5f);
        c.drawRoundRect(r, 2f, 2f, fill);
        fill.setShader(null);
        line.setShader(null);
        line.setColor(0x55FFFFFF);
        line.setStrokeWidth(0.7f);
        c.drawLine(-s.roofHalf - 1, y - 9, s.roofHalf + 1, y - 9, line);
    }

    private void drawMast(Canvas c) {
        line.setShader(null);
        line.setColor(0xFF15171A);
        line.setStrokeWidth(1.1f);
        c.drawLine(0, s.roofY - 9, -1.5f, s.roofY - 30, line);
    }

    /** Picape: borda da caçamba, o vão escuro dela e a maçaneta da tampa. */
    private void drawBed(Canvas c) {
        float y = s.bedY;
        fill.setColor(0xFF0A0B0D);
        r.set(-(s.bodyHalf - 9), y - 0.5f, s.bodyHalf - 9, y + 3.5f);
        c.drawRoundRect(r, 1.5f, 1.5f, fill);
        line.setShader(null);
        line.setColor(darkPaint() ? 0x55FFFFFF : 0xAAFFFFFF);
        line.setStrokeWidth(1.1f);
        c.drawLine(-(s.bodyHalf - 4), y + 5, s.bodyHalf - 4, y + 5, line);
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, y + 9, 0, y + 16, 0xFF2B2F35, 0xFF0B0C0E, Shader.TileMode.CLAMP));
        r.set(-13, y + 9, 13, y + 16);
        c.drawRoundRect(r, 3f, 3f, fill);
        fill.setShader(null);
        // vinco horizontal no meio da tampa
        float k = (y + s.bumperTop) / 2f + 4;
        line.setShader(null);
        line.setStrokeWidth(1.2f);
        line.setColor(0x40000000);
        c.drawLine(-(s.bodyHalf - 20), k, s.bodyHalf - 20, k, line);
        line.setColor(darkPaint() ? 0x30FFFFFF : 0x80FFFFFF);
        c.drawLine(-(s.bodyHalf - 20), k + 1.3f, s.bodyHalf - 20, k + 1.3f, line);
    }

    /** Frestas da tampa (linha escura com a borda clara embaixo dá o relevo). */
    private void drawShutLines(Canvas c) {
        Path p = new Path();
        float bot = s.bumperTop - 2;
        if (model == Model.PICKUP) {
            float x = s.bodyHalf - 17;
            p.moveTo(-x, s.bedY + 6);
            p.lineTo(-x, bot);
            p.lineTo(x, bot);
            p.lineTo(x, s.bedY + 6);
        } else if (model == Model.SEDAN) {
            float x = s.bodyHalf - 31;
            p.moveTo(-x, bot);
            p.lineTo(-(x + 4), s.lightY - 6);
            p.quadTo(-(s.shoulderHalf - 6), s.beltY + 3, 0, s.beltY + 2.5f);
            p.quadTo(s.shoulderHalf - 6, s.beltY + 3, x + 4, s.lightY - 6);
            p.lineTo(x, bot);
            p.close();
        } else {
            float x = s.bodyHalf - 30;
            p.moveTo(-(s.shoulderHalf - 9), s.beltY - 1);
            p.lineTo(-(x - 2), s.lightY + 12);
            p.lineTo(-x, bot);
            p.lineTo(x, bot);
            p.lineTo(x - 2, s.lightY + 12);
            p.lineTo(s.shoulderHalf - 9, s.beltY - 1);
        }
        line.setShader(null);
        line.setStrokeWidth(0.9f);
        line.setColor(0x70000000);
        c.drawPath(p, line);
        p.offset(0, 0.9f);
        line.setColor(darkPaint() ? 0x22FFFFFF : 0x55FFFFFF);
        line.setStrokeWidth(0.6f);
        c.drawPath(p, line);
    }

    private void drawBumper(Canvas c) {
        if (model == Model.PICKUP) {
            fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, s.bumperTop, 0, s.bumperBottom,
                new int[] {0xFFF2F5F7, 0xFF9AA2AA, 0xFFD5DADF, 0xFF6E767E}, new float[] {0f, 0.35f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
            c.drawPath(bumper, fill);
            fill.setShader(null);
            // degraus pretos dos lados da placa
            fill.setColor(0xFF15171A);
            r.set(-62, s.bumperTop + 4, -38, s.bumperTop + 10);
            c.drawRoundRect(r, 2, 2, fill);
            r.set(38, s.bumperTop + 4, 62, s.bumperTop + 10);
            c.drawRoundRect(r, 2, 2, fill);
        } else if (s.darkBumper) {
            fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, s.bumperTop, 0, s.bumperBottom,
                new int[] {0xFF3A3F46, 0xFF22262B, 0xFF121417}, null, Shader.TileMode.CLAMP));
            c.drawPath(bumper, fill);
            fill.setShader(null);
            // textura fosca: linha de luz no alto
            line.setShader(null);
            line.setColor(0x40FFFFFF);
            line.setStrokeWidth(0.8f);
            c.drawLine(-(s.bodyHalf - 4), s.bumperTop + 1, s.bodyHalf - 4, s.bumperTop + 1, line);
        } else {
            // da cor do carro, com vinco claro e difusor preto embaixo
            line.setShader(null);
            line.setColor(darkPaint() ? 0x33FFFFFF : 0x77FFFFFF);
            line.setStrokeWidth(1.1f);
            c.drawLine(-(s.bodyHalf - 6), s.bumperTop + 3, s.bodyHalf - 6, s.bumperTop + 3, line);
            Path d = new Outline(0, s.bumperBottom - 13)
                .line(s.bodyHalf - 14, s.bumperBottom - 13)
                .quad(s.bodyHalf - 7, s.bumperBottom - 12, s.bodyHalf - 11, s.bumperBottom)
                .line(0, s.bumperBottom)
                .build();
            fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, s.bumperBottom - 13, 0, s.bumperBottom, 0xFF2A2E33, 0xFF0D0F11,
                Shader.TileMode.CLAMP));
            c.drawPath(d, fill);
            fill.setShader(null);
            // escapamento
            fill.setColor(0xFFB8BEC4);
            r.set(-62, s.bumperBottom - 7, -46, s.bumperBottom - 1.5f);
            c.drawOval(r, fill);
            fill.setColor(0xFF050506);
            r.inset(1.6f, 1.2f);
            c.drawOval(r, fill);
        }
        // refletores nas pontas
        fill.setColor(0xFFB3141E);
        float ry = s.darkBumper ? s.bumperTop + 9 : s.bumperBottom - 19;
        r.set(-(s.bodyHalf - 5), ry, -(s.bodyHalf - 17), ry + 4.5f);
        c.drawRoundRect(r, 1.5f, 1.5f, fill);
        r.set(s.bodyHalf - 17, ry, s.bodyHalf - 5, ry + 4.5f);
        c.drawRoundRect(r, 1.5f, 1.5f, fill);
    }

    /** Placa Mercosul num rebaixo. */
    private void drawPlate(Canvas c, float top, String plate, boolean onBumper) {
        float w = 32;
        float h = 21;
        r.set(-w - 3, top - 3, w + 3, top + h + 3);
        fill.setColor(onBumper ? 0xFF0E1012 : shade(paint, 0.72f));
        c.drawRoundRect(r, 3, 3, fill);
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, top - 3, 0, top + 3, 0x88000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 3, 3, fill);
        fill.setShader(null);
        r.set(-w, top, w, top + h);
        fill.setColor(0xFFF7F8F9);
        c.drawRoundRect(r, 2, 2, fill);
        fill.setColor(0xFF0B3D91);
        c.drawRect(-w, top, w, top + 5, fill);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(3.4f);
        c.drawText("BRASIL", 0, top + 3.9f, text);
        if (plate != null && !plate.isEmpty()) {
            text.setColor(0xFF111111);
            text.setTextSize(13f);
            text.setTextScaleX(0.92f);
            c.drawText(plate, 0, top + 18.2f, text);
            text.setTextScaleX(1f);
        }
        line.setShader(null);
        line.setColor(0xFF222222);
        line.setStrokeWidth(0.7f);
        c.drawRoundRect(r, 2, 2, line);
    }

    /** Friso cromado acima da placa. */
    private void drawGarnish(Canvas c) {
        float y = plateY() - 9;
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, y, 0, y + 4, new int[] {0xFFFFFFFF, 0xFF9CA4AC, 0xFFDDE2E6}, null,
            Shader.TileMode.CLAMP));
        r.set(-34, y, 34, y + 4);
        c.drawRoundRect(r, 2, 2, fill);
        fill.setShader(null);
    }

    /** Emblema oval genérico (sem marca). */
    private void drawEmblem(Canvas c, float x, float y, float k) {
        r.set(x - 9 * k, y - 4.5f * k, x + 9 * k, y + 4.5f * k);
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFF2D5FB5, 0xFF0B2A63, Shader.TileMode.CLAMP));
        c.drawOval(r, fill);
        fill.setShader(null);
        line.setShader(null);
        line.setColor(0xFFDCE2E8);
        line.setStrokeWidth(1f * k);
        c.drawOval(r, line);
    }

    /** Estepe na tampa: pneu, roda prata de 5 raios duplos (como a da foto), cubo e sombra na lataria. */
    private void drawSpare(Canvas c) {
        float cy = (s.beltY + s.bumperTop) / 2f + 3;
        float R = (s.bumperTop - s.beltY) / 2f + 5;
        float rim = R * 0.64f;
        // sombra na tampa
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new RadialGradient(4, cy + 5, R + 8, new int[] {0x88000000, 0x44000000, 0x00000000},
            new float[] {0.75f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(4, cy + 5, R + 8, fill);
        // pneu
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new RadialGradient(-R * 0.3f, cy - R * 0.35f, R * 1.4f,
            new int[] {0xFF3A3F46, 0xFF1D2025, 0xFF0C0D0F}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(0, cy, R, fill);
        fill.setShader(null);
        line.setShader(null);
        line.setColor(0x26FFFFFF);
        line.setStrokeWidth(1f);
        c.drawCircle(0, cy, R - 1.2f, line);
        line.setColor(0x1AFFFFFF);
        line.setStrokeWidth(2.5f);
        c.drawCircle(0, cy, (R + rim) / 2f + 1, line);
        // cavidade da roda
        fill.setColor(0xFF121417);
        c.drawCircle(0, cy, rim, fill);
        Shader silver = new LinearGradient(-rim, cy - rim, rim, cy + rim,
            new int[] {0xFFF4F6F8, 0xFFB4BBC2, 0xFFE2E6EA, 0xFF8E969E}, null, Shader.TileMode.CLAMP);
        // aro
        line.setColor(0xFFFFFFFF);
        line.setShader(silver);
        line.setStrokeWidth(3.2f);
        c.drawCircle(0, cy, rim - 1.6f, line);
        line.setShader(null);
        // raios duplos
        fill.setColor(0xFFFFFFFF);
        fill.setShader(silver);
        Path p = new Path();
        for (int i = 0; i < 5; i++) {
            c.save();
            c.rotate(i * 72f, 0, cy);
            for (int side = -1; side <= 1; side += 2) {
                p.reset();
                p.moveTo(side * 1.2f, cy - rim * 0.3f);
                p.lineTo(side * 4.2f, cy - rim * 0.3f);
                p.lineTo(side * 7.4f, cy - rim + 2.6f);
                p.lineTo(side * 2.2f, cy - rim + 2.6f);
                p.close();
                c.drawPath(p, fill);
            }
            c.restore();
        }
        // cubo
        c.drawCircle(0, cy, rim * 0.34f, fill);
        fill.setShader(null);
        line.setColor(0x66000000);
        line.setStrokeWidth(0.6f);
        c.drawCircle(0, cy, rim * 0.34f, line);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90);
            fill.setColor(0xFF2B2F34);
            c.drawCircle((float) Math.cos(a) * rim * 0.22f, cy + (float) Math.sin(a) * rim * 0.22f, 1.5f, fill);
        }
        drawEmblem(c, 0, cy, 0.45f);
    }

    /** Lanternas apagadas (lente vermelha escura, frisos e luz de ré). */
    private void drawLightsOff(Canvas c) {
        for (int side = -1; side <= 1; side += 2) {
            c.save();
            c.scale(side, 1);
            fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(lightCx - 20, lightCy - 10, lightCx + 14, lightCy + 10,
                new int[] {0xFF4A060B, 0xFF8E1018, 0xFF5C070D}, null, Shader.TileMode.CLAMP));
            c.drawPath(light, fill);
            fill.setShader(null);
            c.save();
            c.clipPath(light);
            // luz de ré (lente clara)
            fill.setColor(0xFFC9CED4);
            if (s.lights == LIGHT_TALL) {
                c.drawRect(s.bodyHalf - 16, lightCy + 2, s.bodyHalf, lightCy + 9, fill);
            } else if (s.lights == LIGHT_BAR) {
                c.drawRect(s.bodyHalf - 44, s.lightY + 1, s.bodyHalf - 32, s.lightY + 10, fill);
            } else {
                c.drawRect(s.bodyHalf - 30, s.lightY + 5, s.bodyHalf - 19, s.lightY + 14, fill);
            }
            fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, lightCy - 12, 0, lightCy + 12, 0x55FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP));
            c.drawPath(light, fill);
            fill.setShader(null);
            c.restore();
            line.setShader(null);
            line.setColor(0xAA14161A);
            line.setStrokeWidth(0.9f);
            c.drawPath(light, line);
            c.restore();
        }
        if (s.lights == LIGHT_BAR) {
            // faixa de luz entre as lanternas
            fill.setColor(0xFF4A060B);
            float x = s.bodyHalf - 48;
            r.set(-x, s.lightY - 4.5f, x, s.lightY - 2);
            c.drawRoundRect(r, 1.2f, 1.2f, fill);
        }
    }

    // ------------------------------------------------------------------ a cada quadro

    /** Vão escuro embaixo do carro e os pneus (a banda de rodagem corre com {@code phase}). */
    void drawTires(Canvas c, float phase) {
        float top = s.bumperBottom - 18;
        fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(0, s.bumperBottom - 8, 0, 96, 0xFF050607, 0xFF0E1013, Shader.TileMode.CLAMP));
        c.drawRect(-s.trackHalf, s.bumperBottom - 8, s.trackHalf, 95, fill);
        fill.setShader(null);
        for (int side = -1; side <= 1; side += 2) {
            float x = side * s.trackHalf;
            r.set(x - s.tireHalf, top, x + s.tireHalf, 100);
            fill.setColor(0xFFFFFFFF);
        fill.setShader(new LinearGradient(x - s.tireHalf, 0, x + s.tireHalf, 0,
                new int[] {0xFF08090A, 0xFF24282D, 0xFF30353B, 0xFF1C1F23, 0xFF08090A},
                new float[] {0f, 0.2f, 0.5f, 0.8f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, 7, 7, fill);
            fill.setShader(null);
            c.save();
            c.clipRect(r);
            fill.setColor(0xFF0A0B0D);
            float step = 7f;
            float off = phase * step;
            for (float y = top - step + off; y < 100; y += step) {
                c.drawRect(x - s.tireHalf + 3, y, x - 2, y + 2.4f, fill);
                c.drawRect(x + 2, y + step / 2f, x + s.tireHalf - 3, y + step / 2f + 2.4f, fill);
            }
            c.restore();
        }
    }

    /** Lanternas acesas (sempre um pouco, como à noite) e, freando, forte com brilho e a luz alta. */
    void drawLights(Canvas c, boolean braking) {
        int lens = braking ? 0xF2FF2E36 : 0x99E0202A;
        for (int side = -1; side <= 1; side += 2) {
            c.save();
            c.scale(side, 1);
            c.save();
            c.clipPath(light);
            fill.setColor(lens);
            c.drawPath(light, fill);
            // filete de LED
            line.setShader(null);
            line.setColor(braking ? 0xFFFFD0D0 : 0xFFFF6B70);
            line.setStrokeWidth(1.4f);
            if (s.lights == LIGHT_TALL) {
                c.drawLine(s.bodyHalf - 12, s.bedY + 8, s.bodyHalf - 12, lightCy - 2, line);
            } else {
                c.drawLine(lightCx - 14, lightCy - 3, lightCx + 10, lightCy - 5, line);
            }
            c.restore();
            fill.setColor(0xFFFFFFFF);
        fill.setShader(new RadialGradient(lightCx, lightCy, braking ? 34 : 20,
                braking ? 0x88FF2030 : 0x33FF2030, 0x00FF2030, Shader.TileMode.CLAMP));
            c.drawCircle(lightCx, lightCy, braking ? 34 : 20, fill);
            fill.setShader(null);
            c.restore();
        }
        if (s.lights == LIGHT_BAR) {
            fill.setColor(lens);
            float x = s.bodyHalf - 48;
            r.set(-x, s.lightY - 4.5f, x, s.lightY - 2);
            c.drawRoundRect(r, 1.2f, 1.2f, fill);
        }
        if (braking) {
            if (s.spoiler) {
                fill.setColor(0xFFFF3B45);
                r.set(-15, s.glassTop - 7.5f, 15, s.glassTop - 5);
                c.drawRoundRect(r, 1.2f, 1.2f, fill);
            } else if (model == Model.PICKUP || model == Model.SEDAN) {
                float y = model == Model.PICKUP ? s.roofY + 2 : s.glassTop + 1.5f;
                fill.setColor(0xFFFF3B45);
                r.set(-13, y, 13, y + 2.4f);
                c.drawRoundRect(r, 1.2f, 1.2f, fill);
            }
        }
    }

    /** Altura total (para encaixar na tela): do alto do rack/antena até o chão. */
    float topY() {
        return s.roofY - (s.mast ? 30 : s.rails ? 10 : 2);
    }
}
