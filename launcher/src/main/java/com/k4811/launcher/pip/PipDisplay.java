package com.k4811.launcher.pip;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.View;

/**
 * Display virtual de um PIP. O app escolhido roda nele e a imagem aparece dentro do PipView.
 *
 * <p>Fica vivo enquanto o processo do launcher existir (um por PIP): se a tela inicial for
 * recriada (troca de tema ou idioma), o app do PIP continua aberto e só é religado à nova
 * superfície. O framework da NWD deixa abrir qualquer app num display virtual do próprio dono,
 * sem a chave de plataforma (ActivityStackSupervisor.isCallerAllowedToLaunchOnDisplay).
 */
@TargetApi(29)
final class PipDisplay {
    private static final String TAG = "GdmPip";
    // PUBLIC | OWN_CONTENT_ONLY (os mesmos do ActivityView, sem DESTROY_CONTENT_ON_REMOVAL).
    private static final int FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;

    /**
     * Display seguro: apps de vídeo com proteção (modo vídeo do Spotify, Netflix…) só tocam nele.
     * Precisa de CAPTURE_SECURE_VIDEO_OUTPUT (app de sistema na ROM, com a permissão liberada).
     */
    private static final String PERM_SECURE = "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT";

    private static int flags(Context c) {
        boolean secure = c.checkSelfPermission(PERM_SECURE) == PackageManager.PERMISSION_GRANTED;
        return secure ? FLAGS | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE : FLAGS;
    }

    private static final PipDisplay[] SLOTS = new PipDisplay[2];

    private final VirtualDisplay display;
    private final SurfaceControl root;
    /** Tamanho do conteúdo ("DPI do PIP"): 1 = densidade da tela; menor = app desenha menor. */
    private float scale = 1f;

    private PipDisplay(VirtualDisplay display, SurfaceControl root) {
        this.display = display;
        this.root = root;
    }

    /** Display do PIP {@code slot}, criado na primeira vez com a superfície {@code parent}. */
    static PipDisplay obtain(Context c, int slot, SurfaceControl parent, int w, int h) {
        PipDisplay d = SLOTS[slot];
        if (d != null) return d;
        Hidden.unlock();
        try {
            DisplayManager dm = (DisplayManager) c.getApplicationContext()
                .getSystemService(Context.DISPLAY_SERVICE);
            VirtualDisplay vd = dm.createVirtualDisplay("GdmPip" + slot,
                Math.max(w, 1), Math.max(h, 1), DisplayMetrics.DENSITY_DEVICE_STABLE, null, flags(c));
            Log.i(TAG, "display do PIP " + slot + (flags(c) != FLAGS ? " (seguro)" : " (sem vídeo protegido: falta permissão)"));
            if (vd == null) return null;
            SurfaceControl.Builder b = new SurfaceControl.Builder()
                .setName("GdmPip" + slot).setParent(parent);
            Hidden.call(b, "setContainerLayer", new Class<?>[0]);
            d = new PipDisplay(vd, b.build());
            int id = d.id();
            try {
                Object wm = Hidden.callStatic("android.view.WindowManagerGlobal", "getWindowManagerService");
                Hidden.call(wm, "dontOverrideDisplayInfo", new Class<?>[] {int.class}, id);
            } catch (Throwable t) {
                // Android 12+: não existe mais (e o display já não é sobrescrito)
                Log.w(TAG, "dontOverrideDisplayInfo: " + t);
            }
            SLOTS[slot] = d;
            return d;
        } catch (Throwable t) {
            Log.e(TAG, "display do PIP " + slot + " não foi criado", t);
            return null;
        }
    }

    int id() {
        return display.getDisplay().getDisplayId();
    }

    /** Põe o conteúdo do display dentro da superfície {@code parent}, que fica na janela de {@code host}. */
    void attach(View host, SurfaceControl parent) {
        new SurfaceControl.Transaction().reparent(root, parent).setVisibility(root, true).apply();
        try {
            Object session = Hidden.callStatic("android.view.WindowManagerGlobal", "getWindowSession");
            Hidden.call(session, "reparentDisplayContent",
                new Class<?>[] {iWindow(), SurfaceControl.class, int.class}, window(host), root, id());
        } catch (Throwable t) {
            Log.e(TAG, "reparentDisplayContent falhou", t);
        }
    }

    void resize(int w, int h) {
        display.resize(Math.max(w, 1), Math.max(h, 1), density());
    }

    /** Muda o tamanho do conteúdo; vale no próximo {@link #resize}. */
    void setScale(float s) {
        scale = s;
    }

    /** Liga ou desliga o display (desligado, os apps dele param como com a tela apagada). */
    void setOn(boolean on) {
        try {
            Hidden.call(display, "setDisplayState", new Class<?>[] {boolean.class}, on);
        } catch (Throwable t) {
            Log.w(TAG, "setDisplayState falhou", t);
        }
    }

    /** Abre o app no PIP. Se ele já estiver aberto em outro display, a tarefa vem para cá. */
    boolean launch(Context c, Intent intent) {
        try {
            ActivityOptions o = ActivityOptions.makeBasic();
            o.setLaunchDisplayId(id());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent, o.toBundle());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "não abriu " + intent + " no PIP", t);
            return false;
        }
    }

    /** Posição do PIP na janela, para os toques irem para o display certo. */
    void updateLocation(View host, int x, int y) {
        try {
            Object session = Hidden.callStatic("android.view.WindowManagerGlobal", "getWindowSession");
            Hidden.call(session, "updateDisplayContentLocation",
                new Class<?>[] {iWindow(), int.class, int.class, int.class}, window(host), x, y, id());
        } catch (Throwable t) {
            Log.w(TAG, "updateDisplayContentLocation falhou", t);
        }
    }

    /** Área da janela cujos toques vão para o app do PIP (null tira). */
    static void setTapExclude(View host, int regionId, android.graphics.Region region) {
        try {
            Object session = Hidden.callStatic("android.view.WindowManagerGlobal", "getWindowSession");
            Hidden.call(session, "updateTapExcludeRegion",
                new Class<?>[] {iWindow(), int.class, android.graphics.Region.class},
                window(host), regionId, region);
        } catch (Throwable t) {
            Log.w(TAG, "updateTapExcludeRegion falhou", t);
        }
    }

    private static Class<?> iWindow() throws ClassNotFoundException {
        return Class.forName("android.view.IWindow");
    }

    private static Object window(View host) throws Exception {
        return Hidden.call(host, "getWindow", new Class<?>[0]);
    }

    /**
     * Densidade real da tela (a das Activities é trocada por Ui.applyDesignDensity) vezes o tamanho
     * do conteúdo. Com densidade menor o app se vê numa tela "maior" (como um tablet), desenha
     * menor e costuma liberar a rotação travada.
     */
    private int density() {
        return Math.max(80, Math.round(DisplayMetrics.DENSITY_DEVICE_STABLE * scale));
    }
}
