package com.k4811.car.setting;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.k4811.car.setting.system.Hotspot;
import com.k4811.car.setting.ui.InputDialog;
import com.k4811.car.setting.ui.OptionList;
import com.k4811.common.Apps;

/**
 * Aba Conexões: Wi-Fi, roteador Wi-Fi (hotspot), Bluetooth e rede móvel num lugar só, no mesmo
 * padrão das outras abas. O que o Android deixa este app fazer é feito aqui (ligar/desligar, nome e
 * senha do roteador); o resto abre a tela certa do Android ou o app de telefone da suíte.
 */
final class ConnectionsPanel {
    private static final int MIN_PASSWORD = 8;
    /** Ligar/desligar Wi-Fi e roteador leva alguns segundos: reler a tela depois. */
    private static final long SETTLE_MS = 2500;

    private final Activity activity;
    private final OptionList list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Dialog dialog;

    ConnectionsPanel(Activity activity, OptionList list) {
        this.activity = activity;
        this.list = list;
    }

    void build() {
        buildWifi();
        buildHotspot();
        buildBluetooth();
        buildMobile();
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
        if (dialog != null) dialog.dismiss();
    }

    // ---- Wi-Fi ----

    private void buildWifi() {
        list.section(s(R.string.settings_conn_wifi_section));
        list.toggle(s(R.string.settings_conn_wifi), s(R.string.settings_conn_wifi_hint),
            () -> wifi().isWifiEnabled(),
            on -> {
                wifi().setWifiEnabled(on);
                later();
            }).local().help(s(R.string.settings_help_conn_wifi));
        list.info(s(R.string.settings_conn_wifi_network), this::wifiNetwork);
        list.action(s(R.string.settings_conn_wifi_networks), s(R.string.settings_conn_wifi_networks_hint),
            s(R.string.settings_conn_open), () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    }

    /** Rede conectada agora, ou null (linha some). */
    private String wifiNetwork() {
        try {
            if (!wifi().isWifiEnabled()) return null;
            WifiInfo info = wifi().getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) return s(R.string.settings_conn_not_connected);
            String ssid = info.getSSID();
            return ssid == null ? null : ssid.replace("\"", "");
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Roteador Wi-Fi ----

    private void buildHotspot() {
        list.section(s(R.string.settings_conn_hotspot_section));
        list.toggle(s(R.string.settings_conn_hotspot), s(R.string.settings_conn_hotspot_hint),
            () -> Hotspot.isOn(activity),
            on -> {
                if (!Hotspot.setOn(activity, on)) openHotspotSettings();
                later();
            }).local().help(s(R.string.settings_help_conn_hotspot));
        list.info(s(R.string.settings_conn_hotspot_name), () -> {
            WifiConfiguration c = Hotspot.config(activity);
            return c == null ? null : c.SSID;
        });
        list.info(s(R.string.settings_conn_hotspot_password), () -> {
            WifiConfiguration c = Hotspot.config(activity);
            return c == null || c.preSharedKey == null ? null : c.preSharedKey;
        });
        OptionList.Row change = list.plain(s(R.string.settings_conn_hotspot_change), s(R.string.settings_conn_hotspot_change_hint))
            .visibleWhen(() -> Hotspot.config(activity) != null);
        change.addButton(s(R.string.settings_conn_hotspot_name), this::askName);
        change.addButton(s(R.string.settings_conn_hotspot_password), this::askPassword);
        // Sem permissão para o nome e a senha (app não privilegiado): a tela do Android faz.
        list.action(s(R.string.settings_conn_hotspot_settings), s(R.string.settings_conn_hotspot_settings_hint),
            s(R.string.settings_conn_open), this::openHotspotSettings);
    }

    private void askName() {
        WifiConfiguration c = Hotspot.config(activity);
        if (c == null) return;
        if (dialog != null) dialog.dismiss();
        dialog = InputDialog.show(activity, s(R.string.settings_conn_hotspot_name), null, c.SSID, false,
            s(R.string.settings_conn_save), text -> {
                String name = text.trim();
                if (name.isEmpty() || name.length() > 32) return s(R.string.settings_conn_name_invalid);
                if (!Hotspot.save(activity, name, null)) return s(R.string.settings_conn_save_failed);
                later();
                return null;
            });
    }

    private void askPassword() {
        WifiConfiguration c = Hotspot.config(activity);
        if (c == null) return;
        if (dialog != null) dialog.dismiss();
        dialog = InputDialog.show(activity, s(R.string.settings_conn_hotspot_password), null, c.preSharedKey, false,
            s(R.string.settings_conn_save), text -> {
                String pass = text.trim();
                if (pass.length() < MIN_PASSWORD || pass.length() > 63) return s(R.string.settings_conn_password_invalid);
                if (!Hotspot.save(activity, null, pass)) return s(R.string.settings_conn_save_failed);
                later();
                return null;
            });
    }

    private void openHotspotSettings() {
        Intent tether = new Intent(Intent.ACTION_MAIN)
            .setClassName("com.android.settings", "com.android.settings.TetherSettings");
        if (!open(tether)) {
            open(new Intent(Intent.ACTION_MAIN)
                .setClassName("com.android.settings", "com.android.settings.Settings$TetherSettingsActivity"));
        }
    }

    // ---- Bluetooth ----

    private void buildBluetooth() {
        list.section(s(R.string.settings_conn_bt_section));
        list.action(s(R.string.settings_conn_bt_phone), s(R.string.settings_conn_bt_phone_hint),
            s(R.string.settings_conn_open), () -> {
                Intent i = activity.getPackageManager().getLaunchIntentForPackage(Apps.PHONE);
                if (i == null || !open(i)) toast(R.string.settings_conn_unavailable);
            }).help(s(R.string.settings_help_conn_bt));
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) return;
        list.toggle(s(R.string.settings_conn_bt_android), s(R.string.settings_conn_bt_android_hint),
            () -> bt.isEnabled(),
            on -> {
                if (on) bt.enable();
                else bt.disable();
                later();
            }).local();
        list.action(s(R.string.settings_conn_bt_devices), s(R.string.settings_conn_bt_devices_hint),
            s(R.string.settings_conn_open), () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
    }

    // ---- Rede móvel ----

    private void buildMobile() {
        TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null || tm.getSimState() == TelephonyManager.SIM_STATE_ABSENT) return;
        list.section(s(R.string.settings_conn_mobile_section));
        list.info(s(R.string.settings_conn_mobile_operator), () -> {
            String op = tm.getNetworkOperatorName();
            return op == null || op.isEmpty() ? s(R.string.settings_conn_not_connected) : op + mobileState();
        });
        list.action(s(R.string.settings_conn_mobile_settings), s(R.string.settings_conn_mobile_settings_hint),
            s(R.string.settings_conn_open), () -> open(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)));
    }

    /** " · dados ligados/desligados", pela conexão móvel ativa. */
    private String mobileState() {
        try {
            ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            boolean on = ni != null && ni.isConnected();
            return " · " + s(on ? R.string.settings_conn_data_on : R.string.settings_conn_data_off);
        } catch (Exception e) {
            return "";
        }
    }

    // ---- Peças ----

    private WifiManager wifi() {
        return (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private boolean open(Intent i) {
        try {
            activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Relê a aba depois que o Android aplica a mudança (o botão mostra o novo estado logo ao tocar). */
    private void later() {
        handler.postDelayed(list::renderAll, SETTLE_MS);
        handler.postDelayed(list::renderAll, SETTLE_MS * 2);
    }

    private void toast(int res) {
        Toast.makeText(activity, res, Toast.LENGTH_SHORT).show();
    }

    private String s(int res) {
        return activity.getString(res);
    }
}
