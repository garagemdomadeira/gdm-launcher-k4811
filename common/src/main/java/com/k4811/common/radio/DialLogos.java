package com.k4811.common.radio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logos das estações (mesma lógica do rádio da central: dial da cidade no TudoRádio e Radio Browser). Arquivo próprio tem preferência: {@code K4811/radio/FM_96.1.png} (ou .jpg) na
 * memória interna. Sem arquivo, baixa automaticamente da internet (TudoRádio Dials + Radio Browser)
 * usando a frequência, a localização da central (GPS + geocodificação reversa HTTP/Geocoder, com
 * fallback por IP quando sem GPS) e/ou o nome RDS.
 */
final class DialLogos {
    private static final String TAG = "K4811RadioLogos";
    private static final String FOLDER = "K4811/radio";
    private static final String[] SERVERS = {"all.api.radio-browser.info", "de1.api.radio-browser.info",
        "fi1.api.radio-browser.info"};
    private static final String USER_AGENT = "K4811-Radio/2.0 (central multimidia NWD)";
    private static final String BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36";
    private static final String MISSES = ".sem_logo.txt";
    private static final String MISS_PREFIX = "v3:";
    private static final long RETRY_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long PLACE_TTL_MS = 10L * 60 * 1000;
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_IMAGE = 2 * 1024 * 1024;
    private static final int SAVE_SIZE = 256;
    private static final double NEAR_KM = 150;

    private static final String[][] STATES = {
        {"ac", "acre"}, {"al", "alagoas"}, {"ap", "amapa"}, {"am", "amazonas"}, {"ba", "bahia"},
        {"ce", "ceara"}, {"df", "distrito federal"}, {"es", "espirito santo"}, {"go", "goias"},
        {"ma", "maranhao"}, {"mt", "mato grosso"}, {"ms", "mato grosso do sul"}, {"mg", "minas gerais"},
        {"pa", "para"}, {"pb", "paraiba"}, {"pr", "parana"}, {"pe", "pernambuco"}, {"pi", "piaui"},
        {"rj", "rio de janeiro"}, {"rn", "rio grande do norte"}, {"rs", "rio grande do sul"},
        {"ro", "rondonia"}, {"rr", "roraima"}, {"sc", "santa catarina"}, {"sp", "sao paulo"},
        {"se", "sergipe"}, {"to", "tocantins"}};

    private static final Pattern TR_CITY = Pattern.compile(
        "\\\\\"id\\\\\":\\\\\"(\\d+)\\\\\",\\\\\"nome\\\\\":\\\\\"([^\\\\\"]+)\\\\\",\\\\\"coordenadas\\\\\":\\[([-\\d.]+),([-\\d.]+)\\],\\\\\"regionId\\\\\"");
    private static final Pattern TR_TOTAL_PAGES = Pattern.compile("\\\\\"totalPages\\\\\":(\\d+)");
    private static final Pattern TR_STATION = Pattern.compile(
        "\\\\\"nome\\\\\":\\\\\"([^\\\\\"]+)\\\\\",\\\\\"estacao\\\\\":([\\d.]+),\\\\\"frequencia\\\\\":\\\\\"(fm|am)\\\\\",\\\\\"tipo\\\\\":\\\\\"[^\\\\\"]*\\\\\",\\\\\"sinal\\\\\":\\\\\"([^\\\\\"]+)\\\\\"[^}]*?\\\\\"logo\\\\\":\\\\\"(/assets/[a-f0-9-]+)\\\\\"");

    private static final LruCache<String, Bitmap> memory = new LruCache<>(24);
    private static final Set<String> triedWithoutPlace = new HashSet<>();
    private static Place cachedPlace;
    private static long cachedPlaceTime;
    private static String cachedDialKey;
    private static List<TrStation> cachedDialStations;

    private DialLogos() {
    }

    static Bitmap cached(String base) {
        return memory.get(base);
    }

