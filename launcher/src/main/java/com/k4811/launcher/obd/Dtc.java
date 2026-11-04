package com.k4811.launcher.obd;

import android.content.Context;

import com.k4811.launcher.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Código de defeito (DTC) lido da ECU: "P0301", confirmado (modo 03, o que acende a luz de
 * injeção) ou pendente (modo 07, detectado num ciclo e ainda não confirmado). Só leitura: nada
 * aqui apaga códigos (modo 04).
 */
public final class Dtc {

    public final String code;
    public final boolean pending;

    Dtc(String code, boolean pending) {
        this.code = code;
        this.pending = pending;
    }

    /** Chave de "já avisado": pendente e confirmado avisam cada um uma vez. */
    public String key() {
        return pending ? code + "p" : code;
    }

    public boolean misfire() {
        return code.startsWith("P030");
    }

    /**
     * Códigos de uma resposta do modo 03 ({@code mode} "43") ou 07 ("47"); null se a resposta não
     * veio (erro, sem prompt), vazio se a ECU não tem códigos ("NO DATA" ou "4300").
     *
     * <p>Com ATS0/ATH0, cada ECU responde numa linha; acima de 7 bytes (CAN) a resposta vem em
     * vários quadros: uma linha com o tamanho em bytes ("00A") e as linhas "0:…", "1:…".
     */
    static List<String> parse(String reply, String mode) {
        String up = reply.toUpperCase(Locale.US);
        List<String> codes = new ArrayList<>();
        if (up.contains("NO DATA")) return codes;
        List<StringBuilder> msgs = new ArrayList<>();
        List<Integer> lens = new ArrayList<>();
        StringBuilder cur = null;
        int nextLen = -1;
        for (String tok : up.split("\\s+")) {
            if (tok.matches("[0-9A-F]{3}")) {
                nextLen = Integer.parseInt(tok, 16);
                cur = null;
            } else if (tok.matches("[0-9A-F]:[0-9A-F]+")) {
                if (tok.charAt(0) == '0' || cur == null) {
                    cur = new StringBuilder();
                    msgs.add(cur);
                    lens.add(nextLen);
                    nextLen = -1;
                }
                cur.append(tok.substring(2));
            } else if (tok.matches("[0-9A-F]+") && tok.startsWith(mode)) {
                msgs.add(new StringBuilder(tok));
                lens.add(-1);
                cur = null;
            }
        }
        if (msgs.isEmpty()) return null;
        for (int m = 0; m < msgs.size(); m++) {
            String hex = msgs.get(m).toString();
            int len = lens.get(m);
            if (len > 0 && hex.length() > len * 2) hex = hex.substring(0, len * 2);
            if (!hex.startsWith(mode)) continue;
            String body = hex.substring(2);
            // CAN: um byte com a quantidade antes dos códigos (2 bytes cada), então tamanho ímpar.
            if ((body.length() / 2) % 2 == 1) {
                int count = Integer.parseInt(body.substring(0, 2), 16);
                body = body.substring(2);
                if (body.length() > count * 4) body = body.substring(0, count * 4);
            }
            for (int i = 0; i + 4 <= body.length(); i += 4) {
                int a = Integer.parseInt(body.substring(i, i + 2), 16);
                int b = Integer.parseInt(body.substring(i + 2, i + 4), 16);
                if (a == 0 && b == 0) continue;
                String code = decode(a, b);
                if (!codes.contains(code)) codes.add(code);
            }
        }
        return codes;
    }

    /** Dois bytes do SAE J2012: sistema (P/C/B/U) e 4 dígitos. */
    static String decode(int a, int b) {
        return String.format(Locale.US, "%c%d%X%02X", "PCBU".charAt(a >> 6), (a >> 4) & 3, a & 0xF, b);
    }

    /** O que o código quer dizer, em texto curto. */
    public String describe(Context c) {
        char sys = code.charAt(0);
        int n = -1;
        try {
            n = Integer.parseInt(code.substring(1));
        } catch (NumberFormatException ignored) {
            // Código com letra (ex.: P0A80): só o sistema.
        }
        if (sys == 'C') return c.getString(R.string.home_dtc_chassis);
        if (sys == 'B') return c.getString(R.string.home_dtc_body);
        if (sys == 'U') return c.getString(R.string.home_dtc_network);
        if (n < 0) return c.getString(R.string.home_dtc_group_engine);
        if (n >= 1000 && n < 2000 || n >= 3000) return c.getString(R.string.home_dtc_maker);
        if (n >= 2100 && n <= 2199) return c.getString(R.string.home_dtc_throttle);
        if (n >= 1000) return c.getString(R.string.home_dtc_group_engine);
        if (n == 300) return c.getString(R.string.home_dtc_misfire_random);
        if (n >= 301 && n <= 312) return c.getString(R.string.home_dtc_misfire_cyl, n - 300);
        if (n == 171 || n == 174) return c.getString(R.string.home_dtc_lean);
        if (n == 172 || n == 175) return c.getString(R.string.home_dtc_rich);
        if (n >= 100 && n <= 109) return c.getString(R.string.home_dtc_air_flow);
        if (n >= 110 && n <= 114) return c.getString(R.string.home_dtc_air_temp);
        if (n >= 115 && n <= 119) return c.getString(R.string.home_dtc_coolant_sensor);
        if (n == 128) return c.getString(R.string.home_dtc_thermostat);
        if (n >= 120 && n <= 124 || n >= 220 && n <= 229) return c.getString(R.string.home_dtc_throttle);
        if (n >= 130 && n <= 167) return c.getString(R.string.home_dtc_o2);
        if (n >= 201 && n <= 212) return c.getString(R.string.home_dtc_injector_cyl, n - 200);
        if (n >= 325 && n <= 334) return c.getString(R.string.home_dtc_knock);
        if (n >= 335 && n <= 339) return c.getString(R.string.home_dtc_crank);
        if (n >= 340 && n <= 349) return c.getString(R.string.home_dtc_cam);
        if (n >= 350 && n <= 362) return c.getString(R.string.home_dtc_coil);
        if (n >= 420 && n <= 434) return c.getString(R.string.home_dtc_catalyst);
        if (n >= 440 && n <= 459) return c.getString(R.string.home_dtc_evap);
        if (n >= 480 && n <= 485) return c.getString(R.string.home_dtc_fan);
        if (n >= 500 && n <= 503) return c.getString(R.string.home_dtc_speed_sensor);
        if (n >= 505 && n <= 507) return c.getString(R.string.home_dtc_idle);
        if (n >= 560 && n <= 563) return c.getString(R.string.home_dtc_voltage);
        if (n >= 600 && n <= 699) return c.getString(R.string.home_dtc_ecu);
        if (n >= 100 && n <= 299) return c.getString(R.string.home_dtc_group_fuel);
        if (n >= 300 && n <= 399) return c.getString(R.string.home_dtc_group_ignition);
        if (n >= 400 && n <= 499) return c.getString(R.string.home_dtc_group_emission);
        if (n >= 700 && n <= 999) return c.getString(R.string.home_dtc_group_trans);
        return c.getString(R.string.home_dtc_group_engine);
    }
}
