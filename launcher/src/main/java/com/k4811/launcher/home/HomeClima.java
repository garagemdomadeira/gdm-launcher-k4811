package com.k4811.launcher.home;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.k4811.common.ui.IconView;
import com.k4811.common.ui.Ui;
import com.k4811.launcher.R;

/**
 * Widget de tempo (clima do ambiente) da tela inicial: ícone (sol, lua, chuva ou nublado) e a temperatura
 * de fora, pela posição da central (GPS) no Open-Meteo. Fica ao lado do "tocando agora" na tela em pé.
 * O {@link HomeModuleViews} cuida de mostrar/esconder e posicionar o quadro; aqui só o conteúdo.
 */
public final class HomeClima {

    private final IconView icon;
    private final TextView temp;
    private final Weather weather;

    public HomeClima(Activity activity) {
        icon = activity.findViewById(R.id.home_clima_icon);
        temp = activity.findViewById(R.id.home_clima_temp);
        temp.setTypeface(Ui.medium(activity));
        // Escondido até o tempo carregar: sem isso o IconView mostra o ícone padrão (MENU, "3 barrinhas").
        icon.setVisibility(View.INVISIBLE);
        weather = new Weather(activity, this::render);
    }

    private void render(Weather.Now now) {
        if (now == null) {
            icon.setVisibility(View.INVISIBLE);
            temp.setText("");
            return;
        }
        icon.set(now.icon, IconView.Style.NONE);
        icon.color(Ui.TEXT);
        icon.setVisibility(View.VISIBLE);
        temp.setText(Math.round(now.celsius) + "°");
    }

    public void resume() {
        weather.resume();
    }

    public void pause() {
        weather.pause();
    }
}