    /** Arquivo da estação reduzido a ~size px, ou null. */
    static Bitmap load(Context c, String base, int size) {
        for (File d : dirs(c)) {
            for (String ext : new String[] {".png", ".jpg", ".jpeg", ".webp"}) {
                File f = new File(d, base + ext);
                if (!f.isFile()) continue;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int sample = 1;
                while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= size) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (b != null) {
                    memory.put(base, b);
                    return b;
                }
            }
        }
        return null;
    }

    /** Pastas lidas, na ordem: a do usuário (compartilhada) e a do app (quando a outra não dá para gravar). */
    private static Set<File> dirs(Context c) {
        Set<File> out = new LinkedHashSet<>();
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null) out.add(new File(ext, FOLDER));
        File own = c.getExternalFilesDir("logos");
        if (own != null) out.add(own);
        return out;
    }

    private static File writableDir(Context c) {
        for (File d : dirs(c)) {
            if ((d.isDirectory() || d.mkdirs()) && d.canWrite()) return d;
        }
        return null;
    }

    /** Procura e grava o logo; true se gravou um arquivo novo. */
    /** Estação a procurar: FM em centésimos de MHz (9610), AM em kHz; {@code name} = RDS, pode ser null. */
    static final class Station {
        final boolean fm;
        final int frequency;
        final String base;
        final String name;

        Station(boolean fm, int frequency, String name) {
            this.fm = fm;
            this.frequency = frequency;
            this.name = name == null || name.trim().length() < 3 ? null : name.trim();
            this.base = (fm ? "FM_" : "AM_") + value();
        }

        String value() {
            return Band.formatValue(frequency, fm);
        }
    }

    static boolean fetch(Context c, Station st) {
        // AM no Brasil é de 10 em 10 kHz: 603, 999, 1404 são memórias de fábrica da NWD (grade europeia
        // de 9 kHz), não existem aqui. Não busca nem anota (se a região do rádio mudar, voltam a valer).
        if (!st.fm && st.frequency % 10 != 0) return false;
        File dir = writableDir(c);
        if (dir == null) return false;
        String attempt = st.base + "|" + st.name;
        synchronized (triedWithoutPlace) {
            if (triedWithoutPlace.contains(attempt)) return false;
        }
        if (recentMiss(dir, st.base)) return false;
        try {
            String freq = st.value();
            Place here = place(c);

            List<Candidate> candidates = collectCandidates(freq, st.fm, st.name, here, false);
            for (Candidate cand : candidates) {
                Bitmap logo = download(cand.logoUrl);
                if (logo != null && save(dir, st.base, logo)) {
                    Log.i(TAG, st.base + ": logo de \"" + cand.name + "\" (" + cand.source + ")");
                    return true;
                }
            }
            if (!candidates.isEmpty()) {
                for (Candidate cand : collectCandidates(freq, st.fm, st.name, here, true)) {
                    Bitmap logo = download(cand.logoUrl);
                    if (logo != null && save(dir, st.base, logo)) {
                        Log.i(TAG, st.base + ": logo de \"" + cand.name + "\" (" + cand.source + ")");
                        return true;
                    }
                }
            }

            Log.i(TAG, st.base + ": sem logo (" + candidates.size() + " candidatos"
                + (here == null ? ", sem localização" : ", " + here.city + "/" + here.uf.toUpperCase(Locale.ROOT)) + ")");
            if (here != null) {
                markMiss(dir, st.base);
            } else {
                synchronized (triedWithoutPlace) {
                    triedWithoutPlace.add(attempt);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, st.base + ": " + e);
        }
        return false;
    }

    // ---- Candidatos (TudoRádio Dials por cidade/coordenadas + Radio Browser) ----

    private static final class Candidate {
        final String name;
        final String logoUrl;
        final String source;
        final int score;

        Candidate(String name, String logoUrl, String source, int score) {
            this.name = name;
            this.logoUrl = logoUrl;
            this.source = source;
            this.score = score;
        }
    }

    private static final class TrStation {
        final String name;
        final String freq;
        final boolean fm;
        final String signal;
        final String logoUrl;

        TrStation(String name, String freq, boolean fm, String signal, String logoUrl) {
            this.name = name;
            this.freq = freq;
            this.fm = fm;
            this.signal = signal;
            this.logoUrl = logoUrl;
        }
    }

    private static List<Candidate> collectCandidates(String freq, boolean fm, String ps, Place here, boolean onlyRadioBrowser) {
        List<Candidate> all = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        Pattern freqPattern = Pattern.compile("(?<![0-9.,])" + freq.replace(".", "[.,]") + "(?![0-9])");
        String psNorm = ps == null ? "" : cleanPs(ps);

        // 1. TudoRádio (dial completo da cidade ou município mais próximo por GPS em qualquer estado do Brasil)
        if (!onlyRadioBrowser && here != null) {
            for (Candidate c : searchTudoRadio(freq, fm, psNorm, here)) {
                if (seenUrls.add(c.logoUrl)) all.add(c);
            }
        }

        // 2. Radio Browser (fallback internacional ou quando sem localização e com nome RDS)
        if (onlyRadioBrowser || all.isEmpty()) {
            for (Candidate c : searchRadioBrowser(freq, fm, psNorm, here, freqPattern)) {
                if (seenUrls.add(c.logoUrl)) all.add(c);
            }
        }

        for (int i = 0; i < all.size() - 1; i++) {
            for (int j = i + 1; j < all.size(); j++) {
                if (all.get(j).score > all.get(i).score) {
                    Candidate tmp = all.get(i);
                    all.set(i, all.get(j));
                    all.set(j, tmp);
                }
            }
        }
        return all;
    }

    private static List<Candidate> searchTudoRadio(String freq, boolean fm, String psNorm, Place here) {
        List<Candidate> out = new ArrayList<>();
        try {
            List<TrStation> dial = getTudoRadioDial(here);
            for (TrStation s : dial) {
                if (s.fm != fm || !s.freq.equals(freq)) continue;
                int score;
                if ("LOCAL".equalsIgnoreCase(s.signal)) score = 98;
                else if ("GOOD".equalsIgnoreCase(s.signal)) score = 94;
                else if ("FAIR".equalsIgnoreCase(s.signal)) score = 88;
                else score = 82;
                if (psNorm.length() >= 3 && matchesName(norm(s.name), psNorm)) {
                    score += 5;
                }
                out.add(new Candidate(s.name, s.logoUrl, "tudoradio", score));
            }
        } catch (Exception e) {
            Log.w(TAG, "tudoradio (" + freq + "): " + e.getMessage());
        }
        return out;
    }

    private static synchronized List<TrStation> getTudoRadioDial(Place here) throws Exception {
        String key = here.uf + "|" + here.city + "|" + Math.round(here.lat * 20) + "|" + Math.round(here.lon * 20);
        if (cachedDialStations != null && key.equals(cachedDialKey)) {
            return cachedDialStations;
        }
        String ufUpper = here.uf.toUpperCase(Locale.ROOT);
        String bestId = null;
        String bestSlug = null;
        double bestKm = Double.MAX_VALUE;

        int totalPages = 1;
        for (int page = 1; page <= totalPages && page <= 6; page++) {
            String url = "https://tudoradio.com/dials/estado/" + ufUpper + (page > 1 ? "?page=" + page : "");
            String html = new String(get(url, 512 * 1024, true), "UTF-8");
            if (page == 1) {
                Matcher mp = TR_TOTAL_PAGES.matcher(html);
                if (mp.find()) {
                    try {
                        totalPages = Integer.parseInt(mp.group(1));
                    } catch (Exception ignored) {
                    }
                }
            }
            Matcher mc = TR_CITY.matcher(html);
            while (mc.find()) {
                String id = mc.group(1);
                String name = mc.group(2);
                String nameNorm = norm(name).trim();
                String slug = id + "-" + nameNorm.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
                if (nameNorm.equals(here.city)) {
                    bestId = id;
                    bestSlug = slug;
                    bestKm = 0;
                    break;
                }
                try {
                    double lat = Double.parseDouble(mc.group(3));
                    double lon = Double.parseDouble(mc.group(4));
                    if (!Double.isNaN(here.lat) && !Double.isNaN(here.lon)) {
                        float[] r = new float[1];
                        Location.distanceBetween(here.lat, here.lon, lat, lon, r);
                        double km = r[0] / 1000.0;
                        if (km < bestKm) {
                            bestKm = km;
                            bestId = id;
                            bestSlug = slug;
                        }
                    } else if (bestId == null && nameNorm.equals(here.metro)) {
                        bestId = id;
                        bestSlug = slug;
                    }
                } catch (Exception ignored) {
                }
            }
            if (bestKm == 0) break;
        }

        List<TrStation> stations = new ArrayList<>();
        if (bestSlug != null) {
            String dialUrl = "https://tudoradio.com/dials/cidade/" + bestSlug;
            String html = new String(get(dialUrl, 1024 * 1024, true), "UTF-8");
            Matcher ms = TR_STATION.matcher(html);
            while (ms.find()) {
                String name = ms.group(1);
                String est = ms.group(2);
                boolean isFm = "fm".equalsIgnoreCase(ms.group(3));
                String sig = ms.group(4);
                String asset = ms.group(5);
                String logoUrl = "https://tudoradio.com" + asset + "?width=256&quality=85&format=png";
                stations.add(new TrStation(name, est, isFm, sig, logoUrl));
            }
            Log.i(TAG, "Dial carregado: " + bestSlug + " (" + stations.size() + " estações)");
        }
        cachedDialKey = key;
        cachedDialStations = stations;
        return stations;
    }

    private static List<Candidate> searchRadioBrowser(String freq, boolean fm, String psNorm, Place here, Pattern freqPattern) {
        List<Candidate> out = new ArrayList<>();
        try {
            List<JSONObject> list = queryRadioBrowser(freq, fm);
            List<JSONObject> inState = new ArrayList<>();
            for (JSONObject s : list) {
                String name = s.optString("name");
                String fav = s.optString("favicon").trim();
                if (fav.isEmpty() || isSvgUrl(fav) || !freqPattern.matcher(name).find()) continue;
                String nn = norm(name);
                if (!fm && !nn.contains("am")) continue;

                boolean matchesPs = psNorm.length() >= 3 && matchesName(nn, psNorm);
                boolean sameSt = here != null && sameState(s, here);
                if (sameSt) inState.add(s);

                if (matchesPs && sameSt) {
                    out.add(new Candidate(name, fav, "radio-browser", 96));
                } else if (matchesPs && here == null) {
                    out.add(new Candidate(name, fav, "radio-browser", 80));
                }
            }
            if (here != null) {
                for (JSONObject s : inState) {
                    String name = s.optString("name");
                    String fav = s.optString("favicon").trim();
                    String text = norm(name + " " + s.optString("state") + " " + s.optString("tags"));
                    if (here.city.length() > 2 && text.contains(here.city)) {
                        out.add(new Candidate(name, fav, "radio-browser", 92));
                        continue;
                    }
                    if (here.metro.length() > 2 && text.contains(here.metro)) {
                        out.add(new Candidate(name, fav, "radio-browser", 86));
                        continue;
                    }
                    double km = distance(s, here);
                    if (km <= NEAR_KM) {
                        int distScore = 84 - (int) Math.min(10, km / 15.0);
                        out.add(new Candidate(name, fav, "radio-browser", distScore));
                    } else if (inState.size() == 1 || Double.isNaN(s.optDouble("geo_lat", Double.NaN))) {
                        out.add(new Candidate(name, fav, "radio-browser", 65));
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "radio-browser (" + freq + "): " + e.getMessage());
        }
        return out;
    }

    private static List<JSONObject> queryRadioBrowser(String freq, boolean fm) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] forms = fm ? new String[] {freq, freq.replace('.', ',')} : new String[] {freq};
        Exception last = null;
        for (String server : SERVERS) {
            try {
                for (String q : forms) {
                    String url = "https://" + server + "/json/stations/search?countrycode=BR&hidebroken=true&limit=100&name="
                        + URLEncoder.encode(q, "UTF-8");
                    JSONArray a = new JSONArray(new String(get(url, 512 * 1024, false), "UTF-8"));
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject s = a.getJSONObject(i);
                        if (seen.add(s.optString("stationuuid"))) out.add(s);
                    }
                }
                return out;
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return out;
    }

    private static String cleanPs(String ps) {
        String s = norm(ps).replaceAll("\\b(fm|am|mhz|khz|radio|zy[a-z0-9]+)\\b", " ").replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? norm(ps).trim() : s;
    }

    private static boolean matchesName(String stationNorm, String psNorm) {
        if (psNorm.length() < 3) return false;
        if (stationNorm.contains(psNorm)) return true;
        String noSpaces = stationNorm.replace(" ", "");
        String psNoSpaces = psNorm.replace(" ", "");
        return psNoSpaces.length() >= 4 && noSpaces.contains(psNoSpaces);
    }

    private static boolean sameState(JSONObject s, Place here) {
        String st = norm(s.optString("state"));
        String name = norm(s.optString("name"));
        String tags = norm(s.optString("tags"));
        if (st.contains(here.state) || (here.city.length() > 2 && st.contains(here.city))) return true;
        if (!here.metro.isEmpty() && st.contains(here.metro)) return true;
        if (st.trim().equals(here.uf) || tags.contains(here.state)) return true;
        return Pattern.compile("[-(/,]\\s*" + here.uf + "\\b").matcher(name).find()
            || Pattern.compile("\\b" + here.uf + "\\s*\\)").matcher(name).find()
            || Pattern.compile("\\b" + here.uf + "\\b").matcher(st).find();
    }

    private static double distance(JSONObject s, Place here) {
        if (Double.isNaN(here.lat) || Double.isNaN(here.lon)) return Double.MAX_VALUE;
        double lat = s.optDouble("geo_lat", Double.NaN);
        double lon = s.optDouble("geo_long", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon) || (lat == 0 && lon == 0)) return Double.MAX_VALUE;
        float[] r = new float[1];
        Location.distanceBetween(here.lat, here.lon, lat, lon, r);
        return r[0] / 1000.0;
    }

    // ---- Onde a central está (GPS + Geocoder / Reverse Geocode HTTP / IP Fallback) ----

    private static final class Place {
        String state = "";
        String uf = "";
        String city = "";
        String metro = "";
        double lat = Double.NaN;
        double lon = Double.NaN;
        boolean fromGps;
    }

    private static synchronized Place place(Context c) {
        long now = System.currentTimeMillis();
        if (cachedPlace != null && now - cachedPlaceTime < PLACE_TTL_MS) {
            if (cachedPlace.fromGps || lastKnownLocation(c) == null) {
                return cachedPlace;
            }
        }
        Place p = resolvePlace(c);
        if (p != null) {
            cachedPlace = p;
            cachedPlaceTime = now;
        }
        return p;
    }

    private static Place resolvePlace(Context c) {
        Location best = lastKnownLocation(c);
        if (best != null) {
            Place fromGeo = placeFromAndroidGeocoder(c, best.getLatitude(), best.getLongitude());
            if (fromGeo != null) {
                fromGeo.fromGps = true;
                return fromGeo;
            }

            Place fromHttp = placeFromReverseGeocodeHttp(best.getLatitude(), best.getLongitude());
            if (fromHttp != null) {
                fromHttp.fromGps = true;
                return fromHttp;
            }
        }
        // Sem GPS fixado (garagem, boot recente, emulador) ou sem geocoder: descobre pela conexão de internet (IP)
        Place fromIp = placeFromIp();
        if (fromIp != null) {
            if (best != null) {
                fromIp.lat = best.getLatitude();
                fromIp.lon = best.getLongitude();
                fromIp.fromGps = true;
            }
            return fromIp;
        }
        return null;
    }

    private static Location lastKnownLocation(Context c) {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            List<String> providers = new ArrayList<>();
            try {
                providers.addAll(lm.getProviders(true));
            } catch (Exception ignored) {
            }
            for (String p : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
                if (!providers.contains(p)) providers.add(p);
            }
            for (String p : providers) {
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

    private static Place placeFromAndroidGeocoder(Context c, double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return null;
            List<Address> a = new Geocoder(c, new Locale("pt", "BR")).getFromLocation(lat, lon, 1);
            if (a == null || a.isEmpty() || a.get(0).getAdminArea() == null) return null;
            String admin = a.get(0).getAdminArea();
            String city = a.get(0).getLocality();
            if (city == null || city.isEmpty()) city = a.get(0).getSubAdminArea();
            return buildPlace(admin, "", city, "", lat, lon);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Place placeFromReverseGeocodeHttp(double lat, double lon) {
        try {
            String url = String.format(Locale.US,
                "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.5f&longitude=%.5f&localityLanguage=pt",
                lat, lon);
            JSONObject d = new JSONObject(new String(get(url, 64 * 1024, false), "UTF-8"));
            String state = d.optString("principalSubdivision");
            String code = d.optString("principalSubdivisionCode");
            if (code.startsWith("BR-")) code = code.substring(3);
            String locality = d.optString("locality");
            String city = d.optString("city");
            String mainCity = !locality.isEmpty() ? locality : city;
            String metro = extractMetroCity(city);
            if (metro.isEmpty()) {
                JSONObject info = d.optJSONObject("localityInfo");
                if (info != null) {
                    JSONArray admins = info.optJSONArray("administrative");
                    if (admins != null) {
                        for (int i = 0; i < admins.length(); i++) {
                            String m = extractMetroCity(admins.getJSONObject(i).optString("name"));
                            if (!m.isEmpty()) {
                                metro = m;
                                break;
                            }
                        }
                    }
                }
            }
            return buildPlace(state, code, mainCity, metro, lat, lon);
        } catch (Exception e) {
            Log.w(TAG, "reverse-geocode: " + e.getMessage());
            return null;
        }
    }

    private static Place placeFromIp() {
        try {
            JSONObject d = new JSONObject(new String(get("https://ipwho.is/?lang=pt", 32 * 1024, false), "UTF-8"));
            if (d.optBoolean("success", false) && "BR".equalsIgnoreCase(d.optString("country_code"))) {
                Place p = buildPlace(
                    d.optString("region"),
                    d.optString("region_code"),
                    d.optString("city"),
                    "",
                    d.optDouble("latitude", Double.NaN),
                    d.optDouble("longitude", Double.NaN));
                if (p != null) return p;
            }
        } catch (Exception ignored) {
        }
        try {
            // Reserva também por HTTPS (a localização da central não trafega sem criptografia).
            JSONObject d = new JSONObject(new String(get("https://ipapi.co/json/", 32 * 1024, false), "UTF-8"));
            if ("BR".equalsIgnoreCase(d.optString("country_code"))) {
                return buildPlace(
                    d.optString("region"),
                    d.optString("region_code"),
                    d.optString("city"),
                    "",
                    d.optDouble("latitude", Double.NaN),
                    d.optDouble("longitude", Double.NaN));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String extractMetroCity(String s) {
        if (s == null) return "";
        String n = norm(s).trim();
        for (String prefix : new String[] {
            "regiao metropolitana de ", "regiao metropolitana do ", "regiao metropolitana da ",
            "regiao geografica imediata de ", "regiao geografica intermediaria de "}) {
            if (n.startsWith(prefix)) {
                return n.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static Place buildPlace(String rawState, String rawUf, String rawCity, String rawMetro, double lat, double lon) {
        String st = rawState == null ? "" : norm(rawState)
            .replace("state of ", "").replace("estado de ", "").replace("estado do ", "").trim();
        String uf = rawUf == null ? "" : norm(rawUf).trim();
        String matchedUf = "";
        String matchedState = "";
        for (String[] s : STATES) {
            if (s[0].equals(uf) || s[0].equals(st) || s[1].equals(st)) {
                matchedUf = s[0];
                matchedState = s[1];
                break;
            }
        }
        if (matchedUf.isEmpty()) return null;
        Place p = new Place();
        p.uf = matchedUf;
        p.state = matchedState;
        p.city = rawCity == null ? "" : extractMetroCity(rawCity).isEmpty() ? norm(rawCity).trim() : extractMetroCity(rawCity);
        p.metro = rawMetro == null ? "" : norm(rawMetro).trim();
        if (p.metro.isEmpty() && !Double.isNaN(lat) && !Double.isNaN(lon)) {
            float[] r = new float[1];
            Location.distanceBetween(lat, lon, -23.5505, -46.6333, r);
            if ("sp".equals(p.uf) && r[0] <= 65000) p.metro = "sao paulo";
        }
        p.lat = lat;
        p.lon = lon;
        return p;
    }

    // ---- Arquivos ----

    private static boolean recentMiss(File dir, String base) {
        File f = new File(dir, MISSES);
        if (!f.isFile()) return false;
        String key = MISS_PREFIX + base;
        try (InputStream in = new FileInputStream(f)) {
            for (String line : new String(readAll(in, 256 * 1024), "UTF-8").split("\n")) {
                String[] p = line.trim().split(" ");
                if (p.length == 2 && p[0].equals(key)
                    && System.currentTimeMillis() - Long.parseLong(p[1]) < RETRY_MS) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void markMiss(File dir, String base) {
        try (OutputStream out = new FileOutputStream(new File(dir, MISSES), true)) {
            out.write((MISS_PREFIX + base + " " + System.currentTimeMillis() + "\n").getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    /** Quadrado com fundo branco (logo transparente fica legível no fundo escuro), em PNG. */
    private static boolean save(File dir, String base, Bitmap logo) {
        try {
            int side = Math.max(logo.getWidth(), logo.getHeight());
            float k = Math.min(1f, SAVE_SIZE / (float) side);
            int s = Math.round(side * k);
            Bitmap sq = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(sq);
            cv.drawColor(0xFFFFFFFF);
            int w = Math.round(logo.getWidth() * k);
            int h = Math.round(logo.getHeight() * k);
            cv.drawBitmap(logo, null, new Rect((s - w) / 2, (s - h) / 2, (s + w) / 2, (s + h) / 2),
                new Paint(Paint.FILTER_BITMAP_FLAG));
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

    private static boolean isSvgUrl(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".svg");
    }

    private static Bitmap download(String url) {
        if (url == null || !url.startsWith("http") || isSvgUrl(url)) return null;
        try {
            byte[] b = get(url, MAX_IMAGE, true);
            Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
            return bmp != null && Math.min(bmp.getWidth(), bmp.getHeight()) >= 48 ? bmp : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] get(String url, int max, boolean browserUa) throws Exception {
        for (int hop = 0; hop < 4; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);
            con.setRequestProperty("User-Agent", browserUa ? BROWSER_UA : USER_AGENT);
            con.setInstanceFollowRedirects(true);
            int code = con.getResponseCode();
            if (code / 100 == 3 && con.getHeaderField("Location") != null) {
                url = new URL(new URL(url), con.getHeaderField("Location")).toString();
                con.disconnect();
                continue;
            }
            if (code != 200) {
                con.disconnect();
                throw new java.io.IOException("HTTP " + code);
            }
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

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
