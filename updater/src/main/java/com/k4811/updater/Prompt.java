package com.k4811.updater;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.ui.Ui;

import java.util.List;

/**
 * Aviso flutuante "Atualizações da GDM" por cima de qualquer tela (o mapa continua usável em volta): o que mudou e os
 * botões Agora não (pergunta de novo amanhã) e Confirmar update (abre a tela e instala os apps; a ROM pede outra
 * confirmação lá). Sem Activity: escala própria pelo desenho de 1280 de largura.
 */
final class Prompt {
    private static View shown;

    private Prompt() {
    }

    static void show(Context c, List<Ota.App> apps, Ota.Rom rom) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        dismiss(c);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float k = Math.max(dm.widthPixels, dm.heightPixels) / 1280f;

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = px(k, 24);
        card.setPadding(pad, px(k, 20), pad, px(k, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2151A21);
        bg.setCornerRadius(px(k, 26));
        bg.setStroke(px(k, 2), 0xFF2E3742);
        card.setBackground(bg);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.mipmap.ic_launcher);
        head.addView(icon, new LinearLayout.LayoutParams(px(k, 44), px(k, 44)));
        TextView title = text(c, k, 22, 0xFFFFFFFF);
        title.setTypeface(Ui.semiBold(c));
        title.setText(R.string.ota_prompt_title);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tp.leftMargin = px(k, 14);
        head.addView(title, tp);
        card.addView(head);

        StringBuilder lines = new StringBuilder();
        for (Ota.App a : apps) {
            if (lines.length() > 0) lines.append('\n');
            lines.append(c.getString(R.string.ota_app_line, a.name,
                a.installedName == null ? "?" : a.installedName, a.versionName));
        }
        if (rom != null) {
            if (lines.length() > 0) lines.append('\n');
            lines.append(c.getString(R.string.ota_rom_line, rom.version, (int) (rom.size >> 20)));
        }
        TextView body = text(c, k, 18, 0xFFC9D1DA);
        body.setText(lines);
        body.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = px(k, 12);
        card.addView(body, bp);

        LinearLayout actions = new LinearLayout(c);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        ap.topMargin = px(k, 16);
        card.addView(actions, ap);

        TextView later = text(c, k, 19, 0xFFC9D1DA);
        later.setText(R.string.ota_later);
        later.setPadding(px(k, 18), px(k, 12), px(k, 18), px(k, 12));
        later.setOnClickListener(v -> {
            Ota.prefs(c).edit().putLong(Ota.KEY_SNOOZE, System.currentTimeMillis() + Ota.DAY_MS - 60_000L).apply();
            dismiss(c);
        });
        actions.addView(later);

        TextView ok = text(c, k, 19, 0xFF0B0E13);
        ok.setTypeface(Ui.semiBold(c));
        ok.setText(R.string.ota_confirm);
        ok.setPadding(px(k, 24), px(k, 12), px(k, 24), px(k, 12));
        GradientDrawable okBg = new GradientDrawable();
        okBg.setColor(0xFFE9EBEE);
        okBg.setCornerRadius(px(k, 16));
        ok.setBackground(okBg);
        ok.setOnClickListener(v -> {
            dismiss(c);
            c.startActivity(new Intent(c, UpdatesActivity.class)
                .putExtra(UpdatesActivity.EXTRA_INSTALL, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        });
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        okp.leftMargin = px(k, 10);
        actions.addView(ok, okp);
        Ui.applyFont(card);

        @SuppressWarnings("deprecation")
        int type = android.os.Build.VERSION.SDK_INT >= 26 && c.getApplicationInfo().targetSdkVersion >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(px(k, 560),
            WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = px(k, 24);
        try {
            wm.addView(card, lp);
            shown = card;
        } catch (Exception e) {
            // sem permissão de janela por cima: abre a tela
            c.startActivity(new Intent(c, UpdatesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    static void dismiss(Context c) {
        if (shown == null) return;
        try {
            ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).removeView(shown);
        } catch (Exception ignored) {
        }
        shown = null;
    }

    private static TextView text(Context c, float k, float size, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k);
        t.setTextColor(color);
        t.setTypeface(Ui.medium(c));
        return t;
    }

    private static int px(float k, float v) {
        return Math.round(v * k);
    }
}
