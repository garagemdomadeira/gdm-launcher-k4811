package com.k4811.launcher;






import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.k4811.common.Apps;
import com.k4811.common.bt.BtClient;
import com.k4811.common.home.GpsClient;
import com.k4811.common.home.HomeGrid;
import com.k4811.common.home.HomeImage;
import com.k4811.launcher.home.HomeModuleViews;
import com.k4811.launcher.home.HomeSession;
import com.k4811.common.config.LauncherConfig;
import com.k4811.common.config.NwdConfigs;
import com.k4811.common.system.BottomBar;
import com.k4811.launcher.home.MuteClient;
import com.k4811.common.audio.PlayerHandoff;
import com.k4811.launcher.home.VolumeClient;
import com.k4811.common.music.AlbumArt;
import com.k4811.common.radio.Band;
import com.k4811.common.radio.RadioClient;
import com.k4811.common.radio.RadioState;
import com.k4811.common.radio.StationArt;
import com.k4811.common.system.NwdSettings;
import com.k4811.common.system.SystemProps;
import com.k4811.common.ui.IconView;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;
import com.k4811.common.ui.DockView;
import com.k4811.common.ui.DockNavigator;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen: top bar (voltmeter, screen off, settings), the modules the driver placed in the
 * grid (clock, volume and mute, "now playing" for radio, USB/SD or Bluetooth music, fan and
 * Bluetooth status, a picture, GPS speed and GPS data; see {@link HomeModuleViews}) and the dock.
 * A call covers only the "now playing" slot. Opening this screen neither turns the radio on nor
 * cuts what is playing.
 */
public class HomeActivity extends Activity {

    private static final int SOURCE_ANDROID = 0;
    private static final int SOURCE_RADIO = 4;

