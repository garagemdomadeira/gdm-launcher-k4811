package com.k4811.common.config;

import android.content.Context;

import com.k4811.common.home.HomeGrid;
import com.k4811.common.ui.DockView;
import com.k4811.common.ui.Palette;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configurações do suíte seguidas por todos os apps: idioma, cor do tema, fonte, ícones
 * do dock (e o nome do Z-Link) e esconder a barra da NWD; e as da tela inicial (módulos, imagem,
 * voltímetro, dia/noite, apagar a tela, perguntar pelo launcher padrão).
 *
 * Ficam em /sdcard/nwd-configs/launcher.json. Só o car-setting grava; o launcher e os apps leem.
 * Sem arquivo (ou com erro de leitura), valem os padrões.
 */
public final class LauncherConfig {
    private LauncherConfig() {
    }

    public static final String FILE = "launcher.json";
    public static final int VERSION = 2;
    private static final String KEY_VERSION = "version";
    private static final String KEY_DOCK_TOGGLES = "dock_toggles";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_FONT = "font";
    private static final String KEY_THEME = "theme";
    /** true = fundo escuro (padrão); false = fundo claro. */
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_VOLTMETER = "show_voltmeter";
    private static final String KEY_DAY_NIGHT = "show_day_night";
    private static final String KEY_SCREEN_OFF_AFTER = "screen_off_after";
    /** Antigo "mostrar controles de volume"; só serve para montar a tela inicial padrão. */
    private static final String KEY_VOLUME = "show_volume_controls";
    private static final String KEY_HOME_LAYOUT = "home_layout";
    private static final String KEY_HOME_IMAGE = "home_image";
    private static final String KEY_HOME_BG_IMAGE = "home_bg_image";
    private static final String KEY_MEDIA_APPS = "media_app_controls";
    private static final String KEY_HIDE_BOTTOM_BAR = "hide_bottom_bar";
    private static final String KEY_ZLINK_NAME = "zlink_name";
    private static final String KEY_ASK_DEFAULT = "ask_default_launcher";
    private static final String KEY_VOLUME_OVERLAY = "show_volume_overlay";
    /** Antigo liga/desliga do menu da borda; só lido quando ainda não há {@link #KEY_EDGE_MENU_SIDE}. */
    private static final String KEY_EDGE_MENU = "edge_menu";
    private static final String KEY_EDGE_MENU_SIDE = "edge_menu_side";

    /** Launcher original da NWD (valor de fábrica de persist.nwd.launcher.default). */
    public static final String ORIGINAL_LAUNCHER = "com.android.launcher";

    /** Nomes que o item Z-Link do dock pode ter. */
    public static final String[] ZLINK_NAMES = {"Z-Link", "CarPlay", "Auto"};

    /** Idiomas (tag), na ordem das Configurações. Para incluir um: tag aqui, nome em
     *  {@code LANGUAGE_NAMES} e {@code res/values-<tag>/} no common. */
    public static final String[] LANGUAGES = {"pt", "en", "es", "fr", "ru", "ja", "zh", "hi"};
    /** Nome de cada idioma escrito nele mesmo (com uma bandeira de referência). */
    public static final String[] LANGUAGE_NAMES = {
        "🇧🇷 Português", "🇺🇸 English", "🇪🇸 Español", "🇫🇷 Français",
        "🇷🇺 Русский", "🇯🇵 日本語", "🇨🇳 中文（普通话）", "🇮🇳 हिन्दी",
    };
    public static final String DEFAULT_LANGUAGE = "en";

    /** Fontes: prefixo dos arquivos em assets/fonts ({@code <id>-400/500/600.ttf}). */
    public static final String[] FONTS = {"Exo2", "Montserrat", "Rajdhani", "Outfit", "Inter", "PublicSans"};
    public static final String[] FONT_NAMES = {"Exo 2", "Montserrat", "Rajdhani", "Outfit", "Inter", "Public Sans"};
    public static final String DEFAULT_FONT = "Exo2";

    /** Opções de "Apagar a tela após" (segundos sem tocar na tela inicial); 0 = nunca. */
    public static final int[] SCREEN_OFF_AFTER = {0, 5, 10, 30};

