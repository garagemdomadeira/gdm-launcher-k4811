package com.garagemdomadeira.nwd.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.garagemdomadeira.nwd.launcher.home.AppCatalog;
import com.garagemdomadeira.nwd.launcher.home.LauncherPrefs;
import com.garagemdomadeira.nwd.launcher.system.CrashLog;
import com.garagemdomadeira.nwd.launcher.system.DevMode;
import com.garagemdomadeira.nwd.launcher.system.ExternalApps;
import com.garagemdomadeira.nwd.launcher.system.NwdSettings;
import com.garagemdomadeira.nwd.launcher.system.PlatformCompat;
import com.garagemdomadeira.nwd.launcher.system.SystemProps;
import com.garagemdomadeira.nwd.launcher.ui.IconView;
import com.garagemdomadeira.nwd.launcher.ui.InputDialog;
import com.garagemdomadeira.nwd.launcher.ui.OptionList;
import com.garagemdomadeira.nwd.launcher.ui.PropRows;
import com.garagemdomadeira.nwd.launcher.ui.ThemeSwatches;
import com.garagemdomadeira.nwd.launcher.ui.Touch;
import com.garagemdomadeira.nwd.launcher.ui.Ui;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Launcher settings, opened from the gear on the home screen. Options the driver can change
 * safely, split into three panels picked in a side column: Main (language, appearance, utilities), Assists (reverse camera,
 * navigation) and System (the unit, display, "About the unit"). The dock has its own screen
 * ({@link DockSettingsActivity}); factory options live in {@link AdvancedActivity}, behind a password.
 * Each option reads the real system state; nothing the system stores is copied here.
 */
public class SettingsActivity extends Activity {

    /** Time for the NWD service to apply the property before checking. */
    private static final long PROP_CHECK_MS = 1500;
    /** Advanced password (just a guard against accidental taps, not security). */
    private static final String ADVANCED_PASSWORD = "1234";

    // Settings.System keys maintained by the NWD SettingService.
    private static final String KEY_DEFAULT_VOLUME = "mcu_other_setting_default_volume";
    private static final String KEY_MAX_VOLUME = "mcu_max_volume";
    private static final String KEY_GPS_WAY = "mcu_other_setting_gps_setting_type";
    private static final String KEY_VOLUME_MIX = "mcu_other_setting_volume_mix";
    private static final String KEY_BACKLIGHT_DAY = "mcu_back_light_day";
    private static final String KEY_BACKLIGHT_NIGHT = "mcu_back_light_night";
    private static final String KEY_BACKLIGHT_MAX = "mcu_back_light_max";
    private static final String KEY_NO_SOURCE_DEVICE = "mcu_no_source_device_state";
    private static final String KEY_BEEP = "arm_bee_switch";
    private static final String ACTION_BEEP_CHANGED = "com.nwd.action.ACTION_BEEP_SWITCH_CHANGE";
    private static final String KEY_SCREENSAVER = "show_screensaver_time";
    private static final String ACTION_SCREENSAVER = "com.android.screen.protect.CloseScreenSettingReveiver";
    private static final String EXTRA_SCREENSAVER = "key_openscreen_intent_time";
    private static final String KEY_NAVI_PACKAGE = "navigation_packagename";
    private static final String KEY_NAVI_CLASS = "navigation_classname";
    private static final String KEY_NAVI_NAME = "navigation_appname";
    private static final String KEY_NAVI_AUTOSTART = "arm_auto_start_navi_when_acc_on";
    private static final String KEY_FACTORY_FEATURE = "factory_feature";
    private static final String PROP_MEMORY_VOLUME = "persist.sys.nwd.memory.vol";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int PANEL_MAIN = 0;
    private static final int PANEL_ASSISTS = 1;
    private static final int PANEL_SYSTEM = 2;

    private final LinearLayout[] panels = new LinearLayout[3];
    private final TextView[] tabs = new TextView[3];
    private OptionList list;
    private OptionList.Row launcherRow;
    private TextView useGaragem;
    private TextView useOriginal;
    private OptionList.Row brightness;
    private TextView naviButton;
    private Dialog dialog;

