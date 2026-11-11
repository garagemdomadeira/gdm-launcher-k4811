package com.k4811.updater;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/** Baixar do GitHub (as releases redirecionam para outro endereço https; o HttpURLConnection segue). */
final class Net {
    interface Progress {
        /** {@code done} de {@code total} bytes (total 0 = desconhecido). Devolve false para cancelar. */
        boolean on(long done, long total);
    }

    private Net() {
    }

    static byte[] bytes(String url) throws IOException {
        HttpURLConnection c = open(url, 0);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /**
     * Baixa para {@code dest} continuando de onde parou ({@code dest} parcial fica). Confere o tamanho no fim; o
     * SHA-256 quem confere é quem chama.
     */
    static void download(String url, File dest, long expected, Progress p) throws IOException {
        long have = dest.isFile() ? dest.length() : 0;
        if (expected > 0 && have > expected) {
            dest.delete();
            have = 0;
        }
        if (expected > 0 && have == expected) {
            if (p != null) p.on(have, expected);
            return;
        }
        HttpURLConnection c = open(url, have);
        try {
            int code = c.getResponseCode();
            boolean resumed = code == HttpURLConnection.HTTP_PARTIAL;
            if (!resumed) have = 0; // servidor mandou tudo de novo
            long total = expected > 0 ? expected : (resumed ? have : 0) + Math.max(0, c.getContentLength());
            try (InputStream in = c.getInputStream();
                 RandomAccessFile out = new RandomAccessFile(dest, "rw")) {
                out.setLength(have);
                out.seek(have);
                byte[] buf = new byte[1 << 16];
                int n;
                long done = have;
                long lastReport = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (p != null && done - lastReport > (1 << 18)) {
                        lastReport = done;
                        if (!p.on(done, total)) throw new IOException("cancelado");
                    }
                }
                if (p != null) p.on(done, total);
                if (expected > 0 && done != expected) throw new IOException("download incompleto: " + done + "/" + expected);
            }
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, long from) throws IOException {
        String u = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "GDM-Atualizacoes");
            if (from > 0) c.setRequestProperty("Range", "bytes=" + from + "-");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirecionamento sem destino");
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code >= 400) {
                c.disconnect();
                throw new IOException("HTTP " + code + " em " + u);
            }
            return c;
        }
        throw new IOException("redirecionamentos demais");
    }

    /** Copia um arquivo (para o /cache). */
    static void copy(File from, File to) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
        }
    }
}
