package com.k4811.common.system;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k4811.common.config.LauncherConfig;

/**
 * NWD floating bottom bar ({@code com.nwd.statusbarbottom}: Home, Back, volume and, on some cars,
 * A/C), drawn over every app. K4811 has its own dock, so with Settings › System › "Hide the NWD
 * bottom bar" on (off by default) the bar is hidden while any K4811 screen is in front and shown
 * again when another app takes over. It follows the whole app, not each screen: going from the
 * home screen to the radio doesn't flash the bar.
 *
 * The bar's receivers take {@code removeFloat} / {@code createFloat} with
 * {@code pagename=home_horizontal} from any app; on units without it the broadcasts go nowhere.
 *
 * Suíte K4811: cada app é um processo. Ao ir de um app da suíte para outro, o novo começa (e
 * esconde a barra) antes de o antigo parar. Por isso, ao parar, o app espera um pouco e pergunta
 * aos outros ({@link #ACTION_QUERY}, broadcast ordenado; o {@link Query} de cada app responde se
 * tem tela na frente). Só mostra a barra de volta se nenhum app da suíte estiver na frente.
 */
public final class BottomBar implements Application.ActivityLifecycleCallbacks {

    private static final String ACTION_REMOVE = "com.nwd.statusbarbottom.removeFloat";
    private static final String ACTION_CREATE = "com.nwd.statusbarbottom.createFloat";
    private static final String EXTRA_PAGE = "pagename";
    private static final String PAGE = "home_horizontal";
    private static final String TAG = "K4811Launcher";
    private static final String PACKAGE = "com.nwd.statusbarbottom";
    /** "Algum app da suíte está na frente?" Respondido pelo {@link Query} de cada app. */
    public static final String ACTION_QUERY = "com.k4811.action.BOTTOM_BAR_QUERY";
    /** Espera antes de perguntar: tempo de o próximo app da suíte começar. */
    private static final long QUERY_DELAY_MS = 800;
    private final Handler main = new Handler(Looper.getMainLooper());

    private static BottomBar instance;
    private final Context app;
    /** K4811 screens started and not stopped; 0 = another app is in front. */
    private int started;
    /** We asked the bar to go away and haven't brought it back yet. */
    private boolean hidden;

    private BottomBar(Context app) {
        this.app = app;
    }

    public static void install(Application app) {
        instance = new BottomBar(app);
        app.registerActivityLifecycleCallbacks(instance);
    }

    /** The head unit has the bar (Settings only offers the switch then). */
    public static boolean available(Context c) {
        return ExternalApps.installed(c, PACKAGE);
    }

    /** Altura da barra da NWD na tela vertical: 140 px numa largura de 768 (layout home_horizontal do app dela). */
    private static final int BAR_HEIGHT_PX = 140;
    private static final int BAR_WIDTH_PX = 768;

    /**
     * Espaço (px reais) que as telas da suíte deixam livre embaixo para a barra da NWD: nas centrais verticais que
     * têm a barra e com "Ocultar a barra" desligado ela fica por cima dos apps, então o dock da suíte sobe e fica
     * logo acima dela. 0 nas horizontais (sem a barra) ou com a barra oculta.
     * <p>
     * Só conta a parte da barra que cai dentro da área dos apps: em algumas centrais o sistema já termina a janela
     * acima da barra, e descontar de novo deixava a tela inicial mais larga que alta (grade horizontal cortada numa
     * tela de 768) e o dock 140 px acima do lugar.
     */
    public static int reservedPx(Context c) {
        return reservedPx(c, null);
    }

    /**
     * Como {@link #reservedPx(Context)}, medindo onde {@code area} (já na tela) termina: só conta a parte da barra
     * que fica por cima dela. Sem a área medida, supõe a janela até o fim da tela (a barra inteira).
     */
    public static int reservedPx(Context c, android.view.View area) {
        if (!available(c) || LauncherConfig.hideBottomBar(c)) return 0;
        android.view.WindowManager wm = (android.view.WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return 0;
        android.graphics.Point real = new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(real);
        if (real.y <= real.x) return 0; // a barra é da tela vertical
        int bar = Math.round(BAR_HEIGHT_PX * (real.x / (float) BAR_WIDTH_PX));
        if (area == null || area.getHeight() <= 0 || area.getWindowToken() == null) return bar;
        int[] at = new int[2];
        area.getLocationOnScreen(at);
        int under = at[1] + area.getHeight() - (real.y - bar);
        return Math.max(0, Math.min(bar, under));
    }

    /** Settings switch. Applies at once: K4811 is in front while it's toggled. */
    public static void setEnabled(Context c, boolean on) {
        LauncherConfig.setHideBottomBar(c, on);
        BottomBar b = instance;
        if (b == null) return;
        if (on && b.started > 0 && !b.hidden) b.hide();
        else if (!on && b.hidden) b.show();
    }

    /**
     * Reads the switch again with a screen of this app in front: for when launcher.json only became
     * readable after the screen started (at power-on the sdcard mounts after the launcher opens).
     */
    public static void refresh(Context c) {
        BottomBar b = instance;
        if (b == null || b.started == 0) return;
        boolean hide = LauncherConfig.hideBottomBar(c);
        if (hide && !b.hidden) b.hide();
        else if (!hide && b.hidden) b.show();
    }

    @Override
    public void onActivityStarted(Activity activity) {
        // The next screen starts before the previous one stops, so moving between our screens
        // never goes through 0.
        if (started++ == 0 && LauncherConfig.hideBottomBar(app)) hide();
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (started > 0 && --started == 0 && hidden) main.postDelayed(this::showUnlessSuiteInFront, QUERY_DELAY_MS);
    }

    /** Mostra a barra de volta, a não ser que outro app da suíte esteja na frente. */
    private void showUnlessSuiteInFront() {
        if (started > 0 || !hidden) return;
        Intent q = new Intent(ACTION_QUERY);
        app.sendOrderedBroadcast(q, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (started > 0) return;
                if (getResultCode() == 1) {
                    hidden = false; // o outro app da suíte cuida da barra agora
                    return;
                }
                show();
            }
        }, null, 0, null, null);
    }

    /** true se este app tem uma tela na frente. */
    static boolean inFront() {
        return instance != null && instance.started > 0;
    }

    /** Responde ao {@link #ACTION_QUERY} (declarado no manifest do common, entra em todos os apps). */
    public static class Query extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (inFront()) setResultCode(1);
        }
    }

    private void hide() {
        hidden = true;
        send(ACTION_REMOVE);
    }

    private void show() {
        hidden = false;
        send(ACTION_CREATE);
    }

    private void send(String action) {
        try {
            app.sendBroadcast(new Intent(action).putExtra(EXTRA_PAGE, PAGE));
            Log.d(TAG, "→ " + action);
        } catch (Exception e) {
            Log.w(TAG, "Bottom bar: " + e.getMessage());
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
