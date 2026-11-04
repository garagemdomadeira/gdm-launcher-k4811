package com.k4811.launcher.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.ui.IconView;
import com.k4811.common.ui.Ui;

/**
 * Barra de ícones de estado no alto da tela inicial, no centro, acima do relógio: ventoinha, Wi-Fi,
 * rede móvel, GPS, Bluetooth e o tempo lá fora. Só ícones, sempre na mesma posição: brancos com a
 * função ligada, cinza bem apagado desligada. O único texto é a temperatura externa, ao lado do
 * ícone do tempo ({@link Weather}).
 *
 * <p>Leve de propósito: a ventoinha gira em passos de 20° a cada 100 ms (as 3 pás repetem a cada
 * 120°, parece contínuo), só ligada e com a tela à vista; Wi-Fi, rede móvel e GPS chegam por avisos
 * do Android, sem consulta periódica; o tempo é buscado a cada 20 min. Tudo para no {@link #pause}.
 */
public final class StatusStrip {

    /** Toque nos ícones que abrem outro app da suíte. */
    public interface Actions {
        void openFan();

        void openBluetooth();
    }

    private static final long SPIN_MS = 100;
    private static final float SPIN_STEP = 20f;
    /** Cinza bem apagado das funções desligadas. */
    private static final int OFF = 0x40FFFFFF;
    private static final int ON = 0xFFFFFFFF;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IconView fanIcon;
    private final IconView wifiIcon;
    private final IconView cellIcon;
    private final IconView gpsIcon;
    private final IconView btIcon;
    private final IconView weatherIcon;
    private final TextView weatherText;
    private final View weatherCell;
    private final Weather weather;

    private boolean resumed;
    private boolean fanOn;
    private int cellLevel;
    private boolean cellService;
    private boolean gpsFix;
    private Object gnssCallback;
    private PhoneStateListener phoneListener;

