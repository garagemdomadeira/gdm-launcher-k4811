package com.k4811.common.radio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Baixa sozinho o logo da estação sintonizada quando não há arquivo em {@code K4811/radio/}
 * ({@link StationArt}). Fonte: Radio Browser (catálogo aberto de rádios, api.radio-browser.info).
 * O catálogo não tem campo de frequência, mas as rádios brasileiras quase sempre têm a frequência no
 * nome ("Jovem Pan 100.9 FM", "Minas FM 104,1"). Para não pôr o logo de uma rádio de outra cidade na
 * mesma frequência, só aceita a estação do mesmo estado da central (localização + Geocoder) ou cujo
 * nome bata com o nome RDS. Na dúvida (várias no estado, nenhuma na cidade), não baixa nada.
 *
 * <p>Grava {@code FM_96.1.png} na memória interna do Android ({@code /sdcard/K4811/radio}), que o
 * {@link StationArt} já lê. Nunca sobrescreve um arquivo existente: quem copia o próprio logo manda.
 * Frequências sem resultado ficam anotadas em {@code .sem_logo.txt} e só são tentadas de novo depois
 * de 7 dias. Faz rede e disco: chamar fora da thread principal.
 */
public final class StationLogos {
    private static final String TAG = "K4811Logos";
    private static final String[] SERVERS = {"all.api.radio-browser.info", "de1.api.radio-browser.info",
        "fi1.api.radio-browser.info"};
    private static final String USER_AGENT = "K4811-Radio/2.0 (central multimidia NWD)";
    private static final String MISSES = ".sem_logo.txt";
    private static final long RETRY_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_IMAGE = 2 * 1024 * 1024;
    private static final int SAVE_SIZE = 512;
    /** Distância máxima (km) quando o catálogo tem as coordenadas da rádio. */
    private static final double NEAR_KM = 150;

    /** Siglas e nomes dos estados (sem acento, minúsculo), para casar com o campo "state" bagunçado. */
    private static final String[][] STATES = {
        {"ac", "acre"}, {"al", "alagoas"}, {"ap", "amapa"}, {"am", "amazonas"}, {"ba", "bahia"},
        {"ce", "ceara"}, {"df", "distrito federal"}, {"es", "espirito santo"}, {"go", "goias"},
        {"ma", "maranhao"}, {"mt", "mato grosso"}, {"ms", "mato grosso do sul"}, {"mg", "minas gerais"},
        {"pa", "para"}, {"pb", "paraiba"}, {"pr", "parana"}, {"pe", "pernambuco"}, {"pi", "piaui"},
        {"rj", "rio de janeiro"}, {"rn", "rio grande do norte"}, {"rs", "rio grande do sul"},
        {"ro", "rondonia"}, {"rr", "roraima"}, {"sc", "santa catarina"}, {"sp", "sao paulo"},
        {"se", "sergipe"}, {"to", "tocantins"}};

    private static final Set<String> running = new HashSet<>();
    /** Tentadas nesta execução sem localização: de novo só quando o app abrir outra vez. */
    private static final Set<String> triedWithoutPlace = new HashSet<>();

    private StationLogos() {
    }

    /**
     * Procura e grava o logo; devolve true se gravou um arquivo novo (o chamador recarrega a arte).
     * {@code psName} = nome RDS, se houver.
     */
    public static boolean fetch(Context c, int band, int frequency, String psName) {
        if (frequency <= 0) return false;
        String base = StationArt.baseName(band, frequency);
        File dir = new File(Environment.getExternalStorageDirectory(), "K4811/radio");
        synchronized (running) {
            if (!running.add(base)) return false;
        }
        try {
            if (exists(base) || recentMiss(dir, base)) return false;
            synchronized (running) {
                if (triedWithoutPlace.contains(base)) return false;
            }
            // Dial da cidade/região no TudoRádio (frequência + localização), com o Radio Browser de reserva.
            return DialLogos.fetch(c, new DialLogos.Station(Band.isFm(band), frequency, psName));
        } catch (Exception e) {
            // Sem internet: tenta de novo na próxima vez que sintonizar (não anota).
            Log.w(TAG, base + ": " + e);
            return false;
        } finally {
            synchronized (running) {
                running.remove(base);
            }
        }
    }