    /** Máximo de itens opcionais no dock (Início e Apps são fixos e ficam fora da conta). */
    public static final int MAX_DOCK_TOGGLES = 5;
    public static final DockView.Item[] DEFAULT_DOCK = {
        DockView.Item.RADIO, DockView.Item.MUSIC, DockView.Item.BT_MUSIC, DockView.Item.PHONE, DockView.Item.EQ,
    };

    private static final String DEFAULT_HOME_LAYOUT =
        "now_playing:left,clock:center_top,volume:center_bottom,fan:right_top,bluetooth:right_middle";

    private static JSONObject cached;
    private static long cachedModified = -1;

    /** Arquivo atual. Só relê do disco quando a data de modificação muda. */
    private static synchronized JSONObject json() {
        File f = NwdConfigs.file(FILE);
        long modified = f.lastModified(); // 0 se não existe
        if (modified != cachedModified) {
            cachedModified = modified;
            cached = NwdConfigs.read(f);
        }
        return cached;
    }

    private static boolean bool(String key, boolean def) {
        JSONObject j = json();
        return j == null ? def : j.optBoolean(key, def);
    }

    private static int integer(String key, int def) {
        JSONObject j = json();
        return j == null ? def : j.optInt(key, def);
    }

    private static String string(String key, String def) {
        JSONObject j = json();
        if (j == null || !j.has(key) || j.isNull(key)) return def;
        return j.optString(key, def);
    }

    // ------------------------------------------------------------------ todos os apps

    /** Idioma escolhido; inglês quando nada foi escolhido ou o gravado não existe mais. */
    public static String language(Context c) {
        String tag = string(KEY_LANGUAGE, DEFAULT_LANGUAGE);
        for (String known : LANGUAGES) {
            if (known.equals(tag)) return tag;
        }
        return DEFAULT_LANGUAGE;
    }

    public static boolean setLanguage(Context c, String tag) {
        return put(KEY_LANGUAGE, tag == null ? DEFAULT_LANGUAGE : tag);
    }

    /** Fonte escolhida; Exo 2 quando nada foi escolhido ou a gravada não existe mais. */
    public static String font(Context c) {
        String id = string(KEY_FONT, DEFAULT_FONT);
        for (String known : FONTS) {
            if (known.equals(id)) return id;
        }
        return DEFAULT_FONT;
    }

    public static boolean setFont(Context c, String id) {
        return put(KEY_FONT, id == null ? DEFAULT_FONT : id);
    }

    /** Cor do tema: 0..Palette.THEMES-1, ou Palette.DEFAULT (tons de cinza). */
    public static int theme(Context c) {
        int t = integer(KEY_THEME, Palette.DEFAULT);
        return t >= 0 && t < Palette.THEMES ? t : Palette.DEFAULT;
    }

    public static boolean setTheme(Context c, int theme) {
        return put(KEY_THEME, theme);
    }

    /** Modo escuro (fundo escuro). Ligado por padrão; desligado deixa o fundo claro. */
    public static boolean darkMode(Context c) {
        return bool(KEY_DARK_MODE, true);
    }

    public static boolean setDarkMode(Context c, boolean on) {
        return put(KEY_DARK_MODE, on);
    }

    /** Itens opcionais ligados no dock, na ordem fixa do enum. Sem nada gravado, o padrão. */
    public static Set<DockView.Item> dockToggles(Context c) {
        Set<DockView.Item> out = new LinkedHashSet<>();
        JSONObject j = json();
        JSONArray ids = j == null ? null : j.optJSONArray(KEY_DOCK_TOGGLES);
        if (ids == null) {
            Collections.addAll(out, DEFAULT_DOCK);
            return out;
        }
        Set<String> set = new LinkedHashSet<>();
        for (int i = 0; i < ids.length(); i++) set.add(ids.optString(i));
        for (DockView.Item item : DockView.Item.values()) {
            if (item.toggleable && set.contains(item.id) && out.size() < MAX_DOCK_TOGGLES) out.add(item);
        }
        return out;
    }

    public static boolean setDockToggles(Context c, Set<DockView.Item> items) {
        JSONArray ids = new JSONArray();
        for (DockView.Item item : DockView.Item.values()) {
            if (item.toggleable && items.contains(item)) ids.put(item.id);
        }
        return put(KEY_DOCK_TOGGLES, ids);
    }

