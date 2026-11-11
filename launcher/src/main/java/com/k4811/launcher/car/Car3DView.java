package com.k4811.launcher.car;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;
import com.k4811.launcher.R;

/**
 * Carro 3D animado no meio da tela clássica: a traseira do carro escolhido ({@link CarPainter}: EcoSport, SUV, hatch,
 * sedã ou picape, na cor escolhida, com a placa Mercosul da pessoa) sobre uma pista em perspectiva (estilo painel ADAS)
 * que corre na velocidade real (GPS/OBD2). Os pneus giram, o carro balança de leve andando e as lanternas acendem
 * forte ao frear.
 *
 * <p>A carroceria é desenhada uma vez num bitmap (troca só com modelo, cor, placa ou tamanho); a cada quadro só vão a
 * pista, a sombra, os pneus e as luzes.
 */
public final class Car3DView extends View {
    public static final String PREF_PLATE = "car_plate";
    public static final String PREF_USE_3D = "car_use_3d";
    public static final String PREF_MODEL = "car_model";
    public static final String PREF_COLOR = "car_color";
    /** Sem placa escolhida, a placa fica só com "BRASIL" (nenhuma placa de verdade no código). */
    private static final String DEFAULT_PLATE = "";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private float speedKmh = 0f;
    private float targetSpeedKmh = 0f;
    private int rpm = 0;
    private int gear = 0;
    private long brakeUntilMs = 0L;

    private float roadPhase = 0f;
    private float wheelPhase = 0f;
    private long lastFrameMs = 0L;
    private boolean running = false;

    private String plateText = DEFAULT_PLATE;
    private CarPainter.Model model = CarPainter.Model.ECOSPORT;
    private int color = CarPainter.COLORS[0];
    private CarPainter car;
    private Bitmap cache;
    private float scale;
    private float cx;
    private float cy;

    public Car3DView(Context context) {
        this(context, null);
    }

    public Car3DView(Context context, AttributeSet attrs) {
        super(context, attrs);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        loadPlate(context);
    }

    // ------------------------------------------------------------------ preferências

    /** Lê placa, modelo e cor guardados e redesenha. */
    public void loadPlate(Context c) {
        setCar(model(c), colorIndex(c), getPlate(c));
    }

    /** Usado pela prévia do menu (sem gravar). */
    void setCar(CarPainter.Model m, int colorIndex, String plate) {
        int col = CarPainter.COLORS[Math.max(0, Math.min(CarPainter.COLORS.length - 1, colorIndex))];
        String p = plate == null ? "" : plate.trim().toUpperCase();
        if (car != null && m == model && col == color && p.equals(plateText)) return;
        model = m;
        color = col;
        plateText = p;
        car = new CarPainter(m, col);
        dropCache();
        invalidate();
    }

    public static String getPlate(Context c) {
        String p = prefs(c).getString(PREF_PLATE, DEFAULT_PLATE);
        return p == null ? DEFAULT_PLATE : p.trim().toUpperCase();
    }

    public static void setPlate(Context c, String plate) {
        String clean = plate == null ? "" : plate.trim().toUpperCase();
        prefs(c).edit().putString(PREF_PLATE, clean).apply();
    }

    static CarPainter.Model model(Context c) {
        try {
            return CarPainter.Model.valueOf(prefs(c).getString(PREF_MODEL, CarPainter.Model.ECOSPORT.name()));
        } catch (Exception e) {
            return CarPainter.Model.ECOSPORT;
        }
    }

    static int colorIndex(Context c) {
        int i = prefs(c).getInt(PREF_COLOR, 0);
        return i >= 0 && i < CarPainter.COLORS.length ? i : 0;
    }

    /**
     * O carro 3D só aparece para quem escolheu ele; o padrão é a foto (ou o convite para escolher), que serve para
     * qualquer carro. Padrão: o 3D, a não ser que já haja uma foto escolhida.
     */
    public static boolean use3D(Context c) {
        SharedPreferences sp = prefs(c);
        return sp.getBoolean(PREF_USE_3D, CarPhoto.file(c) == null);
    }

