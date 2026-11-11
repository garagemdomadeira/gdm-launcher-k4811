package com.k4811.common.config;

import android.content.Context;

import com.k4811.common.ui.DockView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Layout do dock: a lista ordenada de slots que todos os apps da suíte mostram. Cada slot é um
 * <em>token</em>:
 * <ul>
 *   <li>um id de {@link DockView.Item} (ex.: {@code home}, {@code radio}, {@code apps}) — o item
 *       embutido, com o comportamento da suíte;</li>
 *   <li>um <em>package</em> de app (ex.: {@code com.waze}) — atalho simples que só abre o app.</li>
 * </ul>
 *
 * <p>Fica em {@code /sdcard/nwd-configs/dock.json}. Diferente do {@code launcher.json} (só o
 * car-setting grava), este é editado <strong>pelo launcher</strong> quando o usuário arrasta um app
 * da gaveta para o dock — um escritor por arquivo, sem corrida. Os outros apps só leem.
 *
 * <p>Início e Apps ficam sempre no dock (pontas) e não são substituíveis, para não trancar o
 * usuário fora da tela inicial nem da gaveta.
 */
public final class DockConfig {
    private DockConfig() {
    }

    public static final String FILE = "dock.json";
    private static final String KEY_SLOTS = "slots";

    /** Tokens que não podem ser substituídos por arraste (pontas fixas). */
    public static boolean isFixed(String token) {
        return DockView.Item.HOME.id.equals(token) || DockView.Item.APPS.id.equals(token);
    }

    private static JSONObject cached;
    private static long cachedModified = -1;

    private static synchronized JSONObject json() {
        File f = NwdConfigs.file(FILE);
        long modified = f.lastModified();
        if (modified != cachedModified) {
            cachedModified = modified;
            cached = NwdConfigs.read(f);
        }
        return cached;
    }

    /**
     * Slots do dock, em ordem. Sem {@code dock.json}, monta o padrão a partir dos itens ligados no
     * {@code launcher.json} (compatibilidade com a seleção antiga): {@code home}, os itens, {@code apps}.
     */
    public static List<String> slots(Context c) {
        JSONObject j = json();
        JSONArray arr = j == null ? null : j.optJSONArray(KEY_SLOTS);
        List<String> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String t = arr.optString(i, null);
                if (t != null && !t.isEmpty()) out.add(t);
            }
            ensureEnds(out);
            return out;
        }
        // Padrão: Início + itens ligados (ordem do enum) + Apps.
        out.add(DockView.Item.HOME.id);
        Set<DockView.Item> toggles = LauncherConfig.dockToggles(c);
        for (DockView.Item item : DockView.Item.values()) {
            if (item.toggleable && toggles.contains(item)) out.add(item.id);
        }
        out.add(DockView.Item.APPS.id);
        return out;
    }

    /** Garante Início no começo e Apps no fim (pontas fixas), sem duplicar. */
    private static void ensureEnds(List<String> slots) {
        slots.remove(DockView.Item.HOME.id);
        slots.remove(DockView.Item.APPS.id);
        slots.add(0, DockView.Item.HOME.id);
        slots.add(DockView.Item.APPS.id);
    }

    /** Máximo de itens no dock, contando Início e Apps (sobram {@code MAX_ITEMS - 2} no meio). */
    public static final int MAX_ITEMS = 7;

    private static int middleCount(List<String> slots) {
        return Math.max(0, slots.size() - 2); // tira Início e Apps
    }

    /** true se o dock já está no limite (não cabe mais um app novo no meio). */
    public static boolean isFull(Context c) {
        return middleCount(slots(c)) >= MAX_ITEMS - 2;
    }

    // ------------------------------------------------------------------ gravação (só o launcher)

    /**
     * Coloca {@code token} no dock na posição {@code atIndex} (entre Início e Apps). Se o app já
     * estiver no dock, só o move. Se houver espaço, insere; se o dock estiver cheio, substitui o
     * ícone do slot alvo (não passa de {@link #MAX_ITEMS}). false só se não der para colocar.
     */
    public static synchronized boolean insert(Context c, int atIndex, String token) {
        if (token == null || token.isEmpty() || isFixed(token)) return false;
        List<String> slots = slots(c);
        if (atIndex < 1) atIndex = 1;
        if (atIndex > slots.size() - 1) atIndex = slots.size() - 1;
        int existing = slots.indexOf(token);
        if (existing > 0 && existing < slots.size() - 1) { // já está no dock: mover
            slots.remove(existing);
            if (existing < atIndex) atIndex--;
        } else if (middleCount(slots) >= MAX_ITEMS - 2) {
            // Cheio: substitui o ícone do slot alvo (se for do meio). Em Início/Apps, não dá.
            if (atIndex >= 1 && atIndex <= slots.size() - 2 && !isFixed(slots.get(atIndex))) {
                slots.set(atIndex, token);
                return write(slots);
            }
            return false;
        }
        if (atIndex < 1) atIndex = 1;
        if (atIndex > slots.size() - 1) atIndex = slots.size() - 1;
        slots.add(atIndex, token);
        return write(slots);
    }

    /** Move o item de {@code from} para {@code to} (ambos entre Início e Apps). */
    public static synchronized boolean move(Context c, int from, int to) {
        List<String> slots = slots(c);
        if (from < 1 || from >= slots.size() - 1) return false; // Início/Apps não movem
        String token = slots.remove(from);
        // Solta sobre o slot alvo = assume a posição dele. Movendo para a direita, insere depois do
        // alvo (sem decrementar, senão um passo à direita viraria no-op).
        if (to < 1) to = 1;
        if (to > slots.size() - 1) to = slots.size() - 1;
        slots.add(to, token);
        return write(slots);
    }

    /** Remove o item na posição {@code index} (não mexe em Início/Apps). */
    public static synchronized boolean remove(Context c, int index) {
        List<String> slots = slots(c);
        if (index < 1 || index >= slots.size() - 1) return false;
        slots.remove(index);
        return write(slots);
    }

    private static boolean write(List<String> slots) {
        ensureEnds(slots);
        JSONObject next = new JSONObject();
        try {
            next.put(KEY_SLOTS, new JSONArray(slots));
        } catch (JSONException e) {
            return false;
        }
        boolean ok = NwdConfigs.write(NwdConfigs.file(FILE), next);
        cachedModified = -1;
        return ok;
    }
}