    public static boolean resetDockToggles(Context c) {
        return put(KEY_DOCK_TOGGLES, null);
    }

    /** Índice em {@link #ZLINK_NAMES} do nome do Z-Link no dock; Z-Link por padrão. */
    public static int zlinkName(Context c) {
        int i = integer(KEY_ZLINK_NAME, 0);
        return i >= 0 && i < ZLINK_NAMES.length ? i : 0;
    }

    public static boolean setZlinkName(Context c, int index) {
        return put(KEY_ZLINK_NAME, index);
    }

    /** Esconder a barra flutuante da NWD enquanto um app da suíte está na frente. Desligado por padrão. */
    public static boolean hideBottomBar(Context c) {
        return bool(KEY_HIDE_BOTTOM_BAR, false);
    }

    public static boolean setHideBottomBar(Context c, boolean on) {
        return put(KEY_HIDE_BOTTOM_BAR, on);
    }

    // ------------------------------------------------------------------ tela inicial

    public static boolean showVoltmeter(Context c) {
        return bool(KEY_VOLTMETER, true);
    }

    public static boolean setShowVoltmeter(Context c, boolean on) {
        return put(KEY_VOLTMETER, on);
    }

    /** Mostrar e controlar no launcher o que apps de áudio (Spotify, Deezer…) estão tocando. */
    public static boolean mediaAppControls(Context c) {
        return bool(KEY_MEDIA_APPS, false);
    }

    public static boolean setMediaAppControls(Context c, boolean on) {
        return put(KEY_MEDIA_APPS, on);
    }

    /** Sol/lua ao lado do voltímetro (lua = farol aceso). */
    public static boolean showDayNight(Context c) {
        return bool(KEY_DAY_NIGHT, true);
    }

    public static boolean setShowDayNight(Context c, boolean on) {
        return put(KEY_DAY_NIGHT, on);
    }

    public static int screenOffAfter(Context c) {
        return integer(KEY_SCREEN_OFF_AFTER, 0);
    }

    public static boolean setScreenOffAfter(Context c, int seconds) {
        return put(KEY_SCREEN_OFF_AFTER, seconds);
    }

