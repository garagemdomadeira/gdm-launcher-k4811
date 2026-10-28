package com.k4811.car.setting;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.audio.AudioGains;
import com.k4811.car.setting.audio.AudioPanel;
import com.k4811.common.config.LauncherConfig;
import com.k4811.common.media.AppMediaClient;
import com.k4811.common.system.BottomBar;
import com.k4811.common.system.CrashLog;
import com.k4811.common.system.DevMode;
import com.k4811.common.system.ExternalApps;
import com.k4811.common.system.NwdSettings;
import com.k4811.car.setting.system.PlatformCompat;
import com.k4811.common.system.SystemProps;
import com.k4811.common.ui.IconView;
import com.k4811.car.setting.ui.InputDialog;
import com.k4811.car.setting.ui.OptionList;
import com.k4811.car.setting.ui.ThemeSwatches;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Configurações abertas pela engrenagem. Opções da suíte (idioma, aparência, home, dock)
 * mais um atalho para o Config. Veículo de fábrica. Cada linha tem título, texto explicativo
 * e botão de ajuda com o texto longo.
 */
public class SettingsActivity extends Activity {

    private static final String ADVANCED_PASSWORD = "1234";
    private static final String KEY_FACTORY_FEATURE = "factory_feature";

    private static final int PANEL_MAIN = 0;
    private static final int PANEL_CUSTOMIZE = 1;
    private static final int PANEL_SYSTEM = 2;
    private static final int PANEL_AUDIO = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout[] panels = new LinearLayout[4];
    private final TextView[] tabs = new TextView[4];
    private AudioPanel audio;
    private OptionList list;
    private Dialog dialog;

