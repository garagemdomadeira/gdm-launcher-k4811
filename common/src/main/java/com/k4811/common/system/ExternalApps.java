package com.k4811.common.system;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import com.k4811.common.Apps;
import com.k4811.common.R;

/**
 * Head-unit apps that K4811 only launches (they don't become our own screens): AUX, video,
 * 360 camera, Z-Link (CarPlay / Android Auto) and the NWD settings menus.
 */
public final class ExternalApps {
    private ExternalApps() {
    }

    public static final String PKG_AUX = "com.nwd.auxin";
    public static final String PKG_VIDEO = "com.nwd.android.video.ui";
    public static final String PKG_AVM = "com.ivicar.avm";
    public static final String PKG_CARKIT_360 = "com.nwd.carkit360camera";
    public static final String PKG_CAR_SETTING = "com.android.car.setting";
    public static final String PKG_FACTORY = "com.nwd.factory.setting";
    /** Z-Link: wired/wireless CarPlay and Android Auto on NWD units. */
    public static final String PKG_ZLINK = "com.zjinnova.zlink";

    private static final String HOME_HORIZONTAL = ".home_horizontalActivity";
    private static final String ZLINK_MAIN = "com.zjinnova.android.zlink.features.main.MainActivity";
    private static final String AVM_MAIN = "com.ivicar.modules.main.view.MainNowadaActivity";
    private static final String AVM_SETTINGS = "com.ivicar.modules.setting.view.SettingActivity";
    private static final String AVM_CALIBRATE = "com.ivicar.modules.calibrate.view.CalibrateActivity";

    /** Opens the 360 without changing the view (extra_view = -1). Dynamic receiver: the AVM process must be alive. */
    private static final String ACTION_360_IN = "com.nwd.action.360.activity.in";
    private static final String EXTRA_VIEW = "extra_view";
    /** If the screen doesn't leave the foreground within this time, the broadcast was not handled. */
    private static final long AVM_FALLBACK_MS = 1200;

    /** Default factory menu password; {@code Settings.System nwd_factory_password} takes precedence. */
    private static final String FACTORY_PASSWORD = "1617";

    public static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Apps de navegação conhecidos, na ordem de preferência quando nada foi escolhido. */
    private static final String[] NAV_KNOWN = {"com.waze", "com.google.android.apps.maps",
        "com.sygic.aura", "com.here.app.maps", "net.osmand", "net.osmand.plus", "com.mapfactor.navigator",
        "com.tomtom.gplay.navapp", "cz.aponia.bor3", "ru.yandex.yandexnavi", "com.google.android.apps.mapslite"};

    /**
     * Apps de navegação instalados: os conhecidos e qualquer outro que abra um endereço ({@code geo:})
     * e tenha ícone na gaveta. Package, sem repetir.
     */
    public static java.util.List<String> navApps(Context c) {
        java.util.List<String> out = new java.util.ArrayList<>();
        PackageManager pm = c.getPackageManager();
        for (String p : NAV_KNOWN) {
            if (installed(c, p) && pm.getLaunchIntentForPackage(p) != null) out.add(p);
        }
        try {
            Intent geo = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=0,0"));
            for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(geo, 0)) {
                String p = r.activityInfo.packageName;
                if (!out.contains(p) && pm.getLaunchIntentForPackage(p) != null) out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Nome do app (para o dock e as Configurações), ou o package. */
    public static String appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** Abre o app de navegação escolhido. false se não houver nenhum. */
    public static boolean openNav(Context c) {
        String pkg = com.k4811.common.config.LauncherConfig.navApp(c);
        if (pkg.isEmpty()) return false;
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasAux(Context c) {
        return installed(c, PKG_AUX);
    }

    public static boolean hasVideo(Context c) {
        return installed(c, PKG_VIDEO);
    }

    public static boolean hasZlink(Context c) {
        return installed(c, PKG_ZLINK);
    }

    public static boolean hasAvm(Context c) {
        return installed(c, PKG_AVM);
    }

    public static boolean has360(Context c) {
        return hasAvm(c) || installed(c, PKG_CARKIT_360);
    }

    public static void openAux(Context c) {
        open(c, PKG_AUX, PKG_AUX + HOME_HORIZONTAL);
    }

    public static void openVideo(Context c) {
        open(c, PKG_VIDEO, PKG_VIDEO + HOME_HORIZONTAL);
    }

    public static void openZlink(Context c) {
        open(c, PKG_ZLINK, ZLINK_MAIN);
    }

    /**
     * 360 camera: with the ivicar AVM, requests it via broadcast (opens on the last view). If the
     * current screen stays in front, the AVM process wasn't alive: opens via the launcher Activity.
     */
    public static void open360(Activity from) {
        if (hasAvm(from)) {
            Intent in = new Intent(ACTION_360_IN);
            in.putExtra(EXTRA_VIEW, -1);
            from.sendBroadcast(in);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (from.isFinishing() || !from.hasWindowFocus()) return;
                open(from, PKG_AVM, AVM_MAIN);
            }, AVM_FALLBACK_MS);
            return;
        }
        open(from, PKG_CARKIT_360, PKG_CARKIT_360 + HOME_HORIZONTAL);
    }

    public static void open360Settings(Context c) {
        open(c, PKG_AVM, AVM_SETTINGS);
    }

    public static void open360Calibration(Context c) {
        open(c, PKG_AVM, AVM_CALIBRATE);
    }

    /**
     * Config. Veículo de fábrica. {@code page} (opcional) vai no extra ref0, ex.: setNaviPath.
     * Se o app original ({@link #PKG_CAR_SETTING}) não estiver instalado, abre o das configurações
     * da suíte ({@link Apps#SETTINGS}).
     */
    public static void openCarSettings(Context c, String page) {
        if (installed(c, PKG_CAR_SETTING)) {
            Intent i = component(PKG_CAR_SETTING, PKG_CAR_SETTING + HOME_HORIZONTAL);
            if (page != null) i.putExtra("ref0", page);
            start(c, i, PKG_CAR_SETTING);
            return;
        }
        start(c, component(Apps.SETTINGS, "com.k4811.car.setting.SettingsActivity"), Apps.SETTINGS);
    }

    public static void openFactoryMenu(Context c) {
        open(c, PKG_FACTORY, PKG_FACTORY + ".LoginActivity");
    }

    public static String factoryPassword(Context c) {
        String custom = Settings.System.getString(c.getContentResolver(), "nwd_factory_password");
        return custom == null || custom.trim().isEmpty() ? FACTORY_PASSWORD : custom.trim();
    }

    public static void openAndroidSettings(Context c) {
        open(c, "com.android.settings", "com.android.settings.Settings");
    }

    /** Opens the Activity; if it isn't exported or doesn't exist, falls back to the package's launcher Activity. */
    private static void open(Context c, String pkg, String cls) {
        start(c, component(pkg, cls), pkg);
    }

    private static Intent component(String pkg, String cls) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(new ComponentName(pkg, cls));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private static void start(Context c, Intent i, String pkg) {
        try {
            c.startActivity(i);
            return;
        } catch (Exception ignored) {
            // Activity not exported (e.g. internal AVM screens) or missing on this head unit.
        }
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(launch);
                Toast.makeText(c, c.getString(R.string.adv_toast_opened_launcher), Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(c, c.getString(R.string.adv_toast_app_missing, pkg), Toast.LENGTH_LONG).show();
    }
}
