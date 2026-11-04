package com.k4811.launcher.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;

import com.k4811.common.config.LauncherConfig;
import com.k4811.common.home.HomeGrid;
import com.k4811.common.ui.Ui;
import com.k4811.launcher.R;
import com.k4811.launcher.pip.PipView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Módulo PIP: um app de verdade rodando dentro da tela inicial (por padrão o app de navegação: Waze, Maps…). O botão
 * de apps troca o app, o de expandir abre em tela cheia; ao voltar para a tela inicial, o app volta para o PIP.
 * Precisa do launcher como app privilegiado (ROM); fora dela o quadro fica vazio.
 */
public final class HomePip {
    private static final String PREFS = "gdm_home";
    private static final String KEY_APP = "pip_app";

    private final Activity activity;
    private final HomeModuleViews modules;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PipView pip;
    private final TextView empty;
    private boolean ready;
    private boolean resumed;

    public HomePip(Activity activity, HomeModuleViews modules) {
        this.activity = activity;
        this.modules = modules;
        pip = (PipView) activity.findViewById(R.id.pip_view);
        empty = (TextView) activity.findViewById(R.id.pip_empty);
        empty.setTypeface(Ui.regular(activity));
        empty.setOnClickListener(v -> pick());
        ImageView swap = (ImageView) activity.findViewById(R.id.pip_swap);
        ImageView full = (ImageView) activity.findViewById(R.id.pip_full);
        swap.setColorFilter(Ui.TEXT_SOFT);
        full.setColorFilter(Ui.TEXT_SOFT);
        swap.setOnClickListener(v -> pick());
        full.setOnClickListener(v -> openFull());
        pip.setup(0, Ui.dp(activity, 20), v -> {
            ready = true;
            launch();
        });
        render();
    }

    public void resume() {
        resumed = true;
        render();
        pip.wake();
        // de volta de um app aberto em tela cheia: traz o app para o PIP de novo
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::launch, 400);
    }

    public void pause() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void release() {
        pause();
    }

    private boolean showing() {
        return modules.slotOf(HomeGrid.Module.PIP) != null;
    }

    /** O app do PIP: o escolhido, ou o de navegação das Configurações; null se nenhum. */
    private String app() {
        String chosen = prefs().getString(KEY_APP, "");
        if (!chosen.isEmpty() && launchIntent(chosen) != null) return chosen;
        String nav = LauncherConfig.navApp(activity);
        return nav.isEmpty() ? null : nav;
    }

    private void render() {
        String app = app();
        pip.showEmpty(app == null || !ready);
        empty.setText(app == null ? R.string.home_pip_empty : R.string.home_pip_unavailable);
        activity.findViewById(R.id.pip_full).setVisibility(app == null ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void launch() {
        if (!resumed || !showing()) return;
        String app = app();
        render();
        if (app == null || !ready) return;
        Intent i = launchIntent(app);
        if (i == null || !pip.launch(i)) pip.showEmpty(true);
    }

    private void openFull() {
        String app = app();
        Intent i = app == null ? null : launchIntent(app);
        if (i == null) return;
        try {
            activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    /** Lista dos apps para o PIP (o de navegação primeiro). */
    private void pick() {
        PackageManager pm = activity.getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        TreeMap<String, String> apps = new TreeMap<>(Collator.getInstance());
        if (found != null) {
            for (ResolveInfo r : found) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(activity.getPackageName())) continue;
                apps.put(String.valueOf(r.loadLabel(pm)).trim() + "\u0000" + pkg, pkg);
            }
        }
        List<String> pkgs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, String> e : apps.entrySet()) {
            pkgs.add(e.getValue());
            names.add(e.getKey().substring(0, e.getKey().indexOf('\u0000')));
        }
        String current = app();
        new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(R.string.home_pip_pick_title)
            .setSingleChoiceItems(names.toArray(new String[0]), current == null ? -1 : pkgs.indexOf(current),
                (d, which) -> {
                    d.dismiss();
                    prefs().edit().putString(KEY_APP, pkgs.get(which)).apply();
                    launch();
                })
            .show();
    }

    private Intent launchIntent(String pkg) {
        try {
            return activity.getPackageManager().getLaunchIntentForPackage(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
