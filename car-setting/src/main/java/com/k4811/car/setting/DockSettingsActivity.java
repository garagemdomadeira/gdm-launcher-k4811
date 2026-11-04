package com.k4811.car.setting;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.config.LauncherConfig;
import com.k4811.common.system.ExternalApps;
import com.k4811.common.ui.DockView;
import com.k4811.common.ui.IconView;
import com.k4811.car.setting.ui.OptionList;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;

import java.util.Set;

/**
 * Ícones do dock (Configurações › Customizar › Customizar dock): Início e Apps ficam sempre;
 * o motorista escolhe até {@link LauncherConfig#MAX_DOCK_TOGGLES} dos outros, e o nome do Z-Link.
 * Cada linha tem título, texto explicativo e botão de ajuda. Sem dock nesta tela.
 */
public class DockSettingsActivity extends Activity {

    private OptionList list;
    private OptionList.Row counter;
    private final java.util.Map<DockView.Item, OptionList.Row> swapRows = new java.util.EnumMap<>(DockView.Item.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.applyFont(findViewById(R.id.settings_root));
        TextView title = (TextView) findViewById(R.id.settings_title);
        title.setText(R.string.settings_dock_section);
        title.setTypeface(Ui.semiBold(this));
        IconView back = (IconView) findViewById(R.id.settings_back);
        back.set(IconView.Icon.CHEVRON_LEFT, IconView.Style.NONE);
        back.setOnClickListener(v -> finish());

        list = new OptionList(this, (LinearLayout) findViewById(R.id.settings_content));
        build();
        list.setAfterRender(this::renderCounter);
        list.renderAll();
        Touch.install(findViewById(android.R.id.content));
    }

    private void build() {
        int max = LauncherConfig.MAX_DOCK_TOGGLES;
        counter = list.info(getString(R.string.settings_dock_items), () -> LauncherConfig.dockToggles(this).size()
            + " / " + max);
        counter.setSubtitle(getString(R.string.settings_dock_hint, max));
        counter.help(getString(R.string.settings_help_dock_items, max));

        for (DockView.Item item : DockView.Item.values()) {
            if (!item.toggleable) {
                OptionList.Row fixed = list.info(item.title(this), () -> getString(R.string.settings_always_visible));
                fixed.setSubtitle(getString(R.string.settings_dock_fixed_hint));
                fixed.help(getString(R.string.settings_help_dock_fixed));
                continue;
            }
            OptionList.Row row = list.toggle(item.title(this), getString(R.string.settings_dock_item_hint),
                () -> LauncherConfig.dockToggles(this).contains(item),
                on -> {
                    Set<DockView.Item> set = LauncherConfig.dockToggles(this);
                    if (on) set.add(item);
                    else set.remove(item);
                    LauncherConfig.setDockToggles(this, set);
                })
                .local()
                .help(getString(R.string.settings_help_dock_item, max))
                .visibleWhen(() -> item.isAvailable(this))
                .enabledWhen(() -> {
                    Set<DockView.Item> set = LauncherConfig.dockToggles(this);
                    return set.contains(item) || set.size() < max;
                });
            // Trocar a função do botão por um app (ex.: o Waze no lugar do Equalizador).
            row.addButton(getString(R.string.settings_dock_swap), () -> pickApp(item, row));
            swapRows.put(item, row);
        }

        int[] values = new int[LauncherConfig.ZLINK_NAMES.length];
        for (int i = 0; i < values.length; i++) values[i] = i;
        list.choice(getString(R.string.settings_zlink_name), getString(R.string.settings_zlink_name_hint),
            LauncherConfig.ZLINK_NAMES, values,
            () -> LauncherConfig.zlinkName(this),
            i -> LauncherConfig.setZlinkName(this, i))
            .local()
            .help(getString(R.string.settings_help_zlink_name))
            .visibleWhen(() -> DockView.Item.ZLINK.isAvailable(this));
        java.util.List<String> nav = ExternalApps.navApps(this);
        String[] navNames = new String[nav.size()];
        int[] navValues = new int[nav.size()];
        for (int i = 0; i < navNames.length; i++) {
            navNames[i] = ExternalApps.appLabel(this, nav.get(i));
            navValues[i] = i;
        }
        list.choice(getString(R.string.settings_nav_dock_app), getString(R.string.settings_nav_dock_app_hint),
            navNames, navValues,
            () -> Math.max(0, nav.indexOf(LauncherConfig.navApp(this))),
            i -> LauncherConfig.setNavApp(this, nav.get(i)))
            .local()
            .help(getString(R.string.settings_help_nav_dock_app))
            .visibleWhen(() -> !nav.isEmpty());
        list.action(getString(R.string.settings_dock_reset), getString(R.string.settings_dock_reset_hint),
            getString(R.string.settings_reset), () -> {
            LauncherConfig.resetDockToggles(this);
            list.renderAll();
        }).help(getString(R.string.settings_help_dock_reset));
    }

    /** Lista de apps para pôr no lugar do botão; a primeira opção volta à função original. */
    private void pickApp(DockView.Item item, OptionList.Row row) {
        android.content.pm.PackageManager pm = getPackageManager();
        android.content.Intent q = new android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        java.util.List<android.content.pm.ResolveInfo> found = pm.queryIntentActivities(q, 0);
        java.util.TreeMap<String, String> apps = new java.util.TreeMap<>(java.text.Collator.getInstance());
        if (found != null) {
            for (android.content.pm.ResolveInfo r : found) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || pkg.equals(com.k4811.common.Apps.LAUNCHER)) continue;
                apps.put(String.valueOf(r.loadLabel(pm)).trim() + "\u0000" + pkg, pkg);
            }
        }
        java.util.List<String> pkgs = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        pkgs.add("");
        names.add(getString(R.string.settings_dock_swap_original, item.title(this)));
        for (java.util.Map.Entry<String, String> e : apps.entrySet()) {
            pkgs.add(e.getValue());
            names.add(e.getKey().substring(0, e.getKey().indexOf('\u0000')));
        }
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(getString(R.string.settings_dock_swap_title, item.title(this)))
            .setItems(names.toArray(new String[0]), (d, which) -> {
                LauncherConfig.setDockApp(this, item, pkgs.get(which));
                if (!pkgs.get(which).isEmpty()) {
                    Set<DockView.Item> set = LauncherConfig.dockToggles(this);
                    if (!set.contains(item) && set.size() < LauncherConfig.MAX_DOCK_TOGGLES) {
                        set.add(item);
                        LauncherConfig.setDockToggles(this, set);
                    }
                }
                list.renderAll();
            })
            .show();
    }

    private void renderSwaps() {
        for (java.util.Map.Entry<DockView.Item, OptionList.Row> e : swapRows.entrySet()) {
            String pkg = LauncherConfig.dockApp(this, e.getKey());
            e.getValue().setNote(pkg.isEmpty() ? null
                : getString(R.string.settings_dock_swapped, ExternalApps.appLabel(this, pkg)), false);
        }
    }

    private void renderCounter() {
        renderSwaps();
        int n = LauncherConfig.dockToggles(this).size();
        counter.setNote(n >= LauncherConfig.MAX_DOCK_TOGGLES
            ? getString(R.string.settings_dock_full, LauncherConfig.MAX_DOCK_TOGGLES) : null, false);
    }

    @Override
    protected void onDestroy() {
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
