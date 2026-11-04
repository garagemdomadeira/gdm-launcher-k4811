package com.k4811.car.setting.system;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Roteador Wi-Fi (hotspot) da central pelo próprio Android 10, sem abrir a tela de configurações.
 *
 * <ul>
 * <li>Ligar/desligar: {@code IConnectivityManager.startTethering/stopTethering} (o que a tela do
 * Android usa). O Android aceita de quem tem {@code TETHER_PRIVILEGED} (app privilegiado da ROM) ou
 * a permissão "alterar configurações do sistema" ({@code WRITE_SETTINGS}, que esta suíte já tem).</li>
 * <li>Nome e senha: {@code WifiManager.get/setWifiApConfiguration}, que exigem
 * {@code OVERRIDE_WIFI_CONFIG}: só com o app instalado como privilegiado na ROM (/system/priv-app).
 * Sem isso {@link #config} devolve null e a tela oferece abrir as configurações do Android.</li>
 * </ul>
 * São APIs escondidas do Android: chamadas por reflexão, cada uma protegida (sem quebrar a tela).
 */
public final class Hotspot {
    private static final String TAG = "K4811Hotspot";
    /** ConnectivityManager.TETHERING_WIFI */
    private static final int TETHERING_WIFI = 0;

    private Hotspot() {
    }

    /** Aviso do Android a cada mudança do roteador (fica guardado: dá para ler sem esperar). */
    private static final String ACTION_AP_STATE = "android.net.wifi.WIFI_AP_STATE_CHANGED";
    private static final int AP_ENABLING = 12;
    private static final int AP_ENABLED = 13;

    /**
     * Ligado (ou ligando). Pelo último aviso do Android, que não depende de API escondida (a
     * {@code isWifiApEnabled} é bloqueada para apps comuns); a reflexão fica só de reserva.
     */
    public static boolean isOn(Context c) {
        try {
            android.content.Intent last = c.getApplicationContext()
                .registerReceiver(null, new android.content.IntentFilter(ACTION_AP_STATE));
            if (last != null) {
                int state = last.getIntExtra("wifi_state", -1);
                if (state >= 0) return state == AP_ENABLED || state == AP_ENABLING;
            }
        } catch (Throwable ignored) {
        }
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return (Boolean) WifiManager.class.getMethod("isWifiApEnabled").invoke(wm);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Liga ou desliga. false se o Android recusou (o chamador abre a tela do Android). */
    public static boolean setOn(Context c, boolean on) {
        try {
            Object cm = connectivity();
            String pkg = c.getPackageName();
            if (on) {
                Method m = cm.getClass().getMethod("startTethering", int.class, ResultReceiver.class,
                    boolean.class, String.class);
                m.invoke(cm, TETHERING_WIFI, new ResultReceiver(null), false, pkg);
            } else {
                cm.getClass().getMethod("stopTethering", int.class, String.class).invoke(cm, TETHERING_WIFI, pkg);
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, (on ? "ligar" : "desligar") + ": " + t);
            return false;
        }
    }

    /** Nome e senha atuais, ou null se este app não pode ler (não é privilegiado). */
    public static WifiConfiguration config(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return (WifiConfiguration) WifiManager.class.getMethod("getWifiApConfiguration").invoke(wm);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Grava nome e/ou senha (null = mantém). Senha com 8 caracteres ou mais (WPA2). Com o roteador
     * ligado, desliga e liga de novo para valer na hora.
     */
    public static boolean save(Context c, String ssid, String password) {
        WifiConfiguration cfg = config(c);
        if (cfg == null) return false;
        if (ssid != null) cfg.SSID = ssid;
        if (password != null) {
            cfg.preSharedKey = password;
            cfg.allowedKeyManagement.clear();
            cfg.allowedKeyManagement.set(4); // KeyMgmt.WPA2_PSK
        }
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            boolean ok = (Boolean) WifiManager.class.getMethod("setWifiApConfiguration", WifiConfiguration.class)
                .invoke(wm, cfg);
            if (ok && isOn(c)) {
                setOn(c, false);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> setOn(c, true), 1500);
            }
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "gravar: " + t);
            return false;
        }
    }

    private static Object connectivity() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, Context.CONNECTIVITY_SERVICE);
        Class<?> stub = Class.forName("android.net.IConnectivityManager$Stub");
        return stub.getMethod("asInterface", IBinder.class).invoke(null, b);
    }
}