    public static void setUse3D(Context c, boolean on) {
        prefs(c).edit().putBoolean(PREF_USE_3D, on).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("gdm_home", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ animação

    public void setTelemetry(float kmh, int engineRpm, int currentGear) {
        float clean = Float.isNaN(kmh) || kmh < 0f ? 0f : kmh;
        if (clean < targetSpeedKmh - 1.5f) brakeUntilMs = SystemClock.elapsedRealtime() + 1400L;
        targetSpeedKmh = clean;
        rpm = Math.max(0, engineRpm);
        gear = currentGear;
        if (running) postInvalidateOnAnimation();
    }

    public void start() {
        if (running) return;
        running = true;
        lastFrameMs = SystemClock.elapsedRealtime();
        postInvalidateOnAnimation();
    }

    public void stop() {
        running = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        dropCache();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        dropCache();
    }

    private void dropCache() {
        if (cache != null) cache.recycle();
        cache = null;
    }

    /** Escala e centro: o carro (do teto ao chão, com a pista) cabe na view. */
    private void layoutScale(int vw, int vh) {
        float top = car.topY();
        float h = 112f - top;
        // pela largura do carro (a pista pode sair pelos lados)
        scale = Math.min(vw / (car.s.bodyHalf * 2f + 36f), vh / h) * 0.96f;
        cx = vw * 0.5f;
        cy = -top * scale + (vh - h * scale) / 2f;
    }

    private void buildCache(int vw, int vh) {
        try {
            cache = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            cache = null;
            return;
        }
        Canvas bc = new Canvas(cache);
        bc.translate(cx, cy);
        bc.scale(scale, scale);
        car.drawStatic(bc, plateText);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0 || car == null) return;
        layoutScale(vw, vh);
        if (cache == null || cache.getWidth() != vw || cache.getHeight() != vh) buildCache(vw, vh);

        long now = SystemClock.elapsedRealtime();
        float dt = lastFrameMs > 0 ? Math.min(0.05f, (now - lastFrameMs) / 1000f) : 0.016f;
        lastFrameMs = now;
        speedKmh += (targetSpeedKmh - speedKmh) * Math.min(1f, dt * 6f);
        if (speedKmh < 0.2f && targetSpeedKmh == 0f) speedKmh = 0f;
        // parado com o motor ligado ou freando: luz de freio acesa
        boolean braking = now < brakeUntilMs || (speedKmh < 1.2f && (rpm > 400 || gear >= 0));
        roadPhase = (roadPhase + dt * (speedKmh * 0.045f)) % 1f;
        wheelPhase = (wheelPhase + dt * (speedKmh * 0.25f)) % 1f;
        float speedNorm = Math.min(1.6f, speedKmh / 90f);
        float sway = speedKmh > 3f ? (float) Math.sin(now / 1000f * 0.65f) * Math.min(2.5f, speedNorm * 2f) : 0f;

        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        drawRoad(c, sway * 0.35f);
        drawShadow(c, sway, braking);
        c.translate(sway, 0f);
        car.drawTires(c, wheelPhase);
        c.restore();

        if (cache != null) c.drawBitmap(cache, sway * scale, 0f, bitmapPaint);
        else {
            c.save();
            c.translate(cx + sway * scale, cy);
            c.scale(scale, scale);
            car.drawStatic(c, plateText);
            c.restore();
        }

        c.save();
        c.translate(cx + sway * scale, cy);
        c.scale(scale, scale);
        car.drawLights(c, braking);
        c.restore();

        // Só continua animando enquanto há movimento (ou a luz de freio ainda vai apagar). Parado, o carro fica
        // estático e NÃO redesenha — antes ele re-invalidava a 60fps à toa, segurando ~45% de CPU na tela inicial.
        boolean animating = speedKmh > 0.01f || Math.abs(targetSpeedKmh - speedKmh) > 0.01f || now < brakeUntilMs;
        if (running && animating) postInvalidateOnAnimation();
    }

    private void drawRoad(Canvas c, float camShiftX) {
        float horizonY = -12f;
        float bottomY = 112f;
        paint.setShader(new LinearGradient(0, horizonY, 0, bottomY,
            new int[] {0x000D1520, 0x88101C2B, 0xCC0B131E, 0x000B131E},
            new float[] {0f, 0.25f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(-48f - camShiftX * 0.3f, horizonY);
        path.lineTo(48f - camShiftX * 0.3f, horizonY);
        path.lineTo(155f - camShiftX, bottomY);
        path.lineTo(-155f - camShiftX, bottomY);
        path.close();
        c.drawPath(path, paint);
        paint.setShader(null);

        // linhas de velocidade em perspectiva
        for (int i = 0; i < 6; i++) {
            float p = (i + roadPhase) / 6f;
            float persp = p * p;
            float y = horizonY + (bottomY - horizonY) * persp;
            float halfW = 46f + (150f - 46f) * persp;
            float shift = -camShiftX * (0.3f + 0.7f * persp);
            int alpha = (int) (Math.sin(p * Math.PI) * (speedKmh > 0.5f ? 75 : 32));
            strokePaint.setShader(null);
            strokePaint.setColor((alpha << 24) | 0x4FC3F7);
            strokePaint.setStrokeWidth(0.8f + 1.4f * persp);
            c.drawLine(-halfW + shift, y, halfW + shift, y, strokePaint);
        }

        // faixas laterais e tracejado central
        strokePaint.setStrokeWidth(2.6f);
        strokePaint.setColor(0xFFFFFFFF);
        strokePaint.setShader(new LinearGradient(0, horizonY, 0, bottomY,
            new int[] {0x0038B6FF, 0xCC38B6FF, 0xFF38B6FF, 0x0038B6FF},
            new float[] {0f, 0.3f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawLine(-45f - camShiftX * 0.3f, horizonY, -148f - camShiftX, bottomY, strokePaint);
        c.drawLine(45f - camShiftX * 0.3f, horizonY, 148f - camShiftX, bottomY, strokePaint);
        for (int i = 0; i < 4; i++) {
            float p0 = (i + roadPhase) / 4f;
            float p1 = Math.min(1f, p0 + 0.13f);
            float y0 = horizonY + (bottomY - horizonY) * (p0 * p0);
            float y1 = horizonY + (bottomY - horizonY) * (p1 * p1);
            strokePaint.setStrokeWidth(1.5f + 2f * p0);
            c.drawLine(-18f - 42f * p0 * p0 - camShiftX * p0, y0, -18f - 42f * p1 * p1 - camShiftX * p1, y1, strokePaint);
            c.drawLine(18f + 42f * p0 * p0 - camShiftX * p0, y0, 18f + 42f * p1 * p1 - camShiftX * p1, y1, strokePaint);
        }
        strokePaint.setShader(null);
    }

    private void drawShadow(Canvas c, float sway, boolean braking) {
        float w = car.s.bodyHalf + 14f;
        float sy = 98f;
        paint.setShader(new RadialGradient(sway, sy, w,
            new int[] {0xEE000000, 0xBB000000, 0x44000000, 0x00000000},
            new float[] {0f, 0.55f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        rect.set(sway - w, sy - 16f, sway + w, sy + 14f);
        c.drawOval(rect, paint);
        // reflexo vermelho das lanternas no chão
        int a = braking ? 120 : 45;
        for (int side = -1; side <= 1; side += 2) {
            float x = sway + side * (car.s.bodyHalf - 28f);
            paint.setShader(new RadialGradient(x, sy + 6f, 42f, Color.argb(a, 255, 30, 45), 0x00FF1E2D,
                Shader.TileMode.CLAMP));
            rect.set(x - 42f, sy - 8f, x + 42f, sy + 18f);
            c.drawOval(rect, paint);
        }
        paint.setShader(null);
    }

    // ------------------------------------------------------------------ menu

    /**
     * Menu do carro: prévia ao vivo, modelo, cor e placa; "Usar uma foto" troca para a foto (CarPhoto) e "Usar o
     * carro 3D" grava a escolha.
     */
    public static void showConfigDialog(Activity a, Runnable onUpdated, Runnable onPickPhoto) {
        Dialog dialog = new Dialog(a, R.style.OverlayDialog);
        FrameLayout root = new FrameLayout(a);
        root.setOnClickListener(v -> dialog.dismiss());

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(dp(a, 28), dp(a, 20), dp(a, 28), dp(a, 20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE_HIGH);
        bg.setCornerRadius(dp(a, 28));
        bg.setStroke(dp(a, 2), Ui.OUTLINE);
        card.setBackground(bg);

        TextView title = new TextView(a);
        title.setText(R.string.home_car3d_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTextColor(Ui.TEXT);
        title.setTypeface(Ui.semiBold(a));
        card.addView(title);

        // escolha atual (só grava em "Usar o carro 3D")
        final CarPainter.Model[] pickModel = {model(a)};
        final int[] pickColor = {colorIndex(a)};

        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(a, 12);
        card.addView(body, bp);

        Car3DView preview = new Car3DView(a);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xFF06080B);
        pbg.setCornerRadius(dp(a, 18));
        preview.setBackground(pbg);
        body.addView(preview, new LinearLayout.LayoutParams(dp(a, 360), dp(a, 300)));

        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(a, 420), ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(a, 22);
        body.addView(side, sp);

        EditText input = new EditText(a);
        Runnable refresh = () -> preview.setCar(pickModel[0], pickColor[0], input.getText().toString());

        side.addView(label(a, R.string.home_car3d_model));
        LinearLayout models = new LinearLayout(a);
        models.setOrientation(LinearLayout.VERTICAL);
        side.addView(models);
        int[] names = {R.string.home_car_model_ecosport, R.string.home_car_model_suv, R.string.home_car_model_hatch,
            R.string.home_car_model_sedan, R.string.home_car_model_pickup};
        TextView[] chips = new TextView[names.length];
        LinearLayout rowView = null;
        for (int i = 0; i < names.length; i++) {
            if (i % 3 == 0) {
                rowView = new LinearLayout(a);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = dp(a, 8);
                models.addView(rowView, rp);
            }
            final CarPainter.Model m = CarPainter.Model.values()[i];
            TextView chip = new TextView(a);
            chip.setText(names[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 10));
            chips[i] = chip;
            chip.setOnClickListener(v -> {
                pickModel[0] = m;
                markChips(a, chips, m.ordinal());
                refresh.run();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(a, 128), ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(a, 8);
            rowView.addView(chip, cp);
        }
        markChips(a, chips, pickModel[0].ordinal());

        TextView colorLabel = label(a, R.string.home_car3d_color);
        side.addView(colorLabel);
        LinearLayout colors = new LinearLayout(a);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(a, 8);
        side.addView(colors, clp);
        View[] dots = new View[CarPainter.COLORS.length];
        for (int i = 0; i < dots.length; i++) {
            final int idx = i;
            View dot = new View(a);
            dots[i] = dot;
            dot.setOnClickListener(v -> {
                pickColor[0] = idx;
                markDots(a, dots, idx);
                refresh.run();
            });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(a, 50), dp(a, 50));
            dlp.rightMargin = dp(a, 14);
            colors.addView(dot, dlp);
        }
        markDots(a, dots, pickColor[0]);

        side.addView(label(a, R.string.home_car3d_sub));
        input.setText(getPlate(a));
        input.setSelection(input.getText().length());
        input.setHint("ABC1D23");
        input.setHintTextColor(Ui.TEXT_MUTED);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[] {new InputFilter.AllCaps(), new InputFilter.LengthFilter(8)});
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        input.setTextColor(Ui.TEXT);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 10));
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(0xFF11161D);
        inBg.setCornerRadius(dp(a, 14));
        inBg.setStroke(dp(a, 1.5f), Ui.ACCENT);
        input.setBackground(inBg);
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                refresh.run();
            }
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(a, 300), ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(a, 8);
        side.addView(input, ip);

        LinearLayout actions = new LinearLayout(a);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(a, 16);

        TextView btnPhoto = new TextView(a);
        btnPhoto.setText(R.string.home_car3d_photo);
        btnPhoto.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        btnPhoto.setTextColor(Ui.TEXT_SOFT);
        btnPhoto.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        btnPhoto.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPickPhoto != null) onPickPhoto.run();
        });
        actions.addView(btnPhoto);

        TextView btnSave = new TextView(a);
        btnSave.setText(R.string.home_car3d_save);
        btnSave.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        btnSave.setTextColor(0xFF0B1117);
        btnSave.setTypeface(Ui.semiBold(a));
        btnSave.setPadding(dp(a, 22), dp(a, 12), dp(a, 22), dp(a, 12));
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(Ui.ACCENT);
        saveBg.setCornerRadius(dp(a, 14));
        btnSave.setBackground(saveBg);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        svp.leftMargin = dp(a, 12);
        btnSave.setOnClickListener(v -> {
            setPlate(a, input.getText().toString());
            prefs(a).edit().putString(PREF_MODEL, pickModel[0].name()).putInt(PREF_COLOR, pickColor[0]).apply();
            setUse3D(a, true);
            dialog.dismiss();
            if (onUpdated != null) onUpdated.run();
        });
        actions.addView(btnSave, svp);
        card.addView(actions, ap);

        root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        Ui.applyFont(root);
        Touch.install(root);
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> preview.stop());
        // teclado só quando tocar na placa
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        refresh.run();
        preview.start();
        dialog.show();
    }

    private static TextView label(Activity a, int res) {
        TextView t = new TextView(a);
        t.setText(res);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        t.setTextColor(Ui.TEXT_SOFT);
        t.setPadding(0, dp(a, 14), 0, 0);
        return t;
    }

    private static void markChips(Activity a, TextView[] chips, int on) {
        int accent = Ui.ACCENT;
        for (int i = 0; i < chips.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(a, 22));
            g.setColor(i == on ? accent : 0xFF1A2029);
            chips[i].setBackground(g);
            chips[i].setTextColor(i == on ? 0xFF0B1117 : Ui.TEXT);
        }
    }

    private static void markDots(Activity a, View[] dots, int on) {
        int accent = Ui.ACCENT;
        for (int i = 0; i < dots.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(CarPainter.COLORS[i]);
            g.setStroke(dp(a, i == on ? 4 : 1.5f), i == on ? accent : 0x66FFFFFF);
            dots[i].setBackground(g);
        }
    }

    private static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