    private final Runnable spin = new Runnable() {
        @Override
        public void run() {
            fanIcon.setRotation((fanIcon.getRotation() + SPIN_STEP) % 360f);
            handler.postDelayed(this, SPIN_MS);
        }
    };

    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            renderWifi();
        }
    };

    public StatusStrip(Activity activity, LinearLayout root, Actions actions) {
        this.activity = activity;
        root.removeAllViews();
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        fanIcon = add(root, IconView.Icon.FAN, v -> actions.openFan());
        wifiIcon = add(root, IconView.Icon.WIFI, v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        cellIcon = add(root, IconView.Icon.SIGNAL, v -> open(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)));
        gpsIcon = add(root, IconView.Icon.GPS, v -> open(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        btIcon = add(root, IconView.Icon.BLUETOOTH, v -> actions.openBluetooth());

        // Tempo lá fora: ícone + temperatura (só aparece depois da primeira resposta do serviço).
        LinearLayout w = new LinearLayout(activity);
        w.setOrientation(LinearLayout.HORIZONTAL);
        w.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new IconView(activity);
        weatherIcon.set(IconView.Icon.SUN, IconView.Style.NONE);
        weatherIcon.setClickable(false);
        weatherIcon.color(ON);
        int s = dp(40);
        w.addView(weatherIcon, new LinearLayout.LayoutParams(s, s));
        weatherText = new TextView(activity);
        weatherText.setTextColor(ON);
        weatherText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        weatherText.setTypeface(Ui.medium(activity));
        weatherText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = dp(8);
        w.addView(weatherText, tp);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        wp.leftMargin = dp(36);
        root.addView(w, wp);
        weatherCell = w;
        weatherCell.setVisibility(View.GONE);
        weather = new Weather(activity, this::renderWeather);

        renderFan();
        setBluetooth(false);
        renderGps();
        renderCell();
    }

    public void resume() {
        if (resumed) return;
        resumed = true;
        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        activity.registerReceiver(wifiReceiver, f);
        renderWifi();
        listenPhone(true);
        listenGnss(true);
        weather.resume();
        renderFan();
    }

    public void pause() {
        if (!resumed) return;
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        try {
            activity.unregisterReceiver(wifiReceiver);
        } catch (Exception ignored) {
        }
        listenPhone(false);
        listenGnss(false);
        weather.pause();
    }

    public void setFan(boolean on) {
        fanOn = on;
        renderFan();
    }

    public void setBluetooth(boolean connected) {
        btIcon.color(connected ? ON : OFF);
    }

    // ---- Ventoinha ----

    private void renderFan() {
        fanIcon.color(fanOn ? ON : OFF);
        handler.removeCallbacks(spin);
        if (fanOn && resumed) handler.postDelayed(spin, SPIN_MS);
        else fanIcon.setRotation(0);
    }

    // ---- Wi-Fi ----

    private void renderWifi() {
        try {
            WifiManager wm = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm != null && wm.isWifiEnabled() ? wm.getConnectionInfo() : null;
            boolean connected = info != null && info.getNetworkId() != -1;
            int level = connected ? Math.max(1, WifiManager.calculateSignalLevel(info.getRssi(), 5)) : 4;
            wifiIcon.level(level).color(connected ? ON : OFF);
        } catch (Exception e) {
            wifiIcon.level(4).color(OFF);
        }
    }

    // ---- Rede móvel ----

    private void listenPhone(boolean on) {
        TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return;
        try {
            if (!on) {
                if (phoneListener != null) tm.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
                return;
            }
            if (phoneListener == null) {
                phoneListener = new PhoneStateListener() {
                    @Override
                    public void onSignalStrengthsChanged(SignalStrength s) {
                        cellLevel = Build.VERSION.SDK_INT >= 23 ? s.getLevel() : 2;
                        renderCell();
                    }

                    @Override
                    public void onServiceStateChanged(ServiceState s) {
                        cellService = s.getState() == ServiceState.STATE_IN_SERVICE;
                        renderCell();
                    }
                };
            }
            tm.listen(phoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_SERVICE_STATE);
        } catch (Exception ignored) {
            // sem chip ou sem permissão: fica apagado
        }
    }

    private void renderCell() {
        cellIcon.level(cellService ? Math.max(1, cellLevel) : 4).color(cellService ? ON : OFF);
    }

    // ---- GPS ----

    /** Branco com posição (4 satélites ou mais em uso); a central mantém o GPS sempre ligado. */
    @SuppressLint("MissingPermission")
    private void listenGnss(boolean on) {
        if (Build.VERSION.SDK_INT < 24) return;
        LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        try {
            if (on) {
                if (gnssCallback == null) {
                    gnssCallback = new GnssStatus.Callback() {
                        @Override
                        public void onSatelliteStatusChanged(GnssStatus status) {
                            int used = 0;
                            for (int i = 0; i < status.getSatelliteCount(); i++) {
                                if (status.usedInFix(i)) used++;
                            }
                            gpsFix = used >= 4;
                            renderGps();
                        }

                        @Override
                        public void onStopped() {
                            gpsFix = false;
                            renderGps();
                        }
                    };
                }
                lm.registerGnssStatusCallback((GnssStatus.Callback) gnssCallback, handler);
            } else if (gnssCallback != null) {
                lm.unregisterGnssStatusCallback((GnssStatus.Callback) gnssCallback);
            }
        } catch (Exception ignored) {
            // sem permissão de localização ou sem GPS
        }
    }

    private void renderGps() {
        gpsIcon.color(gpsFix ? ON : OFF);
    }

    // ---- Tempo lá fora ----

    private void renderWeather(Weather.Now now) {
        if (now == null) {
            weatherCell.setVisibility(View.GONE);
            return;
        }
        weatherIcon.set(now.icon, IconView.Style.NONE);
        weatherIcon.color(ON);
        weatherText.setText(Math.round(now.celsius) + "°");
        weatherCell.setVisibility(View.VISIBLE);
    }

    // ---- Peças ----

    /** Ícone fixo de 40 com 28 de espaço entre eles; a área de toque é o ícone todo. */
    private IconView add(LinearLayout root, IconView.Icon which, View.OnClickListener click) {
        IconView v = new IconView(activity);
        v.set(which, IconView.Style.NONE);
        v.color(OFF);
        v.setOnClickListener(click);
        int s = dp(40);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        if (root.getChildCount() > 0) lp.leftMargin = dp(28);
        root.addView(v, lp);
        return v;
    }

    private void open(Intent i) {
        try {
            activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    private int dp(float v) {
        return Math.round(Ui.dp(activity, v));
    }
}
