package com.k4811.launcher.pip;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Region;
import android.os.Build;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

/**
 * Quadro da tela inicial com um app de verdade rodando dentro (PIP), como o ActivityView do
 * Android, mas sem o listener de tarefas (que exige permissão de sistema).
 *
 * <p>Os filhos declarados no XML (ícone e texto de "vazio") ficam por baixo da superfície e só
 * aparecem com {@link #showEmpty(boolean)}.
 */
public class PipView extends FrameLayout {

    /** Avisa quando o display do PIP está pronto para receber o app. */
    public interface Listener {
        void onPipReady(PipView view);
    }

    private final SurfaceView surface;
    private final int[] location = {Integer.MIN_VALUE, 0};
    private final int[] tapBounds = {Integer.MIN_VALUE, 0, 0, 0};
    private final Region tapRegion = new Region();
    private PipDisplay display;
    private Listener listener;
    private int slot;
    private float scale = 1f;
    /** Tela apagada: os toques não vão para o app do PIP. */
    private boolean touchBlocked;
    // Enquanto a alça é arrastada a superfície muda a cada quadro: o display só acompanha quando para.
    private final Runnable resize = () -> {
        if (display != null) display.resize(getWidth(), getHeight());
    };
    private final Runnable nudgeTask = this::nudge;

    public PipView(Context context, AttributeSet attrs) {
        super(context, attrs);
        surface = new SurfaceView(context);
        if (Build.VERSION.SDK_INT >= 29) {
            surface.getHolder().addCallback(new Callback());
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setup(int slot, float cornerRadius, Listener listener) {
        this.slot = slot;
        this.listener = listener;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Hidden.unlock();
                Hidden.call(surface, "setCornerRadius", new Class<?>[] {float.class}, cornerRadius);
            } catch (Throwable ignored) {
                // sem cantos arredondados
            }
        }
    }

    /** Bloqueia ou libera os toques no app do PIP (tela apagada: o toque só acende). */
    public void setTouchBlocked(boolean blocked) {
        touchBlocked = blocked;
        if (blocked) clearTapRegion();
        else updateRegion();
    }

    /** Tamanho do conteúdo do PIP ("DPI do PIP"); 1 = normal. */
    public void setScale(float s) {
        scale = s;
        if (display != null) {
            display.setScale(s);
            display.resize(getWidth(), getHeight());
        }
    }

    public int slot() {
        return slot;
    }

    public boolean ready() {
        return display != null;
    }

    /** Abre o app no PIP; false se o display ainda não existe ou o app não abriu. */
    public boolean launch(Intent intent) {
        return display != null && display.launch(getContext(), intent);
    }

    /**
     * Muda o tamanho do display em 1 pixel e volta: o app refaz o layout e o sistema recoloca as
     * camadas dele. Dentro do PIP, a camada de vídeo de uma tela que abre deslizando (modo vídeo do
     * Spotify) às vezes fica na posição do começo da animação, fora do quadro, e o vídeo sai preto.
     */
    public void scheduleNudge(long delayMs) {
        removeCallbacks(nudgeTask);
        postDelayed(nudgeTask, delayMs);
    }

    public void nudge() {
        if (display == null || getWidth() <= 1) return;
        display.resize(getWidth() - 1, getHeight());
        removeCallbacks(resize);
        postDelayed(resize, 300);
    }

    /** Volta do standby: religa o display do PIP (o sistema desliga todos ao apagar a tela). */
    public void wake() {
        if (display != null && surface.getVisibility() == VISIBLE) display.setOn(true);
    }

    /** Mostra o quadro vazio (sem app) ou a superfície do app. */
    public void showEmpty(boolean empty) {
        surface.setVisibility(empty ? INVISIBLE : VISIBLE);
        if (empty) clearTapRegion();
        else updateRegion();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateRegion();
    }

    @Override
    public boolean gatherTransparentRegion(Region region) {
        updateRegion();
        return super.gatherTransparentRegion(region);
    }

    @Override
    protected void onDetachedFromWindow() {
        clearTapRegion();
        super.onDetachedFromWindow();
    }

    /** Diz ao sistema onde o PIP está na janela e que os toques dele vão para o app. */
    private void updateRegion() {
        if (display == null || getWindowToken() == null || surface.getVisibility() != VISIBLE || touchBlocked) return;
        int oldX = location[0];
        int oldY = location[1];
        getLocationInWindow(location);
        int left = location[0];
        int top = location[1];
        int right = left + getWidth();
        int bottom = top + getHeight();
        if (oldX != left || oldY != top) {
            display.updateLocation(this, left, top);
        }
        if (tapBounds[0] != left || tapBounds[1] != top || tapBounds[2] != right || tapBounds[3] != bottom) {
            tapBounds[0] = left;
            tapBounds[1] = top;
            tapBounds[2] = right;
            tapBounds[3] = bottom;
            tapRegion.set(left, top, right, bottom);
            PipDisplay.setTapExclude(this, System.identityHashCode(this), tapRegion);
        }
    }

    private void clearTapRegion() {
        tapBounds[0] = Integer.MIN_VALUE;
        if (tapRegion.isEmpty() || getWindowToken() == null) return;
        PipDisplay.setTapExclude(this, System.identityHashCode(this), null);
        tapRegion.setEmpty();
    }

    @TargetApi(29)
    private final class Callback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            display = PipDisplay.obtain(getContext(), slot, surface.getSurfaceControl(),
                surface.getWidth(), surface.getHeight());
            if (display == null) return;
            display.attach(PipView.this, surface.getSurfaceControl());
            display.setScale(scale);
            display.resize(surface.getWidth(), surface.getHeight());
            display.setOn(true);
            location[0] = Integer.MIN_VALUE;
            tapBounds[0] = Integer.MIN_VALUE;
            updateRegion();
            if (listener != null) listener.onPipReady(PipView.this);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            removeCallbacks(resize);
            postDelayed(resize, 150);
            updateRegion();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            removeCallbacks(resize);
            removeCallbacks(nudgeTask);
            if (display != null) display.setOn(false);
            clearTapRegion();
        }
    }
}