    /** SettingService writes the result to Settings.System; re-read everything when something changes. */
    private final ContentObserver observer = new ContentObserver(handler) {
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
        buildAppearance();
        buildUtilities();
        list.into(panels[PANEL_ASSISTS]);
        buildReverse();
        buildNavigation();
        list.into(panels[PANEL_SYSTEM]);
        buildDevice();
        buildScreen();
        buildAbout();
        showLastCrash();
        list.setAfterRender(this::renderExtras);
        renderAll();
        setUpTabs();

        getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, observer);
        Touch.install(findViewById(android.R.id.content));
    }

    private void renderAll() {
        if (!isFinishing()) list.renderAll();
    }

    /** What isn't a regular row: launcher state, day/night, navigation app. */
    private void renderExtras() {
        renderLauncherState();
        renderBrightnessMode();
        renderNavigation();
    }

    // ------------------------------------------------------------------ side tabs

    /** Side column with the three groups (Main, Assists, System), one panel visible at a time, plus Advanced. */
    private void setUpTabs() {
        findViewById(R.id.settings_sidebar).setVisibility(View.VISIBLE);
        int[] ids = {R.id.settings_tab_main, R.id.settings_tab_assists, R.id.settings_tab_system};
        for (int i = 0; i < tabs.length; i++) {
            int panel = i;
            tabs[i] = (TextView) findViewById(ids[i]);
            tabs[i].setTypeface(Ui.medium(this));
            tabs[i].setOnClickListener(v -> showPanel(panel));
        }
        // Advanced isn't a panel: in dev mode it's one more tab that asks for the password.
        TextView advanced = (TextView) findViewById(R.id.settings_tab_advanced);
        advanced.setVisibility(DevMode.enabled() ? View.VISIBLE : View.GONE);
        advanced.setTypeface(Ui.medium(this));
        advanced.setOnClickListener(v -> askAdvancedPassword());
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

    // ------------------------------------------------------------------ compatibility

    private void showCompatAlert() {
        PlatformCompat.Verdict v = PlatformCompat.verdict(this);
        String text = PlatformCompat.alert(this, v);
        TextView alert = (TextView) findViewById(R.id.settings_alert);
        if (text == null) return;
        alert.setText(text);
        alert.setTextColor(v == PlatformCompat.Verdict.UNTESTED ? Ui.WARNING : Ui.DANGER);
        alert.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ language

    private void buildLanguage() {
        list.section(getString(R.string.settings_language_section));
        int[] values = new int[LauncherPrefs.LANGUAGES.length];
        for (int i = 0; i < values.length; i++) values[i] = i;
        list.choice(getString(R.string.settings_language), getString(R.string.settings_language_hint),
            LauncherPrefs.LANGUAGE_NAMES, values,
            () -> {
                String tag = LauncherPrefs.language(this);
                for (int i = 0; i < LauncherPrefs.LANGUAGES.length; i++) {
                    if (LauncherPrefs.LANGUAGES[i].equals(tag)) return i;
                }
                return -1;
            },
            i -> {
                if (LauncherPrefs.LANGUAGES[i].equals(LauncherPrefs.language(this))) return;
                LauncherPrefs.setLanguage(this, LauncherPrefs.LANGUAGES[i]);
                restartScreens();
            }).local();
    }

    // ------------------------------------------------------------------ appearance

    private void buildAppearance() {
        list.section(getString(R.string.settings_appearance_section));
        int[] values = new int[LauncherPrefs.FONTS.length];
        for (int i = 0; i < values.length; i++) values[i] = i;
        list.choice(getString(R.string.settings_font), getString(R.string.settings_font_hint),
            LauncherPrefs.FONT_NAMES, values,
            () -> {
                String id = LauncherPrefs.font(this);
                for (int i = 0; i < LauncherPrefs.FONTS.length; i++) {
                    if (LauncherPrefs.FONTS[i].equals(id)) return i;
                }
                return -1;
            },
            i -> {
                if (LauncherPrefs.FONTS[i].equals(LauncherPrefs.font(this))) return;
                LauncherPrefs.setFont(this, LauncherPrefs.FONTS[i]);
                restartScreens();
            }).local();

        int theme = LauncherPrefs.theme(this);
        list.custom(getString(R.string.settings_theme_color), getString(R.string.settings_theme_color_hint),
            new ThemeSwatches(this, theme, this::pickTheme));
        list.action(getString(R.string.settings_customize_dock), getString(R.string.settings_customize_dock_hint),
            getString(R.string.settings_open), () -> startActivity(new Intent(this, DockSettingsActivity.class)));
    }

    private void pickTheme(int theme) {
        if (theme == LauncherPrefs.theme(this)) return;
        LauncherPrefs.setTheme(this, theme);
        restartScreens();
    }

    /** Recreates the home screen (and the rest of the stack) with the new language or font. */
    private void restartScreens() {
        Ui.applyLanguage(this);
        Intent home = new Intent(this, HomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(home);
        Intent again = new Intent(this, SettingsActivity.class);
        startActivity(again);
        finish();
    }

    // ------------------------------------------------------------------ utilities

    private void buildUtilities() {
        list.section(getString(R.string.settings_utilities_section));
        list.toggle(getString(R.string.settings_show_voltmeter),
            getString(R.string.settings_show_voltmeter_hint),
            () -> LauncherPrefs.showVoltmeter(this),
            on -> LauncherPrefs.setShowVoltmeter(this, on)).local();
        list.toggle(getString(R.string.settings_show_volume), getString(R.string.settings_show_volume_hint),
            () -> LauncherPrefs.showVolumeControls(this),
            on -> LauncherPrefs.setShowVolumeControls(this, on)).local();
    }

    // ------------------------------------------------------------------ device (default launcher, beep, volume)

    /** Shows which launcher the unit uses. {@code note} (optional) goes after, e.g. failed to apply. */
    private void renderLauncher(String note) {
        renderLauncherState();
        launcherRow.setNote(note, note != null);
    }

    /** State only (note untouched): runs on every re-read, including while applying. */
    private void renderLauncherState() {
        String current = SystemProps.get(SystemProps.PROP_DEFAULT_LAUNCHER, "");
        boolean garagem = getPackageName().equals(current);
        boolean original = LauncherPrefs.ORIGINAL_LAUNCHER.equals(current);
        String who = garagem ? "Garagem"
            : original ? getString(R.string.settings_launcher_original)
            : current.isEmpty() ? getString(R.string.settings_launcher_unset) : current;
        launcherRow.setSubtitle(getString(R.string.settings_launcher_current, who));
        useGaragem.setActivated(garagem);
        useOriginal.setActivated(original);
    }

    /**
     * Switches the default launcher via the NWD channel (persist.nwd.launcher.default), then checks.
     * When switching back to the stock launcher, opens it and stops asking when Garagem starts.
     */
    private void setDefaultLauncher(String pkg) {
        boolean toOriginal = LauncherPrefs.ORIGINAL_LAUNCHER.equals(pkg);
        if (!toOriginal) LauncherPrefs.setAskDefaultLauncher(this, true);
        SystemProps.requestSet(this, SystemProps.PROP_DEFAULT_LAUNCHER, pkg);
        launcherRow.setNote(getString(R.string.settings_applying), false);
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            String now = SystemProps.get(SystemProps.PROP_DEFAULT_LAUNCHER, "");
            if (!pkg.equals(now)) {
                renderLauncher(getString(R.string.settings_system_not_applied,
                    PropRows.adb(SystemProps.PROP_DEFAULT_LAUNCHER, pkg)));
                return;
            }
            renderLauncher(null);
            if (toOriginal) {
                LauncherPrefs.setAskDefaultLauncher(this, false);
                openOriginalLauncher();
            }
        }, PROP_CHECK_MS);
    }

    private void openOriginalLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setPackage(LauncherPrefs.ORIGINAL_LAUNCHER);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (Exception e) {
            renderLauncher(getString(R.string.settings_original_not_found));
        }
    }

    private boolean nwd() {
        return NwdSettings.isInstalled(this);
    }

    private Integer mcu(String key) {
        return NwdSettings.has(this, key) ? NwdSettings.getInt(this, key, 0) : null;
    }

    private boolean rememberVolume() {
        return "1".equals(SystemProps.get(PROP_MEMORY_VOLUME, "").trim());
    }

    private void buildDevice() {
        list.section(getString(R.string.settings_device_section));
        launcherRow = list.plain(getString(R.string.settings_default_launcher), null);
        useGaragem = launcherRow.addButton(getString(R.string.settings_use_garagem), () -> setDefaultLauncher(getPackageName()));
        useOriginal = launcherRow.addButton(getString(R.string.settings_use_original), () -> setDefaultLauncher(LauncherPrefs.ORIGINAL_LAUNCHER));
        list.toggle(getString(R.string.settings_beep), getString(R.string.settings_beep_hint),
            () -> NwdSettings.systemBit(this, NwdSettings.BIT_BEEP),
            on -> {
                NwdSettings.setSystemFeature(NwdSettings.FEATURE_BEEP, on ? 1 : 0);
                NwdSettings.putInt(this, KEY_BEEP, on ? 1 : 0);
                sendBroadcast(new Intent(ACTION_BEEP_CHANGED));
            }).visibleWhen(this::nwd);
        list.stepper(getString(R.string.settings_startup_volume), getString(R.string.settings_startup_volume_hint),
            () -> 0, () -> NwdSettings.getInt(this, KEY_MAX_VOLUME, 40),
            () -> mcu(KEY_DEFAULT_VOLUME), NwdSettings::setDefaultVolume, String::valueOf)
            .visibleWhen(this::nwd)
            .enabledWhen(() -> !rememberVolume());
        PropRows.toggle(this, list, getString(R.string.settings_remember_volume),
            getString(R.string.settings_remember_volume_hint),
            PROP_MEMORY_VOLUME, "1", "0", false);
    }

    // ------------------------------------------------------------------ display

    private boolean isNight() {
        Boolean dimmer = NwdSettings.systemBit(this, NwdSettings.BIT_ILL_DIMMER);
        return dimmer != null && dimmer && (NwdSettings.getInt(this, KEY_NO_SOURCE_DEVICE, 0) & 2) != 0;
    }

    private void buildScreen() {
        list.section(getString(R.string.settings_screen_section));
        brightness = list.stepper(getString(R.string.settings_brightness), null,
            () -> 0, () -> NwdSettings.getInt(this, KEY_BACKLIGHT_MAX, 10),
            () -> mcu(isNight() ? KEY_BACKLIGHT_NIGHT : KEY_BACKLIGHT_DAY),
            NwdSettings::setDayBackLight, String::valueOf)
            .visibleWhen(this::nwd);
        list.toggle(getString(R.string.settings_dim_headlights), getString(R.string.settings_dim_headlights_hint),
            () -> NwdSettings.systemBit(this, NwdSettings.BIT_ILL_DIMMER),
            on -> NwdSettings.setSystemFeature(NwdSettings.FEATURE_ILL_DIMMER, on ? 1 : 0))
            .visibleWhen(this::nwd);
        list.choice(getString(R.string.settings_screen_off), getString(R.string.settings_screen_off_hint),
            new String[]{getString(R.string.settings_screen_off_never), "30 s", "1 min", "2 min", "5 min", "10 min"},
            new int[]{0, 30, 60, 120, 300, 600},
            () -> mcu(KEY_SCREENSAVER),
            seconds -> {
                NwdSettings.putInt(this, KEY_SCREENSAVER, seconds);
                Intent i = new Intent(ACTION_SCREENSAVER);
                i.putExtra(EXTRA_SCREENSAVER, seconds);
                sendBroadcast(i);
            });
    }

    private void renderBrightnessMode() {
        if (brightness == null) return;
        brightness.setSubtitle(isNight() ? getString(R.string.settings_brightness_night) : getString(R.string.settings_brightness_day));
    }

    // ------------------------------------------------------------------ reverse

    private void buildReverse() {
        list.section(getString(R.string.settings_reverse_section));
        list.toggle(getString(R.string.settings_reverse_mute), getString(R.string.settings_reverse_mute_hint),
            () -> {
                Boolean keep = NwdSettings.systemBit(this, NwdSettings.BIT_REVERSE_KEEP_SOUND);
                return keep == null ? null : !keep;
            },
            // Inverted in the service: 0 mutes, 1 keeps the sound.
            on -> NwdSettings.setSystemFeature(NwdSettings.FEATURE_REVERSE_KEEP_SOUND, on ? 0 : 1))
            .visibleWhen(this::nwd);
        list.toggle(getString(R.string.settings_reverse_mirror),
            getString(R.string.settings_reverse_mirror_hint),
            () -> NwdSettings.systemBit(this, NwdSettings.BIT_MIRROR_REVERSE),
            on -> NwdSettings.setSystemFeature(NwdSettings.FEATURE_MIRROR_REVERSE, on ? 1 : 0))
            .visibleWhen(this::nwd);
    }

    // ------------------------------------------------------------------ navigation

    private void buildNavigation() {
        list.section(getString(R.string.settings_nav_section));
        OptionList.Row app = list.plain(getString(R.string.settings_nav_app), getString(R.string.settings_nav_app_hint));
        naviButton = app.addButton(getString(R.string.settings_choose), this::pickNavigationApp);
        list.toggle(getString(R.string.settings_nav_autostart), null,
            () -> NwdSettings.getInt(this, KEY_NAVI_AUTOSTART, 0) == 1,
            on -> NwdSettings.putInt(this, KEY_NAVI_AUTOSTART, on ? 1 : 0));
        list.toggle(getString(R.string.settings_nav_ducking), getString(R.string.settings_nav_ducking_hint),
            () -> {
                Integer way = mcu(KEY_GPS_WAY);
                return way == null ? null : way == 1;
            },
            on -> NwdSettings.setGpsPromptVolumeWay(on ? 1 : 2)).visibleWhen(this::nwd);
        list.stepper(getString(R.string.settings_duck_amount), getString(R.string.settings_duck_amount_hint), () -> 0, () -> 10,
            () -> mcu(KEY_VOLUME_MIX), NwdSettings::setMixedRatio, String::valueOf)
            .visibleWhen(this::nwd)
            .enabledWhen(() -> {
                Integer way = mcu(KEY_GPS_WAY);
                return way != null && way == 1;
            });
    }

    private void renderNavigation() {
        String name = NwdSettings.getString(this, KEY_NAVI_NAME);
        String pkg = NwdSettings.getString(this, KEY_NAVI_PACKAGE);
        boolean set = pkg != null && !pkg.isEmpty() && ExternalApps.installed(this, pkg);
        naviButton.setText(!set ? getString(R.string.settings_choose) : name != null && !name.isEmpty() ? name : pkg);
        naviButton.setActivated(set);
    }

    private void pickNavigationApp() {
        int iconPx = Math.round(Ui.dp(this, 44));
        new Thread(() -> {
            List<AppCatalog.Entry> apps = AppCatalog.load(getApplicationContext(), iconPx);
            handler.post(() -> {
                if (isFinishing()) {
                    AppCatalog.recycle(apps);
                    return;
                }
                String current = NwdSettings.getString(this, KEY_NAVI_PACKAGE);
                String[] labels = new String[apps.size()];
                Bitmap[] icons = new Bitmap[apps.size()];
                int selected = -1;
                for (int i = 0; i < apps.size(); i++) {
                    labels[i] = apps.get(i).label;
                    icons[i] = apps.get(i).icon;
                    if (apps.get(i).packageName.equals(current)) selected = i;
                }
                list.showChoices(getString(R.string.settings_nav_app), labels, icons, selected, i -> {
                    AppCatalog.Entry e = apps.get(i);
                    NwdSettings.putString(this, KEY_NAVI_PACKAGE, e.packageName);
                    NwdSettings.putString(this, KEY_NAVI_CLASS, e.className);
                    NwdSettings.putString(this, KEY_NAVI_NAME, e.label);
                    renderNavigation();
                });
            });
        }, "nav-apps").start();
    }

    // ------------------------------------------------------------------ about

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
            // Technical details: developer mode only.
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
        list.info("Garagem", this::appVersion);
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

    // ------------------------------------------------------------------ advanced

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

    // ------------------------------------------------------------------ last error

    /** If the app crashed, shows the cause (to photograph or copy and send). */
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

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onDestroy() {
        getContentResolver().unregisterContentObserver(observer);
        handler.removeCallbacksAndMessages(null);
        list.release();
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
        // Props don't notify changes: re-read on return (e.g. from Advanced or the factory menu).
        renderAll();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(getWindow());
    }
}
