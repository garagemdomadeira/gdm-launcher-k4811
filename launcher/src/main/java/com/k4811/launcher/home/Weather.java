package com.k4811.launcher.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k4811.common.ui.IconView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Temperatura e tempo lá fora pela posição da central (GPS) no Open-Meteo (open-meteo.com: grátis,
 * sem cadastro nem chave). Busca ao abrir a tela inicial e a cada 20 min enquanto ela está à vista;
 * sem posição ou sem internet, tenta de novo em 2 min. O último resultado fica guardado e aparece
 * logo ao ligar (se tiver menos de 2 h).
 */
final class Weather {

    static final class Now {
        final float celsius;
        final IconView.Icon icon;

        Now(float celsius, IconView.Icon icon) {
            this.celsius = celsius;
            this.icon = icon;
        }
    }

    interface Listener {
        void onWeather(Now now);
    }

    private static final String TAG = "K4811Weather";
    private static final long REFRESH_MS = 20 * 60 * 1000;
    private static final long RETRY_MS = 2 * 60 * 1000;
    private static final long MAX_AGE_MS = 2 * 60 * 60 * 1000;
    private static final String PREFS = "weather";

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean running;

    private final Runnable fetch = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            worker.execute(() -> {
                Now now = load();
                main.post(() -> {
                    if (!running) return;
                    if (now != null) listener.onWeather(now);
                    main.postDelayed(fetch, now != null ? REFRESH_MS : RETRY_MS);
                });
            });
        }
    };

    Weather(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void resume() {
        if (running) return;
        running = true;
        Now cached = cached();
        if (cached != null) listener.onWeather(cached);
        long age = System.currentTimeMillis() - prefs().getLong("at", 0);
        main.postDelayed(fetch, cached != null && age < REFRESH_MS ? REFRESH_MS - age : 0);
    }

    void pause() {
        running = false;
        main.removeCallbacks(fetch);
    }

    // ---- Busca (thread de trabalho) ----

    private Now load() {
        Location here = location();
        if (here == null) return null;
        try {
            String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                    + "&current=temperature_2m,weather_code,is_day",
                here.getLatitude(), here.getLongitude());
            JSONObject current = new JSONObject(get(url)).getJSONObject("current");
            float celsius = (float) current.getDouble("temperature_2m");
            int code = current.optInt("weather_code", 0);
            boolean day = current.optInt("is_day", 1) == 1;
            prefs().edit().putFloat("c", celsius).putInt("code", code).putBoolean("day", day)
                .putLong("at", System.currentTimeMillis()).apply();
            return new Now(celsius, icon(code, day));
        } catch (Exception e) {
            Log.w(TAG, "tempo: " + e);
            return null;
        }
    }

    /** Última posição conhecida (a central mantém o GPS ligado; a rede serve de reserva). */
    @SuppressLint("MissingPermission")
    private Location location() {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception ignored) {
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try {
            if (con.getResponseCode() != 200) throw new java.io.IOException("HTTP " + con.getResponseCode());
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0 && out.size() < 64 * 1024) out.write(b, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            con.disconnect();
        }
    }

    // ---- Guardado ----

    private Now cached() {
        SharedPreferences p = prefs();
        if (System.currentTimeMillis() - p.getLong("at", 0) > MAX_AGE_MS) return null;
        return new Now(p.getFloat("c", 0), icon(p.getInt("code", 0), p.getBoolean("day", true)));
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Código WMO do Open-Meteo → ícone: limpo (sol/lua), nublado, chuva/tempestade. */
    private static IconView.Icon icon(int code, boolean day) {
        if (code <= 1) return day ? IconView.Icon.SUN : IconView.Icon.MOON;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95) return IconView.Icon.RAIN;
        return IconView.Icon.CLOUD;
    }
}
