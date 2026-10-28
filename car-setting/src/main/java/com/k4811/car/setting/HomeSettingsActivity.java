package com.k4811.car.setting;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.k4811.common.home.HomeGrid;
import com.k4811.common.home.HomeImage;
import com.k4811.common.config.LauncherConfig;
import com.k4811.car.setting.ui.HomeMapView;
import com.k4811.common.ui.IconView;
import com.k4811.car.setting.ui.OptionList;
import com.k4811.common.ui.ThemeDrawables;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen modules (Settings › Customize › Customize home screen): a miniature of the home screen
 * showing the grid and what each slot holds, and below it one row per module; its button opens
 * the same map, where tapping a slot places the module (or "Don't show" hides it). Each slot
 * takes one module, so the columns hold at most 1, 2 and 3; picking a taken slot swaps the two
 * modules. The Image module's row also picks its picture among the images in K4811/. No dock.
 */
public class HomeSettingsActivity extends Activity {

    private static final int THUMB_COLUMNS = 4;
    private static final int THUMB_WIDTH = 200;
    private static final int THUMB_HEIGHT = 130;

    private OptionList list;
    private HomeMapView map;
    private OptionList.Row imageRow;
    private OptionList.Row bgRow;
    private final Map<HomeGrid.Module, TextView> slotButtons = new EnumMap<>(HomeGrid.Module.class);
    private Dialog dialog;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.applyFont(findViewById(R.id.settings_root));
        TextView title = (TextView) findViewById(R.id.settings_title);
        title.setText(R.string.settings_home_title);
        title.setTypeface(Ui.semiBold(this));
        IconView back = (IconView) findViewById(R.id.settings_back);
        back.set(IconView.Icon.CHEVRON_LEFT, IconView.Style.NONE);
        back.setOnClickListener(v -> finish());

        LinearLayout content = (LinearLayout) findViewById(R.id.settings_content);
        map = new HomeMapView(this);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mapLp.topMargin = dp(16);
        content.addView(map, mapLp);