    // ---- Catálogo ----

    /** Estações do Brasil com a frequência no nome ("96.1" e "96,1"). */
    private static List<JSONObject> search(String freq, boolean fm) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] forms = fm ? new String[] {freq, freq.replace('.', ',')} : new String[] {freq};
        Exception last = null;
        for (String server : SERVERS) {
            try {
                for (String q : forms) {
                    String url = "https://" + server + "/json/stations/search?countrycode=BR&hidebroken=true&limit=100&name="
                        + URLEncoder.encode(q, "UTF-8");
                    JSONArray a = new JSONArray(new String(get(url, 512 * 1024), "UTF-8"));
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject s = a.getJSONObject(i);
                        if (seen.add(s.optString("stationuuid"))) out.add(s);
                    }
                }
                return out;
            } catch (Exception e) {
                last = e;   // próximo servidor
            }
        }
        throw last;
    }

    /**
     * A estação certa: com logo, a frequência no nome como número inteiro (96.1 não casa 196.1), e
     * do mesmo lugar. Prefere a da cidade (ou a mais perto); várias no estado e nenhuma na cidade =
     * nenhuma.
     */
    private static JSONObject choose(List<JSONObject> list, Place here, String ps, String freq, boolean fm) {
        Pattern freqPattern = Pattern.compile("(?<![0-9.,])" + freq.replace(".", "[.,]") + "(?![0-9])");
        String psNorm = ps == null ? "" : norm(ps).trim();
        List<JSONObject> inState = new ArrayList<>();
        JSONObject byPs = null;
        for (JSONObject s : list) {
            String name = s.optString("name");
            if (s.optString("favicon").isEmpty() || !freqPattern.matcher(name).find()) continue;
            String nn = norm(name);
            if (!fm && !nn.contains("am")) continue;
            if (psNorm.length() >= 3 && nn.contains(psNorm) && byPs == null) byPs = s;
            if (here != null && sameState(s, here)) inState.add(s);
        }
        if (here != null) {
            JSONObject best = null;
            double bestKm = Double.MAX_VALUE;
            for (JSONObject s : inState) {
                String text = norm(s.optString("name") + " " + s.optString("state"));
                if (here.city.length() > 2 && text.contains(here.city)) return s;
                double km = distance(s, here);
                if (km < bestKm) {
                    bestKm = km;
                    best = s;
                }
            }
            if (best != null && bestKm <= NEAR_KM) return best;
            if (inState.size() == 1) return inState.get(0);
            if (byPs != null && inState.contains(byPs)) return byPs;
            return null;
        }
        return byPs;   // sem localização, só com o nome RDS
    }

    private static boolean sameState(JSONObject s, Place here) {
        String st = norm(s.optString("state"));
        String name = norm(s.optString("name"));
        if (st.contains(here.state) || (here.city.length() > 2 && st.contains(here.city))) return true;
        // "MG", "SP"... no campo de estado ou no fim do nome ("Januaria - MG", "Florianopolis - SC")
        if (st.trim().equals(here.uf)) return true;
        return Pattern.compile("[-(/,]\\s*" + here.uf + "\\b").matcher(name).find()
            || Pattern.compile("\\b" + here.uf + "\\s*\\)").matcher(name).find();
    }

    private static double distance(JSONObject s, Place here) {
        double lat = s.optDouble("geo_lat", Double.NaN);
        double lon = s.optDouble("geo_long", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon) || (lat == 0 && lon == 0)) return Double.MAX_VALUE;
        float[] r = new float[1];
        Location.distanceBetween(here.lat, here.lon, lat, lon, r);
        return r[0] / 1000.0;
    }

    // ---- Onde a central está ----

    private static final class Place {
        String state;
        String uf;
        String city;
        double lat;
        double lon;
    }

    private static Place place(Context c) {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception ignored) {
                    // provedor ausente ou sem permissão
                }
            }
            if (best == null || !Geocoder.isPresent()) return null;
            List<Address> a = new Geocoder(c, new Locale("pt", "BR")).getFromLocation(best.getLatitude(), best.getLongitude(), 1);
            if (a == null || a.isEmpty() || a.get(0).getAdminArea() == null) return null;
            Place p = new Place();
            p.state = norm(a.get(0).getAdminArea()).replace("state of ", "").replace("estado de ", "")
                .replace("estado do ", "").trim();
            p.uf = "";
            for (String[] s : STATES) {
                if (s[1].equals(p.state) || s[0].equals(p.state)) {
                    p.uf = s[0];
                    p.state = s[1];
                }
            }
            p.city = a.get(0).getLocality() == null ? "" : norm(a.get(0).getLocality()).trim();
            p.lat = best.getLatitude();
            p.lon = best.getLongitude();
            return p.uf.isEmpty() ? null : p;
        } catch (Exception e) {
            Log.w(TAG, "localização: " + e);
            return null;
        }
    }

    // ---- Arquivos ----

    private static boolean exists(String base) {
        for (File d : StationArt.dirs()) {
            for (String ext : new String[] {".jpg", ".png"}) {
                if (new File(d, base + ext).isFile()) return true;
            }
        }
        return false;
    }

    private static boolean recentMiss(File dir, String base) {
        File f = new File(dir, MISSES);
        if (!f.isFile()) return false;
        try (InputStream in = new java.io.FileInputStream(f)) {
            for (String line : new String(readAll(in, 256 * 1024), "UTF-8").split("\n")) {
                String[] p = line.trim().split(" ");
                if (p.length == 2 && p[0].equals(base)
                    && System.currentTimeMillis() - Long.parseLong(p[1]) < RETRY_MS) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void markMiss(File dir, String base) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            try (OutputStream out = new FileOutputStream(new File(dir, MISSES), true)) {
                out.write((base + " " + System.currentTimeMillis() + "\n").getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
    }

    /** Quadrado com fundo branco (logos com transparência ficam legíveis no tema escuro), em PNG. */
    private static boolean save(File dir, String base, Bitmap logo) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            int side = Math.max(logo.getWidth(), logo.getHeight());
            float k = Math.min(1f, SAVE_SIZE / (float) side);
            int s = Math.round(side * k);
            Bitmap sq = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(sq);
            cv.drawColor(0xFFFFFFFF);
            int w = Math.round(logo.getWidth() * k);
            int h = Math.round(logo.getHeight() * k);
            cv.drawBitmap(logo, null, new android.graphics.Rect((s - w) / 2, (s - h) / 2, (s + w) / 2, (s + h) / 2),
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
            File tmp = new File(dir, base + ".png.tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                sq.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return tmp.renameTo(new File(dir, base + ".png"));
        } catch (Exception e) {
            Log.w(TAG, "gravar " + base + ": " + e);
            return false;
        }
    }

    // ---- Rede ----

    private static Bitmap download(String url) {
        if (url == null || !url.startsWith("http")) return null;
        try {
            byte[] b = get(url, MAX_IMAGE);
            Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
            // Página no lugar da imagem, ou ícone minúsculo: não serve.
            return bmp != null && Math.min(bmp.getWidth(), bmp.getHeight()) >= 48 ? bmp : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] get(String url, int max) throws Exception {
        HttpURLConnection con = null;
        for (int hop = 0; hop < 4; hop++) {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setInstanceFollowRedirects(true);
            int code = con.getResponseCode();
            if (code / 100 == 3 && con.getHeaderField("Location") != null) {
                url = new URL(new URL(url), con.getHeaderField("Location")).toString();   // http↔https
                con.disconnect();
                continue;
            }
            if (code != 200) throw new java.io.IOException("HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                return readAll(in, max);
            } finally {
                con.disconnect();
            }
        }
        throw new java.io.IOException("redirecionamentos demais");
    }

    private static byte[] readAll(InputStream in, int max) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > max) throw new java.io.IOException("grande demais");
        }
        return out.toByteArray();
    }

    /** Sem acento, minúsculo. */
    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
