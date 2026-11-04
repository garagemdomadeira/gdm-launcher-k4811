package com.k4811.common.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.k4811.common.Apps;
import com.k4811.common.R;
import com.k4811.common.audio.PlayerHandoff;
import com.k4811.common.system.ExternalApps;

/**
 * Coloca o dock embaixo do conteúdo de uma tela e abre o app de cada item.
 * Cada função é um app da suíte: trocar de app pelo dock fecha o atual (não empilha) e o
 * Início volta para o launcher padrão. Dentro dos apps, rádio, música, telefone e EQ abrem depois
 * de um Splash curto com o ícone do serviço. Na tela inicial (launcher) abrem na hora, sem Splash.
 * O rádio, ao abrir, espera o MP3 parar antes de pegar a fonte (PlayerHandoff.switchToRadio).
 */
public final class DockNavigator {
    private DockNavigator() {
    }

    /**
     * Chamar logo depois de setContentView. O conteúdo fica com o espaço acima do dock.
     * @param current item deste app (fica destacado); null na tela inicial e nas telas fora do dock
     */
    public static DockView attachDock(Activity activity, DockView.Item current) {
        ViewGroup root = (ViewGroup) activity.findViewById(android.R.id.content);
        View content = root.getChildAt(0);
        root.removeView(content);

        // O conteúdo fica num "palco" para o Splash cobrir só ele, com o dock visível.
        FrameLayout stage = new FrameLayout(activity);
        stage.setId(R.id.screen_stage);
        stage.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Ui.BG);
        column.addView(stage, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        DockView dock = new DockView(activity);
        column.addView(dock, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.round(Ui.dp(activity, DockView.HEIGHT_DP))));
        root.addView(column, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dock.setActive(current);
        dock.setListener(item -> navigate(activity, current, item));
        return dock;
    }

    public static void navigate(Activity from, DockView.Item current, DockView.Item target) {
        if (target == current) return;
        Splash.cancel(); // outro item tocado enquanto um Splash estava na tela: vale o último
        if (Apps.LAUNCHER.equals(from.getPackageName())) {
            // Tela inicial: abre na hora. Os players já começam a parar agora.
            if (target == DockView.Item.RADIO) PlayerHandoff.beforeRadio(from);
            open(from, target);
            return;
        }
        if (hasSplash(target)) {
            // Rádio: os players param agora, e a troca de fonte só acontece depois do Splash.
            if (target == DockView.Item.RADIO) PlayerHandoff.beforeRadio(from);
            Splash.show(from, target, () -> open(from, target));
            return;
        }
        open(from, target);
    }

    /** Rádio, música, telefone e EQ abrem depois de um Splash curto com o ícone do serviço. */
    private static boolean hasSplash(DockView.Item item) {
        switch (item) {
            case RADIO:
            case MUSIC:
            case PHONE:
            case BT_MUSIC:
            case EQ:
                return true;
            default:
                return false;
        }
    }

    private static void open(Activity from, DockView.Item target) {
        switch (target) {
            case AUX:
                ExternalApps.openAux(from);
                return;
            case VIDEO:
                ExternalApps.openVideo(from);
                return;
            case ZLINK:
                ExternalApps.openZlink(from);
                return;
            case NAV:
                ExternalApps.openNav(from);
                return;
            case CAMERA_360:
                ExternalApps.open360(from);
                return;
            case HOME:
                if (Apps.LAUNCHER.equals(from.getPackageName())) return; // já é a tela inicial
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                switchTo(from, home, R.anim.screen_close_enter, R.anim.screen_close_exit);
                return;
            case APPS:
                Intent apps = new Intent(Apps.ACTION_APPS);
                apps.setPackage(Apps.LAUNCHER);
                apps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (from.getPackageManager().resolveActivity(apps, 0) == null) {
                    // Launcher sem a gaveta: volta para o início.
                    open(from, DockView.Item.HOME);
                    return;
                }
                switchTo(from, apps, R.anim.screen_swap_in, R.anim.screen_swap_out);
                return;
            default:
                Intent launch = target.pkg == null ? null
                    : from.getPackageManager().getLaunchIntentForPackage(target.pkg);
                if (launch == null) {
                    Toast.makeText(from, from.getString(R.string.screen_app_missing, target.pkg),
                        Toast.LENGTH_LONG).show();
                    return;
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Troca entre apps do dock: crossfade rápido (o dock fica no mesmo lugar).
                switchTo(from, launch, R.anim.screen_swap_in, R.anim.screen_swap_out);
        }
    }

    /** Abre o outro app e fecha esta tela, menos a tela inicial (que fica embaixo de tudo). */
    private static void switchTo(Activity from, Intent intent, int enter, int exit) {
        try {
            from.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(from, from.getString(R.string.screen_app_missing, String.valueOf(intent.getPackage())),
                Toast.LENGTH_LONG).show();
            return;
        }
        if (Apps.LAUNCHER.equals(from.getPackageName())) {
            from.overridePendingTransition(enter, exit);
            return;
        }
        from.finish();
        from.overridePendingTransition(enter, exit);
    }
}