    private static final String KEY_FAN_SWITCH = "mcu_smart_fan_switch";
    // Screen backlight ("screen off" button).
    private static final String ACTION_SET_BACKLIGHT = "com.nwd.action.ACTION_SET_BACKLIGHT_SWITCH";
    private static final String EXTRA_BACKLIGHT = "extra_backlight_switch";
    private static final String KEY_BACKLIGHT_STATE = "mcu_back_light_state";
    private static final String[] BT_ACTIONS = {
        "com.bt.ACTION_BT_HFP_ESTABLISHED", "com.bt.ACTION_BT_HFP_RELEASE",
        "com.bt.ACTION_BT_CONNECTION_CHANGE", "com.bt.ACTION_A2DP_ESTABLISHED",
        "com.bt.ACTION_A2DP_RELEASE",
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
    // Created in onCreate, after the app language has been applied (Locale.getDefault()).
    private SimpleDateFormat weekFormat;
    private SimpleDateFormat monthFormat;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            renderClock();
            long delay = 1000L - (System.currentTimeMillis() % 1000L);
            handler.postDelayed(this, Math.max(delay, 16L));
        }
    };

    private HomeSession session;
    private VolumeClient volume;
    private MuteClient mute;
    private BtClient bt;
    private RadioClient radio;
    private RadioState radioState = new RadioState();
    private HomeSession.Snapshot snap;
    private boolean radioReady;
    private Dialog dialog;

    private int volumeValue;
    private boolean muted;
    private boolean fanOn;
    private boolean btHfp;
    private String btDevice = "";

    private View screenOffOverlay;
    private DockView dock;
    private HomeModuleViews modules;
    private GpsClient gps;
    private TextView speedValue;
    private TextView speedUnit;
    private TextView gpsLat;
    private TextView gpsLon;
    private TextView gpsAlt;
    private TextView gpsDir;
    private IconView muteButton;
    private TextView clockTime;
    private TextView clockDate;
    private View fanDot;
    private TextView fanText;
    private TextView npLabel;
    private TextView npLine1;
    private TextView npLine2;
    private View npControls;
    private View npProgressTrack;
    private View npProgress;
    private IconView npPrev;
    private IconView npPlay;
    private IconView npNext;
    private ImageView npArt;
    private IconView npArtIcon;
    /** Track whose cover is on screen (or loading); null = source icon. */
    private String artKey;
    private int artGen;
    private View voltageView;
    private TextView voltageText;
    private View sourceBadge;
    private IconView dayNight;
    private TextView sourceBadgeText;
    private final ExecutorService artLoader = Executors.newSingleThreadExecutor();
    /** Image module: picture version (path, date and size) and slot on screen, to reload only on change. */
    private long imageStamp = -1;
    private HomeGrid.Slot imageSlot;
    /** Background picture version (path, date, size), to reload only on change. */
    private long bgStamp = -1;
    private View cardCall;
    private TextView callState;
    private TextView callName;
    private TextView callNumber;
    private TextView callAnswer;
    private IconView btIcon;
    private TextView btText;
    private TextView btDeviceView;
    private final ContentObserver fanObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            readFan();
        }
    };

    /** If the system turns the backlight back on by itself (key, reverse, etc.), removes the black layer. */
    private final ContentObserver backlightObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            if (screenOffOverlay != null && screenOffOverlay.getVisibility() == View.VISIBLE) {
                screenOffOverlay.setVisibility(View.GONE);
                scheduleAutoScreenOff();
            }
        }
    };

    /** Headlights (MCU ILL signal) switched: sun ⇄ moon. */
    private final ContentObserver headlightObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            renderDayNight();
        }
    };

    /** Settings › "Turn screen off after": same action as the screen-off button, after inactivity. */
    private final Runnable autoScreenOff = () -> {
        if (screenOffOverlay != null && screenOffOverlay.getVisibility() != View.VISIBLE) setScreenOn(false);
    };

    /**
     * Battery voltage via CAN (as in the NWD themes): asks every 10 s and the head unit
     * replies with volts × 10. Without a reply (car without CAN), the indicator stays hidden.
     */
    private static final String ACTION_QUERY_VOLTAGE = "com.nwd.action.ACTION_QUERY_CAN_BATTERY_VOLTAGE";
    private static final String ACTION_VOLTAGE = "com.nwd.action.ACTION_CAN_BATTERY_VOLTAGE";
    private static final String EXTRA_VOLTAGE = "extra_voltage_value";
    private static final long VOLTAGE_PERIOD_MS = 10000;

    private final Runnable voltageTick = new Runnable() {
        @Override
        public void run() {
            try {
                sendBroadcast(new Intent(ACTION_QUERY_VOLTAGE));
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, VOLTAGE_PERIOD_MS);
        }
    };

    private final BroadcastReceiver voltageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!LauncherConfig.showVoltmeter(HomeActivity.this)) return;
            int raw = intent.getIntExtra(EXTRA_VOLTAGE, 0);
            float volts = Math.abs(raw) / 10f;
            if (volts <= 0f) return; // reply without a value: keep the previous one
            volts = Math.max(8f, Math.min(18f, volts));
            voltageText.setText(String.format(Locale.getDefault(), "%.1f V", volts));
            if (voltageView.getVisibility() != View.VISIBLE) {
                voltageView.setVisibility(View.VISIBLE);
                Touch.fadeIn(voltageView);
            }
        }
    };

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Connection changed: ask the Bluetooth service for the state and the device name.
            handler.removeCallbacks(btQuery);
            handler.postDelayed(btQuery, 300);
        }
    };

    private final Runnable btQuery = this::queryBt;
    /** Idioma, tema e fonte quando a tela foi criada (ver lookKey). */
    private static final String TAG = "K4811Home";
    private String appliedLook;
    /**
     * launcher.json wasn't there when the screen opened: at power-on the launcher starts before the
     * sdcard is mounted, so it came up with the defaults. Until the file shows up we watch for the
     * mount (and poll, in case the broadcast doesn't come) and then rebuild with the settings.
     */
    private boolean waitingForConfig;
    private int configPolls;
    private static final long CONFIG_POLL_MS = 2000;
    private static final int CONFIG_MAX_POLLS = 60; // 2 min
    private final Runnable configPoll = new Runnable() {
        @Override
        public void run() {
            if (!waitingForConfig) return;
            if (configReadable()) {
                onConfigAvailable();
            } else if (++configPolls < CONFIG_MAX_POLLS) {
                handler.postDelayed(this, CONFIG_POLL_MS);
            } else {
                stopWaitingForConfig();
            }
        }
    };
    private final BroadcastReceiver mountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (waitingForConfig && configReadable()) onConfigAvailable();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        if (!configReadable()) startWaitingForConfig();
        createDateFormats();
        setContentView(R.layout.activity_home);
        appliedLook = lookKey();
        Ui.applyFont(findViewById(R.id.home_root));
        // On the home screen the dock always highlights Home; what is playing shows in the top bar.
        dock = DockNavigator.attachDock(this, DockView.Item.HOME);
        bindViews();
        modules = new HomeModuleViews(this);
        modules.apply(LauncherConfig.homeLayout(this));
        gps = new GpsClient(this);
        gps.setListener(this::renderGps);
        // Portrait grid: sized to the area the home screen really got (and if it changes).
        findViewById(android.R.id.content).addOnLayoutChangeListener(
            (v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l == or - ol && b - t == ob - ot) return;
                v.post(() -> {
                    modules.relayout();
                    if (snap != null) render(snap);
                });
            });
        // The "screen off" layer must cover the dock too.
        ViewGroup content = (ViewGroup) findViewById(android.R.id.content);
        ((ViewGroup) screenOffOverlay.getParent()).removeView(screenOffOverlay);
        content.addView(screenOffOverlay, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        renderClock();

        session = new HomeSession(this);
        session.setListener(this::render);
        session.start();

        volume = new VolumeClient(this);
        volume.setListener((v, max) -> {
            volumeValue = v;
            renderVolume();
        });
        volume.connect();

        mute = new MuteClient(this);
        mute.setListener(m -> {
            muted = m;
            renderVolume();
        });
        mute.start();

        bt = new BtClient(this);
        bt.connect();
        IntentFilter btFilter = new IntentFilter();
        for (String a : BT_ACTIONS) btFilter.addAction(a);
        registerReceiver(btReceiver, btFilter);
        registerReceiver(voltageReceiver, new IntentFilter(ACTION_VOLTAGE));

        getContentResolver().registerContentObserver(
            Settings.System.getUriFor(KEY_FAN_SWITCH), false, fanObserver);
        getContentResolver().registerContentObserver(
            Settings.System.getUriFor(KEY_BACKLIGHT_STATE), false, backlightObserver);
        getContentResolver().registerContentObserver(
            Settings.System.getUriFor(NwdSettings.KEY_NO_SOURCE_DEVICE), false, headlightObserver);
        readFan();

        // Only observes. connect(false) doesn't send ACTION_MEDIA_PLAY.
        radio = new RadioClient(this);
        radio.setListener(state -> {
            radioState = state;
            if (state.connection == RadioState.Connection.CONNECTED) radioReady = true;
            if (snap != null) renderNowPlaying();
        });
        radio.connect(false);

        handler.postDelayed(this::checkDefaultLauncher, 800);
        Touch.install(findViewById(android.R.id.content));
    }

    private void bindViews() {
        icon(R.id.volume_down, IconView.Icon.MINUS, IconView.Style.NONE).setOnClickListener(v -> stepVolume(-1));
        icon(R.id.volume_up, IconView.Icon.PLUS, IconView.Style.NONE).setOnClickListener(v -> stepVolume(1));
        muteButton = icon(R.id.volume_mute, IconView.Icon.VOLUME, IconView.Style.NONE);
        muteButton.setOnClickListener(v -> {
            if (mute != null) mute.toggle();
        });

        icon(R.id.settings_button, IconView.Icon.SETTINGS, IconView.Style.NONE)
            .setOnClickListener(v -> openApp(Apps.SETTINGS));
        icon(R.id.screen_off_button, IconView.Icon.SCREEN_OFF, IconView.Style.NONE)
            .setOnClickListener(v -> setScreenOn(false));
        screenOffOverlay = findViewById(R.id.screen_off_overlay);
        screenOffOverlay.setOnClickListener(v -> setScreenOn(true));

        clockTime = text(R.id.clock_time);
        clockDate = text(R.id.clock_date);
        clockTime.setTypeface(Ui.semiBold(this));
        clockDate.setTypeface(Ui.regular(this));
        View clock = findViewById(R.id.clock_block);
        clock.setOnClickListener(v -> openDateSettings());

        // Emergency exit: long-pressing the black area opens Android Settings.
        findViewById(R.id.home_space).setOnLongClickListener(v -> {
            openAndroidSettings();
            return true;
        });

        fanDot = findViewById(R.id.fan_status_dot);
        fanText = text(R.id.fan_status_text);
        text(R.id.fan_status_title).setTypeface(Ui.medium(this));
        fanText.setTypeface(Ui.medium(this));
        IconView fanIcon = icon(R.id.fan_status_icon, IconView.Icon.FAN, IconView.Style.NONE);
        fanIcon.setClickable(false);
        fanIcon.color(Ui.TEXT_MUTED);
        findViewById(R.id.fan_status).setOnClickListener(v -> openApp(Apps.FAN));

        npLabel = text(R.id.np_label);
        npLine1 = text(R.id.np_line1);
        npLine2 = text(R.id.np_line2);
        npLabel.setTypeface(Ui.medium(this));
        npLine1.setTypeface(Ui.medium(this));
        npLine2.setTypeface(Ui.regular(this));
        npControls = findViewById(R.id.np_controls);
        npProgressTrack = findViewById(R.id.np_progress_track);
        npProgress = findViewById(R.id.np_progress);
        npPrev = icon(R.id.np_prev, IconView.Icon.SKIP_PREV, IconView.Style.NONE);
        npPlay = icon(R.id.np_play, IconView.Icon.PLAY, IconView.Style.NONE);
        npNext = icon(R.id.np_next, IconView.Icon.SKIP_NEXT, IconView.Style.NONE);
        npPrev.setOnClickListener(v -> transport(-1));
        npPlay.setOnClickListener(v -> transport(0));
        npNext.setOnClickListener(v -> transport(1));
        voltageView = findViewById(R.id.voltage);
        voltageText = text(R.id.voltage_text);
        voltageText.setTypeface(Ui.medium(this));
        icon(R.id.voltage_icon, IconView.Icon.BATTERY, IconView.Style.NONE).color(Ui.TEXT_MUTED);
        dayNight = (IconView) findViewById(R.id.day_night);
        dayNight.setClickable(false);
        sourceBadge = findViewById(R.id.source_badge);
        sourceBadge.setActivated(true);
        sourceBadgeText = text(R.id.source_badge_text);
        sourceBadgeText.setTypeface(Ui.medium(this));
        npArt = (ImageView) findViewById(R.id.np_art);
        npArtIcon = (IconView) findViewById(R.id.np_art_icon);
        npArtIcon.color(Ui.TEXT_DIM);
        findViewById(R.id.now_playing).setOnClickListener(v -> openNowPlaying());

        cardCall = findViewById(R.id.card_call);
        cardCall.setOnClickListener(v -> openDock(DockView.Item.PHONE));
        callState = text(R.id.call_state);
        callName = text(R.id.call_name);
        callNumber = text(R.id.call_number);
        callName.setTypeface(Ui.semiBold(this));
        callAnswer = text(R.id.call_answer);
        callAnswer.setActivated(true);
        callAnswer.setOnClickListener(v -> session.answerCall());
        text(R.id.call_hangup).setOnClickListener(v -> session.hangupCall());

        btIcon = icon(R.id.bt_status_icon, IconView.Icon.BLUETOOTH, IconView.Style.NONE);
        btIcon.setClickable(false);
        btText = text(R.id.bt_status_text);
        btDeviceView = text(R.id.bt_status_device);
        btText.setTypeface(Ui.medium(this));
        btDeviceView.setTypeface(Ui.regular(this));

        speedValue = text(R.id.speed_value);
        speedUnit = text(R.id.speed_unit);
        speedValue.setTypeface(Ui.semiBold(this));
        speedUnit.setTypeface(Ui.regular(this));
        gpsLat = text(R.id.gps_lat_value);
        gpsLon = text(R.id.gps_lon_value);
        gpsAlt = text(R.id.gps_alt_value);
        gpsDir = text(R.id.gps_dir_value);
        for (int id : new int[]{R.id.gps_lat_value, R.id.gps_lon_value, R.id.gps_alt_value, R.id.gps_dir_value}) {
            text(id).setTypeface(Ui.medium(this));
        }
        findViewById(R.id.bt_status).setOnClickListener(v -> openDock(DockView.Item.PHONE));

        renderVolume();
        renderFan();
        renderBt();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!lookKey().equals(appliedLook)) {
            recreate();
            return;
        }
        Ui.hideSystemBars(getWindow());
        renderClock();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        if (session != null) session.refresh();
        if (volume != null) volume.refresh();
        if (mute != null) mute.refresh();
        readFan();
        queryBt();
        applyPrefs();
    }

    /** Settings screen options: home modules and voltmeter. */
    private void applyPrefs() {
        modules.apply(LauncherConfig.homeLayout(this));
        if (snap != null) render(snap);
        renderImage();
        renderBackground();
        updateGps();
        handler.removeCallbacks(voltageTick);
        if (LauncherConfig.showVoltmeter(this)) {
            handler.post(voltageTick);
        } else {
            voltageView.setVisibility(View.GONE);
        }
        renderDayNight();
        scheduleAutoScreenOff();
    }

    @Override
    protected void onPause() {
        // Leaving the home screen with the backlight off (another app, a call): turn it back on.
        if (screenOffOverlay != null && screenOffOverlay.getVisibility() == View.VISIBLE) setScreenOn(true);
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(voltageTick);
        handler.removeCallbacks(autoScreenOff);
        gps.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopWaitingForConfig();
        handler.removeCallbacksAndMessages(null);
        gps.stop();
        gps.setListener(null);
        try {
            unregisterReceiver(btReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(voltageReceiver);
        } catch (Exception ignored) {
        }
        getContentResolver().unregisterContentObserver(fanObserver);
        getContentResolver().unregisterContentObserver(backlightObserver);
        getContentResolver().unregisterContentObserver(headlightObserver);
        if (volume != null) {
            volume.setListener(null);
            volume.release();
            volume = null;
        }
        if (mute != null) {
            mute.setListener(null);
            mute.stop();
        }
        if (bt != null) bt.release();
        if (session != null) {
            session.setListener(null);
            session.stop();
        }
        if (radio != null) {
            radio.setListener(null);
            radio.release();
        }
        if (dialog != null) dialog.dismiss();
        artLoader.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Ui.applyDesignDensity(this);
        createDateFormats();
    }

    /** Weekday and month names follow the app language (applied by Ui.applyDesignDensity). */
    private void createDateFormats() {
        weekFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        monthFormat = new SimpleDateFormat(getString(R.string.home_month_pattern), Locale.getDefault());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(getWindow());
    }

    /** Home screen: Back doesn't close anything. */
    @Override
    public void onBackPressed() {
    }

    // ------------------------------------------------------------------ rendering

    private void render(HomeSession.Snapshot s) {
        snap = s;
        boolean calling = s.call != HomeSession.CallPhase.NONE;
        show(cardCall, calling);
        if (modules.slotOf(HomeGrid.Module.NOW_PLAYING) != null) {
            findViewById(R.id.now_playing).setVisibility(calling ? View.INVISIBLE : View.VISIBLE);
        }
        renderNowPlaying();
        renderSourceBadge(s);
        renderCall(s);
        renderBt();
    }

    private void renderClock() {
        Date now = new Date();
        clockTime.setText(timeFormat.format(now));
        // "Friday, October 9, 2026" / "Sexta, 9 de Outubro de 2026": weekday without "-feira"
        // and capitalized month (where the language does that); the order comes from home_date.
        String week = weekFormat.format(now);
        int dash = week.indexOf('-');
        if (dash > 0) week = week.substring(0, dash);
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        String month = monthFormat.format(now);
        if (getResources().getBoolean(R.bool.home_capitalize_month)) month = capitalize(month);
        clockDate.setText(getString(R.string.home_date, capitalize(week), month,
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.YEAR)));
    }

    private void stepVolume(int delta) {
        if (muted && mute != null) mute.setMuted(false); // changing the volume unmutes
        if (volume != null) volume.nudge(delta);
    }

    private void renderVolume() {
        if (muteButton == null) return;
        muteButton.set(muted ? IconView.Icon.VOLUME_MUTE : IconView.Icon.VOLUME, IconView.Style.NONE);
        muteButton.color(muted ? Ui.TEXT : Ui.TEXT_SOFT);
        muteButton.setContentDescription(getString(
            muted ? R.string.home_unmute_volume : R.string.home_mute_volume, volumeValue));
    }

    /** Top bar, next to the voltmeter: what is playing right now (Radio, MP3 or BT). */
    private void renderSourceBadge(HomeSession.Snapshot s) {
        String label = null;
        if (s.source == SOURCE_RADIO) label = getString(R.string.home_radio);
        else if (s.kind == HomeSession.Kind.USB && s.playing) label = "MP3";
        else if (s.kind == HomeSession.Kind.BT && s.btPlaying) label = "BT";
        if (label != null) sourceBadgeText.setText(label);
        show(sourceBadge, label != null);
    }

    /** "Now playing": radio, USB/SD or Bluetooth music, depending on the current source. */
    private void renderNowPlaying() {
        if (snap == null) return;
        HomeSession.Snapshot s = snap;
        boolean controls = true;
        float progress = -1f; // < 0 hides the bar
        String sub = "";
        switch (s.kind) {
            case RADIO: {
                boolean live = s.source == SOURCE_RADIO;
                npLabel.setText(R.string.home_now_playing_radio);
                if (!radioReady) {
                    npLine1.setText(radioState.connection == RadioState.Connection.NOT_FOUND
                        ? R.string.home_radio_unavailable : R.string.home_radio);
                } else {
                    boolean fm = Band.isFm(radioState.band);
                    String freq = Band.formatValue(radioState.frequency, fm) + " " + Band.unit(fm);
                    boolean named = radioState.psName != null && !radioState.psName.isEmpty();
                    npLine1.setText(named ? radioState.psName : freq);
                    sub = Band.label(radioState.band) + (named ? "  ·  " + freq : "");
                }
                // On the radio the center button toggles on/off.
                npPlay.set(IconView.Icon.POWER, IconView.Style.NONE);
                npPlay.color(live ? Ui.TEXT : Ui.TEXT_MUTED);
                npPlay.setContentDescription(getString(live ? R.string.home_radio_turn_off : R.string.home_radio_turn_on));
                break;
            }
            case USB:
                npLabel.setText(R.string.home_now_playing_mp3);
                npLine1.setText(s.title.isEmpty() ? getString(R.string.home_music) : s.title);
                sub = s.artist.isEmpty() ? "USB / SD" : s.artist;
                progress = s.durationMs > 0 ? Math.min(1f, s.positionMs / (float) s.durationMs) : 0f;
                setPlay(s.playing);
                break;
            case BT: {
                npLabel.setText(R.string.home_now_playing_bt);
                boolean named = !s.btTitle.isEmpty();
                if (s.a2dpKnown && !s.a2dpConnected && !named) {
                    npLine1.setText(R.string.home_phone_disconnected);
                } else {
                    npLine1.setText(named ? s.btTitle : getString(R.string.home_bt_music));
                    sub = s.btArtist;
                }
                setPlay(s.btPlaying);
                break;
            }
            default:
                npLabel.setText(""); // nothing playing: no source to show
                npLine1.setText(R.string.home_nothing_playing);
                controls = false;
                progress = 0f; // empty bar, as in the design
                break;
        }
        renderArt(s);
        npLine2.setText(sub);
        npLine2.setVisibility(sub.isEmpty() || !modules.nowPlayingLine2() ? View.GONE : View.VISIBLE);
        npControls.setVisibility(controls ? View.VISIBLE : View.GONE);
        npProgressTrack.setVisibility(progress < 0 || !modules.nowPlayingProgress() ? View.GONE : View.VISIBLE);
        if (progress >= 0) {
            npProgress.setPivotX(0f);
            npProgress.setScaleX(progress);
        }
    }

    /**
     * Track cover for MP3, station art for the radio (K4811/radio/FM_104.5.jpg, as on the radio
     * screen); otherwise, or without a picture, the source icon in the frame.
     */
    private void renderArt(HomeSession.Snapshot s) {
        IconView.Icon icon = s.kind == HomeSession.Kind.RADIO ? IconView.Icon.RADIO
            : s.kind == HomeSession.Kind.BT ? IconView.Icon.BLUETOOTH : IconView.Icon.MUSIC;
        npArtIcon.set(icon, IconView.Style.NONE);
        npArtIcon.color(Ui.TEXT_DIM);

        long id = 0;
        if (s.kind == HomeSession.Kind.USB && s.mediaId != null) {
            try {
                id = Long.parseLong(s.mediaId.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String path = s.kind == HomeSession.Kind.USB ? s.path : null;
        boolean station = s.kind == HomeSession.Kind.RADIO && radioReady && radioState.frequency > 0;
        int band = radioState.band;
        int frequency = radioState.frequency;
        String key = station ? "radio:" + StationArt.baseName(band, frequency)
            : id > 0 ? "id:" + id : path != null ? "path:" + path : null;
        if (key == null) {
            clearArt();
            return;
        }
        if (key.equals(artKey)) return;
        clearArt();
        artKey = key;
        int gen = ++artGen;
        long mediaId = id;
        int size = npArt.getWidth() > 0 ? npArt.getWidth() : Math.round(Ui.dp(this, 216));
        try {
            artLoader.execute(() -> {
                Bitmap bmp = station ? StationArt.load(band, frequency, size)
                    : AlbumArt.decode(getApplicationContext(), mediaId, path);
                handler.post(() -> {
                    if (isFinishing() || gen != artGen || bmp == null) return;
                    npArt.setImageBitmap(bmp);
                    npArt.setVisibility(View.VISIBLE);
                    npArtIcon.setVisibility(View.GONE);
                    Touch.fadeIn(npArt);
                });
            });
        } catch (Exception ignored) {
            // Executor already shut down (screen leaving).
        }
    }

    /** Image module: (re)loads the chosen picture when it changed or the module moved to another slot. */
    private void renderImage() {
        HomeGrid.Slot slot = modules.slotOf(HomeGrid.Module.IMAGE);
        if (slot == null) return;
        File f = HomeImage.file(this);
        long stamp = f != null && f.isFile()
            ? (f.getPath().hashCode() * 31L + f.lastModified()) * 31 + f.length() : 0;
        if (stamp == imageStamp && slot == imageSlot) return;
        imageStamp = stamp;
        imageSlot = slot;
        ImageView view = (ImageView) findViewById(R.id.image_view);
        View empty = findViewById(R.id.image_empty);
        if (stamp == 0) {
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        HomeGrid.Frame frame = modules.frame(slot);
        int width = Math.round(Ui.dp(this, frame.width));
        int height = Math.round(Ui.dp(this, frame.height));
        try {
            artLoader.execute(() -> {
                Bitmap bmp = HomeImage.load(f, width, height);
                handler.post(() -> {
                    if (isFinishing() || stamp != imageStamp) return;
                    view.setImageBitmap(bmp);
                    view.setVisibility(bmp != null ? View.VISIBLE : View.GONE);
                    empty.setVisibility(bmp != null ? View.GONE : View.VISIBLE);
                    if (bmp != null) Touch.fadeIn(view);
                });
            });
        } catch (Exception ignored) {
            // Executor already shut down (screen leaving).
        }
    }

    /** Optional home background: (re)loads the chosen picture when it changed; hidden when none or missing. */
    private void renderBackground() {
        ImageView view = (ImageView) findViewById(R.id.home_bg);
        File f = HomeImage.fileBg(this);
        long stamp = f != null && f.isFile()
            ? (f.getPath().hashCode() * 31L + f.lastModified()) * 31 + f.length() : 0;
        if (stamp == bgStamp) return;
        bgStamp = stamp;
        if (stamp == 0) {
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
            return;
        }
        View root = findViewById(R.id.home_root);
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int height = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
        try {
            artLoader.execute(() -> {
                Bitmap bmp = HomeImage.load(f, width, height);
                handler.post(() -> {
                    if (isFinishing() || stamp != bgStamp) return;
                    if (bmp != null) {
                        view.setImageBitmap(bmp);
                        view.setVisibility(View.VISIBLE);
                        Touch.fadeIn(view);
                    } else {
                        view.setImageDrawable(null);
                        view.setVisibility(View.GONE);
                    }
                });
            });
        } catch (Exception ignored) {
            // Executor already shut down (screen leaving).
        }
    }

    private void clearArt() {
        artGen++;
        artKey = null;
        npArt.setImageDrawable(null);
        npArt.setVisibility(View.GONE);
        npArtIcon.setVisibility(View.VISIBLE);
    }

    private void setPlay(boolean playing) {
        npPlay.set(playing ? IconView.Icon.PAUSE : IconView.Icon.PLAY, IconView.Style.NONE);
        npPlay.color(Ui.TEXT);
        npPlay.setContentDescription(getString(playing ? R.string.home_pause : R.string.home_play));
    }

    private void renderCall(HomeSession.Snapshot s) {
        if (s.call == HomeSession.CallPhase.NONE) return;
        if (s.call == HomeSession.CallPhase.INCOMING) callState.setText(R.string.home_call_incoming);
        else if (s.call == HomeSession.CallPhase.OUTGOING) callState.setText(R.string.home_call_outgoing);
        else callState.setText(R.string.home_call_active);
        show(callAnswer, s.call == HomeSession.CallPhase.INCOMING);
        boolean hasName = !s.callName.isEmpty();
        boolean hasNumber = !s.callNumber.isEmpty();
        if (hasName) {
            callName.setText(s.callName);
            callNumber.setText(hasNumber ? s.callNumber : "");
        } else if (hasNumber) {
            callName.setText(s.callNumber);
            callNumber.setText("");
        } else {
            callName.setText(R.string.home_unknown_number);
            callNumber.setText("");
        }
    }

    // ------------------------------------------------------------------ GPS

    /** GPS only runs while the home screen is visible and a GPS module is on the grid. */
    private void updateGps() {
        boolean needed = modules.slotOf(HomeGrid.Module.SPEED) != null || modules.slotOf(HomeGrid.Module.GPS) != null;
        if (!needed) {
            gps.stop();
            return;
        }
        renderGps(null); // until the first fix (start() delivers a recent one at once)
        gps.start();
        if (!gps.available()) renderGps(null);
    }

    /** Speed and GPS data modules. {@code fix} null = no recent position (searching, tunnel, no GPS). */
    private void renderGps(GpsClient.Fix fix) {
        String state = getString(gps.available() ? R.string.home_gps_waiting : R.string.home_gps_unavailable);
        String none = getString(R.string.home_gps_none);
        if (fix == null || Float.isNaN(fix.speedKmh)) {
            speedValue.setText(none);
            speedUnit.setText(fix == null ? state : getString(R.string.home_speed_unit));
        } else {
            // Below walking pace it's GPS jitter: show 0.
            speedValue.setText(String.valueOf(fix.moving() ? Math.round(fix.speedKmh) : 0));
            speedUnit.setText(R.string.home_speed_unit);
        }
        if (fix == null) {
            gpsLat.setText(none);
            gpsLon.setText(none);
            gpsAlt.setText(none);
            gpsDir.setText(state);
            return;
        }
        gpsLat.setText(coordinate(fix.latitude, R.string.home_gps_dir_n, R.string.home_gps_dir_s));
        gpsLon.setText(coordinate(fix.longitude, R.string.home_gps_dir_e, R.string.home_gps_dir_w));
        gpsAlt.setText(Double.isNaN(fix.altitude) ? none
            : getString(R.string.home_gps_altitude_value, Math.round(fix.altitude)));
        gpsDir.setText(Float.isNaN(fix.bearing) ? getString(R.string.home_gps_stopped)
            : getString(R.string.home_gps_heading_value, compass(fix.bearing), Math.round(fix.bearing) % 360));
    }

    /** "23.55052° S": degrees with 5 decimals (about 1 m) and the hemisphere. */
    private String coordinate(double value, int positive, int negative) {
        return String.format(Locale.US, "%.5f° %s", Math.abs(value), getString(value >= 0 ? positive : negative));
    }

    private static final int[] COMPASS = {
        R.string.home_gps_dir_n, R.string.home_gps_dir_ne, R.string.home_gps_dir_e, R.string.home_gps_dir_se,
        R.string.home_gps_dir_s, R.string.home_gps_dir_sw, R.string.home_gps_dir_w, R.string.home_gps_dir_nw,
    };

    /** One of the 8 compass points (45° each, centered on N, NE, E…). */
    private String compass(float bearing) {
        return getString(COMPASS[Math.round(bearing / 45f) % 8]);
    }

    // ------------------------------------------------------------------ fan and Bluetooth

    /** Actual fan state, kept by the head unit system in Settings.System. */
    private void readFan() {
        int v = 0;
        try {
            v = Settings.System.getInt(getContentResolver(), KEY_FAN_SWITCH, 0);
        } catch (Exception ignored) {
        }
        fanOn = v != 0;
        renderFan();
    }

    private void renderFan() {
        if (fanText == null) return;
        fanDot.setActivated(fanOn);
        fanText.setText(fanOn ? R.string.home_fan_on : R.string.home_fan_off);
        fanText.setTextColor(fanOn ? Ui.TEXT : Ui.TEXT_MUTED);
    }

    private void queryBt() {
        if (bt == null) return;
        bt.queryPhoneInfo((hfp, powered, localName, pin, remoteName, autoFeatures, callState) -> {
            btHfp = hfp;
            btDevice = remoteName == null ? "" : remoteName;
            renderBt();
        });
    }

    /** Connected = phone (HFP) or audio (A2DP) linked to the phone. */
    private void renderBt() {
        if (btText == null) return;
        boolean a2dp = snap != null && snap.a2dpKnown && snap.a2dpConnected;
        boolean connected = btHfp || a2dp;
        btText.setText(connected ? R.string.home_bt_connected : R.string.home_bt_disconnected);
        btText.setTextColor(Ui.TEXT);
        btIcon.color(connected ? Ui.TEXT : Ui.TEXT_MUTED);
        btDeviceView.setText(connected ? (btDevice.isEmpty() ? getString(R.string.home_phone) : btDevice)
            : getString(R.string.home_tap_to_connect));
    }

    // ------------------------------------------------------------------ actions

    /**
     * Turns the screen backlight off/on through the NWD system. With the backlight off, touch keeps
     * working: a black layer covers the screen and any touch on it turns the backlight back on.
     */
    private void setScreenOn(boolean on) {
        Intent i = new Intent(ACTION_SET_BACKLIGHT);
        i.putExtra(EXTRA_BACKLIGHT, on);
        sendBroadcast(i);
        android.util.Log.d("K4811Launcher", "→ " + ACTION_SET_BACKLIGHT + " " + EXTRA_BACKLIGHT + "=" + on);
        screenOffOverlay.setVisibility(on ? View.GONE : View.VISIBLE);
        if (!on) screenOffOverlay.bringToFront();
    }

    /** Any touch on the home screen restarts the "turn screen off after" countdown. */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        scheduleAutoScreenOff();
    }

    private void scheduleAutoScreenOff() {
        handler.removeCallbacks(autoScreenOff);
        int seconds = LauncherConfig.screenOffAfter(this);
        if (seconds > 0) handler.postDelayed(autoScreenOff, seconds * 1000L);
    }

    /** Sun by day, moon with the headlights on (Settings › Main can hide it). */
    private void renderDayNight() {
        if (!LauncherConfig.showDayNight(this)) {
            dayNight.setVisibility(View.GONE);
            return;
        }
        boolean night = NwdSettings.headlightsOn(this);
        dayNight.set(night ? IconView.Icon.MOON : IconView.Icon.SUN, IconView.Style.NONE).color(Ui.TEXT_SOFT);
        dayNight.setContentDescription(getString(night ? R.string.home_night : R.string.home_day));
        dayNight.setVisibility(View.VISIBLE);
    }

    /** −1 previous, 0 play/pause (on the radio: on/off), +1 next. */
    private void transport(int what) {
        if (snap == null) return;
        switch (snap.kind) {
            case RADIO:
                if (what == 0) toggleRadio();
                else tuneRadio(what > 0);
                break;
            case USB:
                session.usbTransport(what < 0 ? HomeSession.USB_PREV
                    : what > 0 ? HomeSession.USB_NEXT : HomeSession.USB_TOGGLE);
                break;
            case BT:
                if (what < 0) session.btTransport(HomeSession.BT_PREV);
                else if (what > 0) session.btTransport(HomeSession.BT_NEXT);
                else session.btTransport(snap.btPlaying ? HomeSession.BT_PAUSE : HomeSession.BT_PLAY);
                break;
            default:
                break;
        }
    }

    private void openNowPlaying() {
        if (snap == null) return;
        switch (snap.kind) {
            case RADIO:
                openDock(DockView.Item.RADIO);
                break;
            case USB:
                openDock(DockView.Item.MUSIC);
                break;
            case BT:
                openDock(DockView.Item.BT_MUSIC);
                break;
            default:
                break;
        }
    }

    /** Previous/next memory. If the radio isn't the source, turns it on first — the tap is what turns it on. */
    private void tuneRadio(boolean next) {
        if (snap == null || snap.source != SOURCE_RADIO) {
            PlayerHandoff.switchToRadio(this, radio::activateSource);
            session.assumeSource(SOURCE_RADIO);
        }
        radio.presetStep(next);
    }

    private void toggleRadio() {
        if (snap != null && snap.source == SOURCE_RADIO) {
            radio.deactivateSource();
            session.assumeSource(SOURCE_ANDROID);
        } else {
            PlayerHandoff.switchToRadio(this, radio::activateSource);
            session.assumeSource(SOURCE_RADIO);
        }
    }

    // ------------------------------------------------------------------ dock e apps

    /** Abre o app de um item do dock (rádio, música, telefone…), com o Splash de cada um. */
    private void openDock(DockView.Item item) {
        DockNavigator.navigate(this, null, item);
    }

    /** Abre um app da suíte fora do dock (configurações, ventoinha). */
    private void openApp(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(this, getString(R.string.screen_app_missing, pkg), Toast.LENGTH_LONG).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private static boolean configReadable() {
        return NwdConfigs.file(LauncherConfig.FILE).isFile();
    }

    private void startWaitingForConfig() {
        waitingForConfig = true;
        configPolls = 0;
        IntentFilter f = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        f.addDataScheme("file");
        registerReceiver(mountReceiver, f);
        handler.postDelayed(configPoll, CONFIG_POLL_MS);
        Log.i(TAG, "launcher.json not readable yet (sdcard not mounted?): waiting for it");
    }

    private void stopWaitingForConfig() {
        if (!waitingForConfig) return;
        waitingForConfig = false;
        handler.removeCallbacks(configPoll);
        try {
            unregisterReceiver(mountReceiver);
        } catch (Exception ignored) {
        }
    }

    /** The settings file showed up: language, theme, font, dock and modules all come from it, so rebuild. */
    private void onConfigAvailable() {
        stopWaitingForConfig();
        Log.i(TAG, "launcher.json is readable now: applying the settings");
        Ui.applyLanguage(getApplicationContext());
        BottomBar.refresh(this);
        recreate();
    }

    /** Idioma, tema, modo escuro e fonte em uso nesta tela; se mudarem nas Configurações (outro app), recria. */
    private String lookKey() {
        return LauncherConfig.language(this) + "|" + LauncherConfig.theme(this)
            + "|" + LauncherConfig.darkMode(this) + "|" + LauncherConfig.font(this);
    }

    private void openDateSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            openAndroidSettings();
        }
    }

    private void openAndroidSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, R.string.home_settings_not_found, Toast.LENGTH_SHORT).show();
        }
    }


    // ------------------------------------------------------------------ NWD default launcher

    /**
     * The NWD system stores the default launcher in persist.nwd.launcher.default (system
     * services read this property). If it isn't this app, asks the user whether to
     * change it.
     */
    private void checkDefaultLauncher() {
        if (isFinishing()) return;
        // Without launcher.json yet we don't know what the user answered before: ask after the rebuild.
        if (waitingForConfig) return;
        if (!LauncherConfig.askDefaultLauncher(this)) return; // the user chose the stock launcher
        String current = SystemProps.get(SystemProps.PROP_DEFAULT_LAUNCHER, "");
        if (getPackageName().equals(current)) return;

        showConfirm(
            getString(R.string.home_default_launcher_title),
            getString(R.string.home_default_launcher_message,
                current.isEmpty() ? getString(R.string.home_not_set_paren) : current),
            null,
            getString(R.string.home_not_now), null,
            getString(R.string.home_set), this::setAsDefaultLauncher);
    }

    private void setAsDefaultLauncher() {
        SystemProps.requestSet(this, SystemProps.PROP_DEFAULT_LAUNCHER, getPackageName());
        handler.postDelayed(() -> {
            String now = SystemProps.get(SystemProps.PROP_DEFAULT_LAUNCHER, "");
            if (getPackageName().equals(now)) {
                Toast.makeText(this, R.string.home_default_launcher_done, Toast.LENGTH_LONG).show();
            } else {
                showConfirm(
                    getString(R.string.home_default_launcher_failed_title),
                    getString(R.string.home_default_launcher_failed_message,
                        now.isEmpty() ? getString(R.string.home_not_set) : now),
                    "adb shell setprop " + SystemProps.PROP_DEFAULT_LAUNCHER + " " + getPackageName(),
                    null, null,
                    getString(R.string.home_ok), null);
            }
        }, 1500);
    }

    /** App-styled dialog. A button with null text is hidden; a null action just dismisses. */
    private void showConfirm(String title, String message, String code,
                             String negative, Runnable onNegative,
                             String positive, Runnable onPositive) {
        if (dialog != null) dialog.dismiss();
        Dialog d = new Dialog(this, R.style.OverlayDialog);
        View root = getLayoutInflater().inflate(R.layout.dialog_confirm, null);
        Ui.applyFont(root);
        d.setContentView(root);
        ((TextView) root.findViewById(R.id.confirm_title)).setText(title);
        ((TextView) root.findViewById(R.id.confirm_message)).setText(message);
        TextView codeView = (TextView) root.findViewById(R.id.confirm_code);
        if (code != null) {
            codeView.setText(code);
            codeView.setTypeface(android.graphics.Typeface.MONOSPACE);
            codeView.setVisibility(View.VISIBLE);
        }
        bindButton((TextView) root.findViewById(R.id.confirm_negative), negative, onNegative, d);
        TextView positiveButton = (TextView) root.findViewById(R.id.confirm_positive);
        positiveButton.setActivated(true);
        bindButton(positiveButton, positive, onPositive, d);
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        Ui.hideSystemBars(d.getWindow());
        d.show();
        Touch.install(root);
        dialog = d;
    }

    private static void bindButton(TextView b, String text, Runnable action, Dialog d) {
        if (text == null) {
            b.setVisibility(View.GONE);
            return;
        }
        b.setText(text);
        b.setOnClickListener(v -> {
            d.dismiss();
            if (action != null) action.run();
        });
    }

    // ------------------------------------------------------------------ util

    private TextView text(int id) {
        return (TextView) findViewById(id);
    }

    private IconView icon(int id, IconView.Icon glyph, IconView.Style style) {
        IconView v = (IconView) findViewById(id);
        v.set(glyph, style);
        return v;
    }

    private static void show(View v, boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        if (v.getVisibility() != vis) v.setVisibility(vis);
    }

    private static String capitalize(String t) {
        if (t == null || t.isEmpty()) return "";
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
