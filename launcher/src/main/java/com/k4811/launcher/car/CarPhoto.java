package com.k4811.launcher.car;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
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

import com.k4811.common.system.Storages;
import com.k4811.common.ui.Ui;
import com.k4811.launcher.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Foto do carro no meio da tela clássica: a pessoa escolhe uma imagem (PNG, JPG ou WebP) das pastas Pictures, DCIM,
 * Download, {@code K4811/fotos} ou da raiz de um pen drive. Guarda o caminho ({@code gdm_home}, {@code car_photo}).
 * Sem fundo transparente a foto fica no retângulo dela; com, fica só o carro.
 */
public final class CarPhoto {
    private static final String KEY = "car_photo";
    private static final int MAX = 80;

    private CarPhoto() {
    }

    /** A foto escolhida, ou null. */
    public static File file(Context c) {
        String p = c.getSharedPreferences("gdm_home", Context.MODE_PRIVATE).getString(KEY, "");
        if (p.isEmpty()) return null;
        File f = new File(p);
        return f.isFile() ? f : null;
    }

    private static void set(Context c, String path) {
        c.getSharedPreferences("gdm_home", Context.MODE_PRIVATE).edit().putString(KEY, path).apply();
    }

    private static List<File> candidates(Context c) {
        File sd = Environment.getExternalStorageDirectory();
        List<File> dirs = new ArrayList<>();
        dirs.add(new File(sd, "K4811/fotos"));
        dirs.add(new File(sd, Environment.DIRECTORY_PICTURES));
        dirs.add(new File(sd, Environment.DIRECTORY_DCIM + "/Camera"));
        dirs.add(new File(sd, Environment.DIRECTORY_DOWNLOADS));
        for (File r : Storages.removable(c)) {
            dirs.add(r);
            dirs.add(new File(r, "fotos"));
        }
        List<File> out = new ArrayList<>();
        for (File d : dirs) {
            File[] list = d.listFiles();
            if (list == null) continue;
            for (File f : list) {
                String n = f.getName().toLowerCase(Locale.US);
                if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) {
                    out.add(f);
                }
            }
        }
        // as mais novas primeiro
        Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out.size() > MAX ? out.subList(0, MAX) : out;
    }

    /** Grade com as fotos encontradas; "Sem foto" tira. {@code after} roda depois de escolher. */
    public static void pick(Activity a, Runnable after) {
        Dialog dialog = new Dialog(a, R.style.OverlayDialog);
        FrameLayout root = new FrameLayout(a);
        root.setOnClickListener(v -> dialog.dismiss());
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(dp(a, 28), dp(a, 24), dp(a, 28), dp(a, 20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE_HIGH);
        bg.setCornerRadius(dp(a, 28));
        bg.setStroke(dp(a, 2), Ui.OUTLINE);
        card.setBackground(bg);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(a, 24, Ui.TEXT);
        title.setTypeface(Ui.semiBold(a));
        title.setText(R.string.home_car_photo_title);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView none = text(a, 18, Ui.ACCENT);
        none.setText(R.string.home_car_photo_none);
        none.setPadding(dp(a, 16), dp(a, 8), 0, dp(a, 8));
        none.setOnClickListener(v -> {
            set(a, "");
            dialog.dismiss();
            after.run();
        });
        head.addView(none);
        card.addView(head);

        List<File> files = candidates(a);
        if (files.isEmpty()) {
            TextView empty = text(a, 18, Ui.TEXT_SOFT);
            empty.setText(R.string.home_car_photo_empty);
            empty.setPadding(0, dp(a, 20), 0, dp(a, 12));
            card.addView(empty, new LinearLayout.LayoutParams(dp(a, 720), ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            int thumbW = dp(a, 160);
            int thumbH = dp(a, 100);
            GridView grid = new GridView(a);
            grid.setNumColumns(4);
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
                    ImageView iv = convert instanceof ImageView ? (ImageView) convert : new ImageView(a);
                    iv.setLayoutParams(new GridView.LayoutParams(thumbW, thumbH));
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(thumb(files.get(i), thumbW));
                    return iv;
                }
            });
            grid.setOnItemClickListener((p, v, i, id) -> {
                set(a, files.get(i).getAbsolutePath());
                dialog.dismiss();
                after.run();
            });
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(a, 720), dp(a, 440));
            gp.topMargin = dp(a, 16);
            card.addView(grid, gp);
        }
        root.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        Ui.applyFont(root);
        dialog.setContentView(root);
        dialog.show();
    }

    private static Bitmap thumb(File f, int w) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    private static TextView text(Activity a, float size, int color) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        t.setTextColor(color);
        t.setTypeface(Ui.medium(a));
        return t;
    }

    private static int dp(Activity a, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }
}