        list = new OptionList(this, content);
        build();
        list.setAfterRender(() -> {
            map.invalidate();
            Map<HomeGrid.Module, HomeGrid.Slot> layout = LauncherConfig.homeLayout(this);
            for (Map.Entry<HomeGrid.Module, TextView> e : slotButtons.entrySet()) {
                HomeGrid.Slot slot = layout.get(e.getKey());
                e.getValue().setText(slot == null ? getString(R.string.settings_home_hidden) : slot.label);
            }
            File image = HomeImage.file(this);
            // Subtítulo explicativo fica fixo; o nome da foto atual vai na nota.
            imageRow.setNote(image == null ? getString(R.string.settings_home_no_image)
                : image.isFile() ? HomeImage.displayName(image) : getString(R.string.settings_home_image_missing), false);
            File bg = HomeImage.fileBg(this);
            bgRow.setNote(bg == null ? getString(R.string.settings_home_no_bg)
                : bg.isFile() ? HomeImage.displayName(bg) : getString(R.string.settings_home_image_missing), false);
        });
        list.renderAll();
        Touch.install(findViewById(android.R.id.content));
    }

    private void build() {
        list.section(getString(R.string.settings_home_modules));

        for (HomeGrid.Module module : HomeGrid.Module.values()) {
            OptionList.Row row = list.plain(getString(module.titleRes), getString(hintRes(module)))
                .help(getString(R.string.settings_help_home_module));
            TextView button = row.addButton("", () -> showSlotPicker(module));
            button.setMinWidth(dp(190));
            slotButtons.put(module, button);
            if (module == HomeGrid.Module.IMAGE) {
                imageRow = row;
                row.help(getString(R.string.settings_help_home_pick_image));
                row.addButton(getString(R.string.settings_home_pick_image), () -> pickImage(false));
            }
        }
        list.action(getString(R.string.settings_home_reset), getString(R.string.settings_home_reset_hint),
            getString(R.string.settings_reset), () -> {
            LauncherConfig.resetHomeLayout(this);
            list.renderAll();
        }).help(getString(R.string.settings_help_home_reset));

        list.section(getString(R.string.settings_home_background));
        bgRow = list.plain(getString(R.string.settings_home_bg_image), getString(R.string.settings_home_bg_hint))
            .help(getString(R.string.settings_help_home_bg));
        bgRow.addButton(getString(R.string.settings_home_pick_image), () -> pickImage(true));
    }

    private int hintRes(HomeGrid.Module module) {
        switch (module) {
            case NOW_PLAYING: return R.string.settings_home_module_now_playing_hint;
            case CLOCK: return R.string.settings_home_module_clock_hint;
            case VOLUME: return R.string.settings_home_module_volume_hint;
            case FAN: return R.string.settings_home_module_fan_hint;
            case BLUETOOTH: return R.string.settings_home_module_bluetooth_hint;
            case IMAGE: return R.string.settings_home_module_image_hint;
            case SPEED: return R.string.settings_home_module_speed_hint;
            case GPS: return R.string.settings_home_module_gps_hint;
            default: return R.string.settings_home_modules_hint;
        }
    }

    /** The home map as a picker: tapping a slot places {@code module} there (swapping if taken). */
    private void showSlotPicker(HomeGrid.Module module) {
        Dialog d = new Dialog(this, R.style.OverlayDialog);
        LinearLayout panel = panel(d, 820, getString(module.titleRes), getString(R.string.settings_home_pick_slot_hint));

        HomeMapView picker = new HomeMapView(this);
        picker.setPicker(module, slot -> {
            d.dismiss();
            place(module, slot);
        });
        panel.addView(picker, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.RIGHT);
        buttons.setPadding(0, dp(16), 0, 0);
        panel.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        addDialogButton(buttons, getString(R.string.settings_home_hidden), () -> {
            d.dismiss();
            place(module, null);
        });
        addDialogButton(buttons, getString(R.string.settings_home_cancel), d::dismiss);
        show(d);
    }

    /** Dialog panel with a title and a hint; content goes below. Shown by {@link #show}. */
    private LinearLayout panel(Dialog d, int widthDp, String title, String hint) {
        if (dialog != null) dialog.dismiss();
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> d.dismiss());
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        ThemeDrawables.setBackground(panel, R.drawable.panel);
        panel.setClickable(true);
        panel.setPadding(dp(28), dp(20), dp(28), dp(20));
        root.addView(panel, new FrameLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(26);
        t.setTypeface(Ui.semiBold(this));
        panel.addView(t);
        TextView h = new TextView(this);
        h.setText(hint);
        h.setTextColor(Ui.TEXT_MUTED);
        h.setTextSize(18);
        h.setTypeface(Ui.regular(this));
        h.setPadding(0, dp(4), 0, dp(14));
        panel.addView(h);
        d.setContentView(root);
        return panel;
    }

    private void show(Dialog d) {
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        Ui.hideSystemBars(d.getWindow());
        d.show();
        Touch.install(d.getWindow().getDecorView());
        dialog = d;
    }

    private void addDialogButton(LinearLayout parent, String text, Runnable onClick) {
        TextView b = (TextView) getLayoutInflater().inflate(R.layout.option_button, parent, false);
        b.setText(text);
        b.setTypeface(Ui.medium(this));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
        lp.leftMargin = dp(14);
        parent.addView(b, lp);
        b.setOnClickListener(v -> onClick.run());
    }

    /** {@code slot} null hides the module. A swap also moves another one: refresh every row and the map. */
    private void place(HomeGrid.Module module, HomeGrid.Slot slot) {
        LauncherConfig.setHomeSlot(this, module, slot);
        list.renderAll();
    }

    private int dp(float v) {
        return Math.round(Ui.dp(this, v));
    }

    // ------------------------------------------------------------------ image

    /**
     * Grid of the images in K4811/ (thumbnails load in the background); tapping one picks it.
     * {@code background} true picks the home background (with a Remove option); false the Image module.
     */
    private void pickImage(boolean background) {
        Dialog d = new Dialog(this, R.style.OverlayDialog);
        LinearLayout panel = panel(d, 56 + THUMB_COLUMNS * THUMB_WIDTH + (THUMB_COLUMNS - 1) * 16,
            getString(background ? R.string.settings_home_bg_image : R.string.settings_home_pick_image),
            getString(R.string.settings_home_images_hint));

        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.RIGHT);
        buttons.setPadding(0, dp(16), 0, 0);
        panel.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (background) {
            addDialogButton(buttons, getString(R.string.settings_home_bg_remove), () -> {
                d.dismiss();
                HomeImage.clearBg(this);
                list.renderAll();
            });
        }
        addDialogButton(buttons, getString(R.string.settings_home_cancel), d::dismiss);
        show(d);

        File current = background ? HomeImage.fileBg(this) : HomeImage.file(this);
        loader.execute(() -> {
            List<File> files = HomeImage.list();
            runOnUiThread(() -> {
                if (dialog != d) return;
                if (files.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText(getString(R.string.settings_home_images_empty, HomeImage.roots().get(0)));
                    empty.setTextColor(Ui.TEXT_SOFT);
                    empty.setTextSize(20);
                    empty.setPadding(0, dp(24), 0, dp(24));
                    grid.addView(empty);
                    return;
                }
                LinearLayout row = null;
                for (int i = 0; i < files.size(); i++) {
                    if (i % THUMB_COLUMNS == 0) {
                        row = new LinearLayout(this);
                        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        if (i > 0) rowLp.topMargin = dp(16);
                        grid.addView(row, rowLp);
                    }
                    File f = files.get(i);
                    View cell = thumbnail(d, f, f.equals(current), background);
                    LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(dp(THUMB_WIDTH),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i % THUMB_COLUMNS > 0) cellLp.leftMargin = dp(16);
                    row.addView(cell, cellLp);
                }
                Touch.install(grid);
                // Many images: cap the height to fit the 720 screen and scroll.
                scroll.post(() -> {
                    if (scroll.getHeight() > dp(400)) {
                        scroll.getLayoutParams().height = dp(400);
                        scroll.requestLayout();
                    }
                });
            });
        });
    }

    /** One image of the grid: thumbnail (loaded in the background) and its name inside K4811/. */
    private View thumbnail(Dialog d, File f, boolean selected, boolean background) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClickable(true);
        cell.setOnClickListener(v -> {
            d.dismiss();
            if (background) HomeImage.chooseBg(this, f);
            else HomeImage.choose(this, f);
            list.renderAll();
        });

        FrameLayout frame = new FrameLayout(this);
        ThemeDrawables.setBackground(frame, selected ? R.drawable.pill : R.drawable.art_frame);
        frame.setActivated(selected);
        int pad = dp(selected ? 3 : 1);
        frame.setPadding(pad, pad, pad, pad);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        cell.addView(frame, new LinearLayout.LayoutParams(dp(THUMB_WIDTH), dp(THUMB_HEIGHT)));

        TextView name = new TextView(this);
        name.setText(HomeImage.displayName(f));
        name.setTextColor(selected ? Ui.TEXT : Ui.TEXT_MUTED);
        name.setTextSize(15);
        name.setTypeface(selected ? Ui.semiBold(this) : Ui.regular(this));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        name.setPadding(0, dp(6), 0, 0);
        cell.addView(name);

        int w = dp(THUMB_WIDTH);
        int h = dp(THUMB_HEIGHT);
        loader.execute(() -> {
            Bitmap bmp = HomeImage.load(f, w, h);
            runOnUiThread(() -> {
                if (bmp != null && dialog == d) image.setImageBitmap(bmp);
            });
        });
        return cell;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        loader.shutdownNow();
        list.release();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Ui.applyDesignDensity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.hideSystemBars(getWindow());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(getWindow());
    }
}
