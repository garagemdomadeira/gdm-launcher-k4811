package com.k4811.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.ui.Ui;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Tela do GDM Atualizações: lista o que tem de novo, Procurar agora, Confirmar update (apps, um por um) e, para a ROM,
 * uma segunda confirmação (baixa no /cache e a central reinicia para gravar, sem apagar os dados).
 */
public final class UpdatesActivity extends Activity {
    static final String EXTRA_INSTALL = "install";
    private static volatile boolean working;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView last;
    private TextView status;
    private TextView confirm;
    private View progressTrack;
    private View progress;
    private Ota ota;
    private List<Ota.App> apps = new ArrayList<>();
    private Ota.Rom rom;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.applyDesignDensity(this);
        setContentView(R.layout.activity_updates);
        Ui.applyFont(findViewById(android.R.id.content));
        list = (LinearLayout) findViewById(R.id.ota_list);
        last = (TextView) findViewById(R.id.ota_last);
        status = (TextView) findViewById(R.id.ota_status);
        confirm = (TextView) findViewById(R.id.ota_confirm);
        progressTrack = findViewById(R.id.ota_progress_track);
        progress = findViewById(R.id.ota_progress);
        ((TextView) findViewById(R.id.ota_title)).setTypeface(Ui.semiBold(this));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFE9EBEE);
        g.setCornerRadius(Ui.dp(this, 18));
        confirm.setBackground(g);
        confirm.setTypeface(Ui.semiBold(this));
        confirm.setOnClickListener(v -> installApps());
        findViewById(R.id.ota_check).setOnClickListener(v -> check());
        findViewById(R.id.ota_close).setOnClickListener(v -> finish());
        CheckReceiver.schedule(this);
        Prompt.dismiss(getApplicationContext());

        ota = Ota.cached(this);
        render();
        boolean install = getIntent() != null && getIntent().getBooleanExtra(EXTRA_INSTALL, false);
        if (install && !apps.isEmpty()) installApps();
        else if (!working) check();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(getWindow());
    }

    // ------------------------------------------------------------------ procurar

    private void check() {
        if (working) return;
        working = true;
        status.setText(R.string.ota_checking);
        new Thread(() -> {
            CheckService.Fetched f = CheckService.obtain(getApplicationContext(), true);
            ui.post(() -> {
                working = false;
                if (isFinishing()) return;
                if (f.ota != null) ota = f.ota;
                render();
                if (f.result == CheckService.Result.OFFLINE) status.setText(R.string.ota_offline);
                else if (f.result == CheckService.Result.BAD_SIGNATURE) status.setText(R.string.ota_bad_sig);
            });
        }, "gdm-ota-ui-check").start();
    }

    // ------------------------------------------------------------------ tela

    private void render() {
        long when = Ota.prefs(this).getLong(Ota.KEY_LAST_CHECK, 0);
        last.setText(getString(R.string.ota_last_check, when == 0 ? getString(R.string.ota_never)
            : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(when))));
        list.removeAllViews();
        apps = ota == null ? new ArrayList<>() : ota.pendingApps(this);
        rom = ota == null ? null : ota.pendingRom(this);
        for (Ota.App a : apps) {
            row(getString(R.string.ota_app_line, a.name, a.installedName == null ? "?" : a.installedName, a.versionName),
                a.notes, null, null);
        }
        if (rom != null) {
            row(getString(R.string.ota_rom_line, rom.version, (int) (rom.size >> 20)), rom.notes,
                getString(R.string.ota_rom_install), v -> confirmRom());
        } else if (Ota.RomInfo.read().channel == null) {
            row(null, getString(R.string.ota_rom_no_channel), null, null);
        }
        confirm.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
        if (!working) {
            setProgress(-1);
            status.setText(apps.isEmpty() && rom == null ? getString(R.string.ota_up_to_date) : "");
        }
    }

    private void row(String title, String body, String button, View.OnClickListener onButton) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(px(22), px(16), px(22), px(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        bg.setCornerRadius(px(20));
        bg.setStroke(px(1.5f), Ui.HAIRLINE);
        r.setBackground(bg);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        if (title != null) {
            TextView t = text(21, Ui.TEXT);
            t.setTypeface(Ui.semiBold(this));
            t.setText(title);
            texts.addView(t);
        }
        if (body != null && !body.isEmpty()) {
            TextView b = text(17, Ui.TEXT_SOFT);
            b.setText(body);
            texts.addView(b);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (button != null) {
            TextView b = text(18, 0xFF0B0E13);
            b.setTypeface(Ui.semiBold(this));
            b.setText(button);
            b.setPadding(px(22), px(12), px(22), px(12));
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFFE9EBEE);
            g.setCornerRadius(px(16));
            b.setBackground(g);
            b.setOnClickListener(onButton);
            r.addView(b);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        if (list.getChildCount() > 0) lp.topMargin = px(12);
        list.addView(r, lp);
    }

    // ------------------------------------------------------------------ apps

    private void installApps() {
        if (working || apps.isEmpty()) return;
        working = true;
        confirm.setEnabled(false);
        confirm.setAlpha(0.5f);
        List<Ota.App> todo = new ArrayList<>(apps);
        new Thread(() -> {
            StringBuilder errors = new StringBuilder();
            int ok = 0;
            for (Ota.App a : todo) {
                ui.post(() -> status.setText(getString(R.string.ota_downloading, a.name, 0)));
                try {
                    Installer.installApp(getApplicationContext(), a, (done, total) -> {
                        int pct = total > 0 ? (int) (done * 100 / total) : 0;
                        ui.post(() -> {
                            status.setText(pct >= 100 ? getString(R.string.ota_installing, a.name)
                                : getString(R.string.ota_downloading, a.name, pct));
                            setProgress(total > 0 ? done / (float) total : -1);
                        });
                        return true;
                    });
                    ok++;
                } catch (Exception e) {
                    if (errors.length() > 0) errors.append('\n');
                    errors.append(getString(R.string.ota_failed, a.name, String.valueOf(e.getMessage())));
                }
            }
            int okCount = ok;
            ui.post(() -> {
                working = false;
                if (isFinishing()) return;
                setProgress(-1);
                confirm.setEnabled(true);
                confirm.setAlpha(1f);
                render();
                status.setText(errors.length() > 0 ? errors.toString() : getString(R.string.ota_done, okCount));
            });
        }, "gdm-ota-apps").start();
    }

    // ------------------------------------------------------------------ ROM

    private void confirmRom() {
        if (working || rom == null) return;
        String why = Installer.romBlocker(this, rom);
        if (why != null) {
            status.setText("chave".equals(why) ? R.string.ota_rom_blocked_key
                : "cache".equals(why) ? R.string.ota_rom_blocked_cache : R.string.ota_rom_blocked_system);
            return;
        }
        long free = Installer.cacheFree();
        if (free >= 0 && free < rom.size + (16L << 20)) {
            status.setText(R.string.ota_rom_no_space);
            return;
        }
        String msg = getString(R.string.ota_rom_confirm_msg, (int) (rom.size >> 20));
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null && cm.isActiveNetworkMetered()) msg = getString(R.string.ota_rom_metered) + "\n\n" + msg;
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(getString(R.string.ota_rom_confirm_title, rom.version))
            .setMessage(msg)
            .setNegativeButton(R.string.ota_later, null)
            .setPositiveButton(R.string.ota_confirm, (d, w) -> installRom())
            .show();
    }

    private void installRom() {
        Ota.Rom r = rom;
        working = true;
        new Thread(() -> {
            try {
                Installer.installRom(getApplicationContext(), r, (done, total) -> {
                    int pct = total > 0 ? (int) (done * 100 / total) : 0;
                    ui.post(() -> {
                        status.setText(getString(R.string.ota_downloading, "ROM " + r.version, pct));
                        setProgress(total > 0 ? done / (float) total : -1);
                    });
                    return true;
                });
                ui.post(() -> status.setText(R.string.ota_rom_rebooting));
            } catch (Exception e) {
                ui.post(() -> {
                    working = false;
                    setProgress(-1);
                    status.setText(getString(R.string.ota_failed, "ROM " + r.version, String.valueOf(e.getMessage())));
                });
            }
        }, "gdm-ota-rom").start();
    }

    // ------------------------------------------------------------------ util

    private void setProgress(float p) {
        progressTrack.setVisibility(p < 0 ? View.INVISIBLE : View.VISIBLE);
        if (p >= 0) {
            progress.setPivotX(0f);
            progress.setScaleX(Math.min(1f, p));
        }
    }

    private TextView text(float sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Ui.medium(this));
        return t;
    }

    private int px(float v) {
        return Math.round(Ui.dp(this, v));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_INSTALL, false)) {
            ota = Ota.cached(this);
            render();
            installApps();
        }
    }
}
