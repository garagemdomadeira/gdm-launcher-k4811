package com.k4811.launcher.home;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.k4811.common.ui.Ui;
import com.k4811.launcher.R;

import java.io.File;
import java.util.List;

/**
 * Escolher o ícone de um app ({@link CustomIcons}): grade com os PNG da pasta GDM-icones, do pen drive e de
 * downloads; "Ícone original" desfaz. Tocar fora do cartão fecha.
 */
public final class IconPicker {

    private IconPicker() {
    }

    public static void show(Activity a, String pkg, String cls, CharSequence appLabel, Runnable onChanged) {
        Dialog dialog = new Dialog(a, R.style.OverlayDialog);
        FrameLayout root = new FrameLayout(a);
        root.setOnClickListener(v -> dialog.dismiss());

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 28), dp(a, 24), dp(a, 28), dp(a, 20));
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE_HIGH);
        bg.setCornerRadius(dp(a, 28));
        bg.setStroke(dp(a, 2), Ui.OUTLINE);
        card.setBackground(bg);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(a, 24, Ui.TEXT, true);
        title.setText(a.getString(R.string.home_icon_pick_title, appLabel));
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        int accent = Ui.ACCENT;
        if (CustomIcons.has(pkg, cls)) {
            TextView original = text(a, 18, accent, true);
            original.setText(R.string.home_icon_original_app);
            original.setPadding(dp(a, 16), dp(a, 8), 0, dp(a, 8));
            original.setOnClickListener(v -> {
                CustomIcons.clear(pkg, cls);
                dialog.dismiss();
                onChanged.run();
            });
            head.addView(original);
        }
        card.addView(head);

        List<File> files = CustomIcons.candidates(a);
        if (files.isEmpty()) {
            TextView empty = text(a, 18, Ui.TEXT_SOFT, false);
            empty.setText(a.getString(R.string.home_icon_pick_empty, CustomIcons.DIR));
            empty.setPadding(0, dp(a, 20), 0, dp(a, 12));
            card.addView(empty, new LinearLayout.LayoutParams(dp(a, 720), LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            GridView grid = new GridView(a);
            grid.setNumColumns(6);
            grid.setVerticalSpacing(dp(a, 12));
            grid.setHorizontalSpacing(dp(a, 12));
            grid.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return files.size();
                }

                @Override
                public Object getItem(int i) {
                    return files.get(i);
                }

                @Override
                public long getItemId(int i) {
                    return i;
                }

                @Override
                public View getView(int i, View convert, ViewGroup parent) {
                    LinearLayout cell = new LinearLayout(a);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    ImageView iv = new ImageView(a);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    Bitmap b = CustomIcons.load(files.get(i));
                    if (b != null) iv.setImageBitmap(b);
                    cell.addView(iv, new LinearLayout.LayoutParams(dp(a, 72), dp(a, 72)));
                    TextView name = text(a, 12, Ui.TEXT_MUTED, false);
                    name.setSingleLine(true);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                    name.setText(files.get(i).getName());
                    cell.addView(name, new LinearLayout.LayoutParams(dp(a, 100), LinearLayout.LayoutParams.WRAP_CONTENT));
                    return cell;
                }
            });
            grid.setOnItemClickListener((p, v, i, id) -> {
                // por tela: apps com várias telas na gaveta (Ferramentas da NWD) ganham ícones diferentes
                boolean ok = CustomIcons.set(CustomIcons.key(pkg, cls), files.get(i));
                dialog.dismiss();
                if (ok) onChanged.run();
                else Toast.makeText(a, R.string.home_icon_pick_failed, Toast.LENGTH_SHORT).show();
            });
            // altura das linhas que existem (até ~4 linhas; mais que isso rola)
            int rows = Math.min(4, (files.size() + 5) / 6);
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(a, 720), rows * dp(a, 112));
            gp.topMargin = dp(a, 16);
            card.addView(grid, gp);
        }

        root.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        Ui.applyFont(root);
        dialog.setContentView(root);
        dialog.show();
    }

    private static TextView text(Activity a, float size, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        t.setTextColor(color);
        t.setTypeface(bold ? Ui.semiBold(a) : Ui.regular(a));
        return t;
    }

    private static int dp(Activity a, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }
}
