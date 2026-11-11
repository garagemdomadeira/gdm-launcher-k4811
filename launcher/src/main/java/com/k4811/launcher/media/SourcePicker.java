package com.k4811.launcher.media;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.k4811.common.Apps;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;
import com.k4811.launcher.R;
import com.k4811.launcher.home.CustomIcons;
import com.k4811.launcher.home.NowPlaying;
import com.k4811.launcher.home.IconStyle;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Seletor da fonte do "tocando agora": Automático (segue o que está tocando), Rádio, MP3, música BT
 * e os apps de música instalados (Spotify, Deezer, YouTube Music…).
 */
public final class SourcePicker {

    public interface Listener {
        void onPicked(String key);
    }

    private static final String[] KNOWN_PLAYERS = {
        "com.spotify.music",
        "deezer.android.app",
        "com.google.android.apps.youtube.music",
    };

    private SourcePicker() {
    }

    public static Drawable icon(Context c, String key, String pkg) {
        if (key == null || NowPlaying.SRC_BT.equals(key)) {
            return fallbackIcon(c, R.drawable.ms_bluetooth);
        }
        if (NowPlaying.SRC_AUTO.equals(key)) {
            return fallbackIcon(c, R.drawable.ms_auto_awesome);
        }
        String targetPkg = NowPlaying.SRC_RADIO.equals(key) ? Apps.RADIO
            : (pkg != null ? pkg : key);
        int size = Math.round(Ui.dp(c, 40));
        Drawable custom = CustomIcons.get(c, targetPkg);
        if (custom != null) {
            if (IconStyle.shape(c) != IconStyle.ORIGINAL) {
                Bitmap b = IconStyle.render(c, custom, size, true);
                if (b != null) return new BitmapDrawable(c.getResources(), b);
            }
            return custom;
        }
        try {
            Drawable appIcon = c.getPackageManager().getApplicationIcon(targetPkg);
            if (IconStyle.shape(c) != IconStyle.ORIGINAL) {
                Bitmap b = IconStyle.render(c, appIcon, size, false);
                if (b != null) return new BitmapDrawable(c.getResources(), b);
            }
            return appIcon;
        } catch (Exception ignored) {
        }
        return fallbackIcon(c, R.drawable.ms_play_circle);
    }

    private static Drawable fallbackIcon(Context c, int resId) {
        Drawable d = c.getResources().getDrawable(resId).mutate();
        if (Build.VERSION.SDK_INT >= 21) {
            d.setTint(Ui.ACCENT);
        }
        return d;
    }

    public static void show(Activity a, NowPlaying widget, String current, Listener listener) {
        Dialog dialog = new Dialog(a, R.style.OverlayDialog);
        FrameLayout root = new FrameLayout(a);
        root.setOnClickListener(v -> dialog.dismiss());

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 28), dp(a, 22), dp(a, 28), dp(a, 22));
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE_HIGH);
        bg.setCornerRadius(dp(a, 28));
        bg.setStroke(dp(a, 2), Ui.OUTLINE);
        card.setBackground(bg);

        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);

        List<String> keys = new ArrayList<>();
        keys.add(NowPlaying.SRC_AUTO);
        keys.add(NowPlaying.SRC_RADIO);
        keys.add(NowPlaying.SRC_MP3);
        keys.add(NowPlaying.SRC_BT);
        keys.addAll(musicPackages(a));

        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            boolean selected = key.equals(current);
            View r = row(a, widget, key, selected, () -> {
                dialog.dismiss();
                listener.onPicked(key);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(a, 440), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(a, 8);
            rows.addView(r, lp);
        }

        ScrollView scroll = new ScrollView(a);
        scroll.addView(rows);
        card.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cp.topMargin = dp(a, 32);
        cp.bottomMargin = dp(a, 32);
        root.addView(card, cp);

        Ui.applyFont(root);
        Touch.install(root);
        dialog.setContentView(root);
        dialog.show();
    }

    private static View row(Activity a, NowPlaying widget, String key, boolean selected, Runnable onTap) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(a, 18));
        bg.setColor(selected ? (Ui.ACCENT & 0x26FFFFFF) : 0x12FFFFFF);
        bg.setStroke(dp(a, selected ? 2 : 1), selected ? Ui.ACCENT : Ui.OUTLINE);
        r.setBackground(bg);

        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(icon(a, key, widget.packageOf(key)));
        int ic = dp(a, 38);
        r.addView(iv, new LinearLayout.LayoutParams(ic, ic));

        TextView name = new TextView(a);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        name.setTextColor(selected ? Ui.ACCENT : Ui.TEXT);
        name.setTypeface(selected ? Ui.semiBold(a) : Ui.medium(a));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(widget.sourceLabel(key));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        np.leftMargin = dp(a, 14);
        r.addView(name, np);

        if (selected) {
            TextView check = new TextView(a);
            check.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            check.setTextColor(Ui.ACCENT);
            check.setTypeface(Ui.semiBold(a));
            check.setText("✓");
            r.addView(check);
        }
        r.setOnClickListener(v -> onTap.run());
        return r;
    }

    private static List<String> musicPackages(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> skip = new HashSet<>();
        skip.add(c.getPackageName());
        skip.add(Apps.RADIO);
        skip.add(Apps.MUSIC);
        skip.add(Apps.BT_MUSIC);
        skip.add(Apps.EQ);
        skip.add(Apps.PHONE);
        skip.add(Apps.SETTINGS);
        skip.add(Apps.FAN);

        List<String> out = new ArrayList<>();
        for (String known : KNOWN_PLAYERS) {
            try {
                pm.getApplicationInfo(known, 0);
                out.add(known);
                skip.add(known);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        List<String> extras = new ArrayList<>();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        if (found != null) {
            for (ResolveInfo info : found) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg == null || skip.contains(pkg)) continue;
                ApplicationInfo app = info.activityInfo.applicationInfo;
                boolean isMusic = false;
                if (Build.VERSION.SDK_INT >= 26 && app != null && app.category == ApplicationInfo.CATEGORY_AUDIO) {
                    isMusic = true;
                } else {
                    String p = pkg.toLowerCase(Locale.US);
                    if (p.contains("spotify") || p.contains("deezer") || p.contains("music")
                        || p.contains("podcast") || p.contains("tidal") || p.contains("soundcloud")) {
                        isMusic = true;
                    }
                }
                if (isMusic) {
                    skip.add(pkg);
                    extras.add(pkg);
                }
            }
        }
        final Collator order = Collator.getInstance(Locale.getDefault());
        Collections.sort(extras, (a, b) -> order.compare(labelOf(pm, a), labelOf(pm, b)));
        out.addAll(extras);
        return out;
    }

    private static String labelOf(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
            c.getResources().getDisplayMetrics()));
    }
}