    private final android.database.ContentObserver observer = new android.database.ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            handler.removeCallbacks(rerender);
            handler.postDelayed(rerender, 200);
        }
    };
    private final Runnable rerender = this::renderAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.applyFont(findViewById(R.id.settings_root));
        ((TextView) findViewById(R.id.settings_title)).setTypeface(Ui.semiBold(this));
        IconView back = (IconView) findViewById(R.id.settings_back);
        back.set(IconView.Icon.CHEVRON_LEFT, IconView.Style.NONE);
        back.setOnClickListener(v -> finish());

        LinearLayout content = (LinearLayout) findViewById(R.id.settings_content);
        for (int i = 0; i < panels.length; i++) {
            panels[i] = new LinearLayout(this);
            panels[i].setOrientation(LinearLayout.VERTICAL);
            content.addView(panels[i]);
        }
        list = new OptionList(this, panels[PANEL_MAIN]);
        showCompatAlert();
        buildLanguage();
        buildMainHome();
        list.into(panels[PANEL_CUSTOMIZE]);
        buildAppearance();
        buildHomeScreen();
        list.into(panels[PANEL_SYSTEM]);
        buildDevice();
        buildAbout();
        showLastCrash();
        if (AudioGains.ENABLED) {
            list.into(panels[PANEL_AUDIO]);
            audio = new AudioPanel(this, list);
            audio.build();
        }
        renderAll();
        setUpTabs();

        getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, observer);
        Touch.install(findViewById(android.R.id.content));
    }

    private void renderAll() {
        if (!isFinishing()) list.renderAll();
    }

    private void setUpTabs() {
        findViewById(R.id.settings_sidebar).setVisibility(View.VISIBLE);
        findViewById(R.id.settings_tab_assists).setVisibility(View.GONE);
        int[] ids = {R.id.settings_tab_main, R.id.settings_tab_customize,
            R.id.settings_tab_system, R.id.settings_tab_audio};
        for (int i = 0; i < tabs.length; i++) {
            int panel = i;
            tabs[i] = (TextView) findViewById(ids[i]);
            tabs[i].setTypeface(Ui.medium(this));
            tabs[i].setOnClickListener(v -> showPanel(panel));
        }
        tabs[PANEL_AUDIO].setVisibility(AudioGains.ENABLED ? View.VISIBLE : View.GONE);
        TextView advanced = (TextView) findViewById(R.id.settings_tab_advanced);
        advanced.setVisibility(DevMode.enabled() ? View.VISIBLE : View.GONE);
        advanced.setTypeface(Ui.medium(this));
        advanced.setOnClickListener(v -> askAdvancedPassword());
        TextView carSettings = (TextView) findViewById(R.id.settings_tab_car_settings);
        // Prefere o Config. Veículo de fábrica; se não houver, abre o com.nwd.car.setting (esta suíte).
        carSettings.setVisibility(View.VISIBLE);
        carSettings.setTypeface(Ui.medium(this));
        carSettings.setOnClickListener(v -> ExternalApps.openCarSettings(this, null));
        showPanel(PANEL_MAIN);
    }

    private void showPanel(int panel) {
        for (int i = 0; i < panels.length; i++) {
            panels[i].setVisibility(i == panel ? View.VISIBLE : View.GONE);
            tabs[i].setActivated(i == panel);
        }
        findViewById(R.id.settings_scroll).scrollTo(0, 0);
        Touch.fadeIn(panels[panel]);
    }

    private void showCompatAlert() {
        PlatformCompat.Verdict v = PlatformCompat.verdict(this);
        String text = PlatformCompat.alert(this, v);
        TextView alert = (TextView) findViewById(R.id.settings_alert);
        if (text == null) return;
        alert.setText(text);
        alert.setTextColor(v == PlatformCompat.Verdict.UNTESTED ? Ui.WARNING : Ui.DANGER);
        alert.setVisibility(View.VISIBLE);
    }

    private void buildLanguage() {
        list.section(getString(R.string.settings_language_section));
        int[] values = new int[LauncherConfig.LANGUAGES.length];
        for (int i = 0; i < values.length; i++) values[i] = i;
        list.choice(getString(R.string.settings_language), getString(R.string.settings_language_hint),
            LauncherConfig.LANGUAGE_NAMES, values,
            () -> {
                String tag = LauncherConfig.language(this);
                for (int i = 0; i < LauncherConfig.LANGUAGES.length; i++) {
                    if (LauncherConfig.LANGUAGES[i].equals(tag)) return i;
                }
                return -1;
            },
            i -> {
                if (LauncherConfig.LANGUAGES[i].equals(LauncherConfig.language(this))) return;
                LauncherConfig.setLanguage(this, LauncherConfig.LANGUAGES[i]);
                restartScreens();
            }).local().help(getString(R.string.settings_help_language));
    }

    private void buildAppearance() {
        list.section(getString(R.string.settings_appearance_section));
        list.toggle(getString(R.string.settings_dark_mode), getString(R.string.settings_dark_mode_hint),
            () -> LauncherConfig.darkMode(this),
            on -> {
                if (on == LauncherConfig.darkMode(this)) return;
                LauncherConfig.setDarkMode(this, on);
                restartScreens();
            }).local().help(getString(R.string.settings_help_dark_mode));
        int theme = LauncherConfig.theme(this);
        list.custom(getString(R.string.settings_theme_color), getString(R.string.settings_theme_color_hint),
            new ThemeSwatches(this, theme, this::pickTheme))
            .help(getString(R.string.settings_help_theme_color));
        int[] values = new int[LauncherConfig.FONTS.length];
        for (int i = 0; i < values.length; i++) values[i] = i;
        list.choice(getString(R.string.settings_font), getString(R.string.settings_font_hint),
            LauncherConfig.FONT_NAMES, values,
            () -> {
                String id = LauncherConfig.font(this);
                for (int i = 0; i < LauncherConfig.FONTS.length; i++) {
                    if (LauncherConfig.FONTS[i].equals(id)) return i;
                }
                return -1;
            },
            i -> {
                if (LauncherConfig.FONTS[i].equals(LauncherConfig.font(this))) return;
                LauncherConfig.setFont(this, LauncherConfig.FONTS[i]);
                restartScreens();
            }).local().help(getString(R.string.settings_help_font));
    }

    private void pickTheme(int theme) {
        if (theme == LauncherConfig.theme(this)) return;
        LauncherConfig.setTheme(this, theme);
        restartScreens();
    }

    private void restartScreens() {
        Ui.applyLanguage(this);
        Intent again = new Intent(this, SettingsActivity.class);
        again.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(again);
        finish();
        overridePendingTransition(0, 0);
    }

    private void buildHomeScreen() {
        list.section(getString(R.string.settings_home_section));
        list.action(getString(R.string.settings_customize_home), getString(R.string.settings_customize_home_hint),
            getString(R.string.settings_open), () -> startActivity(new Intent(this, HomeSettingsActivity.class)))
            .help(getString(R.string.settings_help_customize_home));
        list.action(getString(R.string.settings_customize_dock), getString(R.string.settings_customize_dock_hint),
            getString(R.string.settings_open), () -> startActivity(new Intent(this, DockSettingsActivity.class)))
            .help(getString(R.string.settings_help_customize_dock));
    }

    private void buildMainHome() {
        list.section(getString(R.string.settings_home_section));
        list.toggle(getString(R.string.settings_show_day_night),
            getString(R.string.settings_show_day_night_hint),
            () -> LauncherConfig.showDayNight(this),
            on -> LauncherConfig.setShowDayNight(this, on))
            .local().help(getString(R.string.settings_help_day_night));
        list.toggle(getString(R.string.settings_show_voltmeter),
            getString(R.string.settings_show_voltmeter_hint),
            () -> LauncherConfig.showVoltmeter(this),
            on -> LauncherConfig.setShowVoltmeter(this, on))
            .local().help(getString(R.string.settings_help_voltmeter));
        list.toggle(getString(R.string.settings_volume_overlay),
            getString(R.string.settings_volume_overlay_hint),
            () -> LauncherConfig.showVolumeOverlay(this),
            on -> LauncherConfig.setShowVolumeOverlay(this, on))
            .local().help(getString(R.string.settings_help_volume_overlay));
        list.toggle(getString(R.string.settings_media_apps),
            getString(R.string.settings_media_apps_hint),
            () -> LauncherConfig.mediaAppControls(this),
            on -> {
                LauncherConfig.setMediaAppControls(this, on);
                // Ao ligar sem a permissão, leva o usuário para "Acesso a notificações".
                if (on && !AppMediaClient.enabled(this)) openNotificationAccess();
            })
            .local().help(getString(R.string.settings_help_media_apps));
        String[] sides = {getString(R.string.settings_edge_menu_off), getString(R.string.settings_edge_menu_bottom),
            getString(R.string.settings_edge_menu_left), getString(R.string.settings_edge_menu_right)};
        list.choice(getString(R.string.settings_edge_menu), getString(R.string.settings_edge_menu_hint),
            sides, LauncherConfig.EDGE_MENU_SIDES,
            () -> LauncherConfig.edgeMenuSide(this),
            side -> LauncherConfig.setEdgeMenuSide(this, side))
            .local().help(getString(R.string.settings_help_edge_menu));
        int[] values = LauncherConfig.SCREEN_OFF_AFTER;
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] == 0 ? getString(R.string.settings_screen_off_never) : values[i] + " s";
        }
        list.choice(getString(R.string.settings_screen_off_after), getString(R.string.settings_screen_off_after_hint),
            labels, values,
            () -> LauncherConfig.screenOffAfter(this),
            seconds -> LauncherConfig.setScreenOffAfter(this, seconds))
            .local().help(getString(R.string.settings_help_screen_off_after));
    }

    private boolean nwd() {
        return NwdSettings.isInstalled(this);
    }

    /** Abre a tela do Android de "Acesso a notificações" (permissão dos controles de mídia de apps). */
    private void openNotificationAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    private void buildDevice() {
        list.section(getString(R.string.settings_device_section));
        list.toggle(getString(R.string.settings_hide_bottom_bar), getString(R.string.settings_hide_bottom_bar_hint),
            () -> LauncherConfig.hideBottomBar(this),
            on -> BottomBar.setEnabled(this, on))
            .local()
            .visibleWhen(() -> BottomBar.available(this))
            .help(getString(R.string.settings_help_hide_bottom_bar));
    }

    private String yesNo(boolean v) {
        return getString(v ? R.string.settings_yes : R.string.settings_no);
    }

    private static String orDash(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s.trim();
    }

    private void buildAbout() {
        list.section(getString(R.string.settings_about_section));
        list.info(getString(R.string.settings_compat), () -> PlatformCompat.label(this, PlatformCompat.verdict(this)));
        list.info("Firmware", () -> orDash(SystemProps.get(PlatformCompat.PROP_FIRMWARE, "")));
        list.info("Android", () -> Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        if (DevMode.enabled()) {
            list.info(getString(R.string.settings_nwd_platform), () -> getString(R.string.settings_platform_value,
                orDash(PlatformCompat.platform()), orDash(PlatformCompat.family())));
            list.info("SoC", () -> orDash(SystemProps.get(PlatformCompat.PROP_SOC, "")));
            list.info("DSP", () -> yesNo("1".equals(SystemProps.get("sys.nwd.hw.has.dsp", "").trim())));
            list.info(getString(R.string.settings_setting_service), () -> yesNo(nwd()));
            list.info(getString(R.string.settings_bt_service), () -> yesNo(PlatformCompat.hasBtService(this)));
            list.info(getString(R.string.settings_factory_features), this::factoryFeatures);
            list.info(getString(R.string.settings_reverse_camera), () -> orDash(SystemProps.get("persist.sys.nwd.hacamera.ctrl", ""))
                + " · " + orDash(SystemProps.get("persist.vendor.sensor.back.mode", "")));
            list.info(getString(R.string.settings_avm), () -> getString(ExternalApps.hasAvm(this)
                ? R.string.settings_installed : R.string.settings_not_installed));
        }
        list.info(getString(R.string.settings_locale_tz), () -> Locale.getDefault().toString() + " · " + TimeZone.getDefault().getID());
        list.info(getString(R.string.app_name), this::appVersion);
    }

    private String factoryFeatures() {
        if (!NwdSettings.has(this, KEY_FACTORY_FEATURE)) return "—";
        int f = NwdSettings.getInt(this, KEY_FACTORY_FEATURE, 0);
        StringBuilder sb = new StringBuilder();
        if ((f & 262144) != 0) sb.append("CarPlay, ");
        if ((f & 16) != 0) sb.append("360, ");
        if ((f & (512 | 1024 | 32768)) != 0) sb.append("DVR, ");
        if ((f & 8) != 0) sb.append("Phone Connected, ");
        return sb.length() == 0 ? getString(R.string.settings_none) : sb.substring(0, sb.length() - 2);
    }

    private String appVersion() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName + " (" + p.versionCode + ")";
        } catch (Exception e) {
            return "—";
        }
    }

    private void askAdvancedPassword() {
        if (dialog != null) dialog.dismiss();
        dialog = InputDialog.show(this, getString(R.string.settings_advanced), getString(R.string.settings_enter_password), "", true,
            getString(R.string.settings_sign_in),
            text -> {
                if (!ADVANCED_PASSWORD.equals(text.trim())) return getString(R.string.settings_wrong_password);
                startActivity(new Intent(this, AdvancedActivity.class));
                return null;
            });
    }

    private void showLastCrash() {
        String crash = CrashLog.read(this);
        View box = findViewById(R.id.crash_box);
        if (crash == null || !DevMode.enabled()) {
            box.setVisibility(View.GONE);
            return;
        }
        TextView text = (TextView) findViewById(R.id.crash_text);
        text.setTypeface(Typeface.MONOSPACE);
        text.setText(crash);
        box.setVisibility(View.VISIBLE);
        findViewById(R.id.crash_clear).setOnClickListener(v -> {
            CrashLog.clear(this);
            box.setVisibility(View.GONE);
        });
    }

    @Override
    protected void onDestroy() {
        getContentResolver().unregisterContentObserver(observer);
        handler.removeCallbacksAndMessages(null);
        list.release();
        if (audio != null) audio.release();
        if (dialog != null) dialog.dismiss();
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
        renderAll();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(getWindow());
    }
}