    /**
     * Área de cada módulo da tela inicial; módulo fora do mapa fica escondido. Sem nada gravado,
     * a tela inicial original (o volume só se o antigo "mostrar controles de volume" estava ligado).
     */
    public static Map<HomeGrid.Module, HomeGrid.Slot> homeLayout(Context c) {
        Map<HomeGrid.Module, HomeGrid.Slot> out = new EnumMap<>(HomeGrid.Module.class);
        String raw = string(KEY_HOME_LAYOUT, null);
        if (raw == null) {
            out.put(HomeGrid.Module.NOW_PLAYING, HomeGrid.Slot.LEFT);
            out.put(HomeGrid.Module.CLOCK, HomeGrid.Slot.CENTER_TOP);
            if (bool(KEY_VOLUME, true)) out.put(HomeGrid.Module.VOLUME, HomeGrid.Slot.CENTER_BOTTOM);
            out.put(HomeGrid.Module.FAN, HomeGrid.Slot.RIGHT_TOP);
            out.put(HomeGrid.Module.BLUETOOTH, HomeGrid.Slot.RIGHT_MIDDLE);
            return out;
        }
        for (String pair : raw.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length != 2) continue;
            HomeGrid.Module m = HomeGrid.Module.byId(kv[0].trim());
            HomeGrid.Slot s = HomeGrid.Slot.byId(kv[1].trim());
            if (m != null && s != null && !out.containsValue(s)) out.put(m, s);
        }
        return out;
    }

    /** De volta à tela inicial original, com o volume. */
    public static boolean resetHomeLayout(Context c) {
        return put(KEY_HOME_LAYOUT, DEFAULT_HOME_LAYOUT);
    }

    /**
     * Põe {@code module} em {@code slot} (null = escondido). Se outro módulo já estiver lá, os dois
     * trocam de lugar (o outro fica escondido se {@code module} não tinha área).
     */
    public static boolean setHomeSlot(Context c, HomeGrid.Module module, HomeGrid.Slot slot) {
        Map<HomeGrid.Module, HomeGrid.Slot> layout = homeLayout(c);
        HomeGrid.Slot old = layout.remove(module);
        if (slot != null) {
            for (HomeGrid.Module other : HomeGrid.Module.values()) {
                if (layout.get(other) != slot) continue;
                if (old != null) layout.put(other, old);
                else layout.remove(other);
            }
            layout.put(module, slot);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<HomeGrid.Module, HomeGrid.Slot> e : layout.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().id).append(':').append(e.getValue().id);
        }
        return put(KEY_HOME_LAYOUT, sb.toString());
    }

    /** Mostrar o volume por cima de qualquer app quando ele muda (VolumeOverlayService do launcher). */
    public static boolean showVolumeOverlay(Context c) {
        return bool(KEY_VOLUME_OVERLAY, true);
    }

    public static boolean setShowVolumeOverlay(Context c, boolean on) {
        return put(KEY_VOLUME_OVERLAY, on);
    }

    /** Borda do menu Voltar | Vol− Vol+ | Início (EdgeMenuService do launcher), na ordem das Configurações. */
    public static final int EDGE_MENU_OFF = 0;
    public static final int EDGE_MENU_BOTTOM = 1;
    public static final int EDGE_MENU_LEFT = 2;
    public static final int EDGE_MENU_RIGHT = 3;
    public static final int[] EDGE_MENU_SIDES = {EDGE_MENU_OFF, EDGE_MENU_BOTTOM, EDGE_MENU_LEFT, EDGE_MENU_RIGHT};

    /** De qual borda o menu sai (deslizando dela para dentro); {@link #EDGE_MENU_OFF} = sem menu. */
    public static int edgeMenuSide(Context c) {
        int fallback = bool(KEY_EDGE_MENU, true) ? EDGE_MENU_BOTTOM : EDGE_MENU_OFF;
        int side = integer(KEY_EDGE_MENU_SIDE, fallback);
        return side >= EDGE_MENU_OFF && side <= EDGE_MENU_RIGHT ? side : fallback;
    }

    public static boolean setEdgeMenuSide(Context c, int side) {
        return put(KEY_EDGE_MENU_SIDE, side);
    }

    /** Caminho da imagem do módulo Imagem (ver HomeImage); null se nenhuma foi escolhida. */
    public static String homeImage(Context c) {
        return string(KEY_HOME_IMAGE, null);
    }

    public static boolean setHomeImage(Context c, String path) {
        return put(KEY_HOME_IMAGE, path);
    }

    /** Caminho da imagem de fundo da tela inicial (ver HomeImage); null se nenhuma foi escolhida. */
    public static String homeBgImage(Context c) {
        return string(KEY_HOME_BG_IMAGE, null);
    }

    public static boolean setHomeBgImage(Context c, String path) {
        return put(KEY_HOME_BG_IMAGE, path);
    }

    /**
     * Perguntar na abertura do launcher se ele deve virar o padrão. Desligado quando o usuário
     * escolhe outro launcher (para não insistir); religado quando escolhe este.
     */
    public static boolean askDefaultLauncher(Context c) {
        return bool(KEY_ASK_DEFAULT, true);
    }

    public static boolean setAskDefaultLauncher(Context c, boolean ask) {
        return put(KEY_ASK_DEFAULT, ask);
    }

    // ------------------------------------------------------------------ gravação (só o car-setting)

    /** Cria o arquivo com os padrões se ele ainda não existir. */
    public static boolean ensureExists(Context c) {
        if (NwdConfigs.file(FILE).isFile()) return true;
        return put(KEY_LANGUAGE, language(c));
    }

    private static synchronized boolean put(String key, Object value) {
        JSONObject current = json();
        JSONObject next;
        try {
            next = new JSONObject();
            if (current != null) {
                for (Iterator<String> it = current.keys(); it.hasNext(); ) {
                    String k = it.next();
                    next.put(k, current.get(k));
                }
            }
            next.put(KEY_VERSION, VERSION);
            if (value == null) next.remove(key);
            else next.put(key, value);
        } catch (JSONException e) {
            return false;
        }
        boolean ok = NwdConfigs.write(NwdConfigs.file(FILE), next);
        cachedModified = -1; // força reler na próxima consulta
        return ok;
    }
}
