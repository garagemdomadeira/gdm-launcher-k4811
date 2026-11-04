package com.k4811.launcher.obd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.k4811.common.system.SystemProps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Leitor OBD2 por um ELM327 Bluetooth pareado no Android (na K4811 o Bluetooth da NWD usa a pilha
 * do próprio Android: o ELM327 pareado em Ferramentas → OBD aparece aqui, senha em
 * {@code key_pair_password}).
 *
 * <p>Uma thread abre a conexão serial (SPP), inicializa o ELM327 e lê em ciclo: tensão da bateria
 * ({@code ATRV}), temperatura do motor (0105), rotação e velocidade (010C e 010D, num pedido só) e combustível
 * (012F). Cada leitura vai para o {@link Listener} na thread principal. Se cair, tenta de novo.
 *
 * <p>Uma thread por vez: o ELM327 só aceita uma conexão, e duas threads (parar e voltar rápido
 * com a tela inicial) derrubavam uma à outra em laço. Cada thread confere se ainda é a atual. Se o
 * ELM327 parar de responder (sem o prompt ">" várias vezes seguidas), reconecta.
 */
public final class ObdClient {

    public interface Listener {
        void onObd(ObdData data);
    }

    private static final String TAG = "GdmObd";
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long RETRY_MS = 10_000;
    private static final long CYCLE_PAUSE_MS = 300;
    private static final long SLOW_EVERY_MS = 10_000;
    /** Códigos de defeito (modos 03 e 07): ao conectar e depois a cada 30 s. */
    private static final long DTC_EVERY_MS = 30_000;
    private static final int TIMEOUT_MS = 2500;

    /**
     * Demonstração sem ELM327 (vídeos, testes no emulador): {@code adb shell setprop
     * debug.gdm.obd_demo 1}. Simula uma volta de 50 s: parado, acelera trocando as marchas até
     * 90 km/h, mantém e freia.
     */
    private static final String PROP_DEMO = "debug.gdm.obd_demo";
    /**
     * Teste do balão de defeito sem defeito no carro: {@code adb shell setprop debug.gdm.obd_dtc
     * P0301,P0420} (com ELM327 ou na demonstração); vazio desliga.
     */
    private static final String PROP_TEST_DTC = "debug.gdm.obd_dtc";
    private static final float[] DEMO_RATIO = GearEstimator.DEFAULT;

    private static ObdClient instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ObdData data = new ObdData();
    private final GearEstimator gears = new GearEstimator();
    private final ShiftHint shifts = new ShiftHint();
    private Listener listener;
    /** A thread atual; as outras (de um start/stop anterior) saem sozinhas. */
    private volatile Thread thread;
    private volatile boolean running;
    /** Respostas sem o prompt ">" seguidas que derrubam a conexão (ELM327 travado). */
    private static final int MAX_SILENT = 3;
    /** Espera para o Bluetooth soltar o canal da conexão anterior. */
    private static final long RELEASE_MS = 800;
    private volatile BluetoothSocket socket;

    public static synchronized ObdClient get() {
        if (instance == null) instance = new ObdClient();
        return instance;
    }

    /** Endereço do ELM327 pareado (nome com OBD, ELM, VHM ou CAR), ou null. */
    public static BluetoothDevice pairedDevice() {
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null || !bt.isEnabled()) return null;
            Set<BluetoothDevice> bonded = bt.getBondedDevices();
            if (bonded == null) return null;
            for (BluetoothDevice d : bonded) {
                String n = d.getName() == null ? "" : d.getName().toUpperCase(Locale.US);
                if (n.contains("OBD") || n.contains("ELM") || n.contains("VHM") || n.contains("CAR")) return d;
            }
        } catch (Exception e) {
            Log.w(TAG, "sem acesso ao Bluetooth", e);
        }
        return null;
    }

    public static boolean demo() {
        return "1".equals(SystemProps.get(PROP_DEMO, ""));
    }

    /** Há de onde ler: um ELM327 pareado, ou a demonstração ligada. */
    public static boolean available() {
        return demo() || pairedDevice() != null;
    }

    public void setListener(Listener l) {
        listener = l;
        if (l != null) post();
    }

    /** {@code c}: para carregar e gravar o aprendizado das marchas. */
    public synchronized void start(android.content.Context c) {
        gears.attach(c);
        if (running) return;
        Log.i(TAG, demo() ? "início (demonstração)" : "início");
        running = true;
        Thread t = new Thread(this::loop, "GdmObd");
        thread = t;
        t.start();
    }

    /** Esquece o aprendizado das marchas e volta ao ponto de partida. */
    public void resetGears() {
        gears.reset();
    }

    /** Para e solta o ELM327 (outro app pode usá-lo). */
    public synchronized void stop() {
        if (running) Log.i(TAG, "parada");
        running = false;
        Thread t = thread;
        thread = null;
        closeSocket();
        if (t != null) t.interrupt();
    }

    // ---- Thread ----

    /** Esta thread ainda é a que deve rodar. */
    private boolean alive() {
        return running && Thread.currentThread() == thread;
    }

    private void loop() {
        // A conexão da thread anterior pode estar fechando ainda.
        sleep(RELEASE_MS);
        if (demo()) {
            demoLoop();
            return;
        }
        while (alive()) {
            BluetoothDevice dev = pairedDevice();
            if (dev == null) {
                setState(ObdData.State.NO_DEVICE);
                sleep(RETRY_MS);
                continue;
            }
            setState(ObdData.State.CONNECTING);
            BluetoothSocket s = null;
            try {
                s = open(dev);
                socket = s;
                silent = 0;
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                for (String cmd : new String[] {"ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0"}) {
                    command(in, out, cmd, cmd.equals("ATZ") ? 4000 : TIMEOUT_MS);
                }
                Log.i(TAG, "conectado a " + dev.getAddress());
                logSupported(in, out);
                combined = true;
                setState(ObdData.State.CONNECTED);
                long lastSlow = 0;
                long lastDtc = 0;
                while (alive()) {
                    long now = System.currentTimeMillis();
                    readRpmSpeed(in, out);
                    if (now - lastSlow >= SLOW_EVERY_MS) {
                        lastSlow = now;
                        readSlow(in, out);
                    }
                    if (now - lastDtc >= DTC_EVERY_MS) {
                        lastDtc = now;
                        readDtc(in, out);
                    }
                    post();
                    sleep(CYCLE_PAUSE_MS);
                }
            } catch (Exception e) {
                Log.w(TAG, "OBD caiu: " + e.getMessage());
                setState(ObdData.State.ERROR);
            } finally {
                // Só a conexão desta thread (a de uma thread nova continua aberta).
                closeQuietly(s);
                if (socket == s) socket = null;
            }
            if (alive()) sleep(RETRY_MS);
        }
    }

    private void demoLoop() {
        long start = System.currentTimeMillis();
        data.state = ObdData.State.CONNECTED;
        while (alive()) {
            float t = ((System.currentTimeMillis() - start) % 50_000) / 1000f;
            float speed = t < 5 ? 0 : t < 30 ? (t - 5) / 25f * 90f : t < 40 ? 90 : Math.max(0, 90 - (t - 40) * 9);
            int rpm = 850;
            if (speed >= 5) {
                // Marcha mais alta que deixa o giro abaixo de 2800.
                int g = 0;
                while (g < DEMO_RATIO.length - 1 && speed * DEMO_RATIO[g] > 2800) g++;
                rpm = Math.max(850, Math.round(speed * DEMO_RATIO[g]));
            }
            data.speed = Math.round(speed);
            data.rpm = rpm;
            data.gear = gears.update(data.rpm, data.speed);
        data.shift = shifts.update(data.gear, data.rpm, data.speed);
            data.volts = 14.1f + (float) Math.sin(t) * 0.1f;
            data.coolant = Math.min(90, 72 + Math.round(t));
            data.fuel = 62;
            data.dtcs = withTest(new ArrayList<Dtc>());
            post();
            sleep(CYCLE_PAUSE_MS);
        }
    }

    private BluetoothSocket open(BluetoothDevice dev) throws IOException {
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
        try {
            BluetoothSocket s = dev.createRfcommSocketToServiceRecord(SPP);
            s.connect();
            return s;
        } catch (IOException first) {
            // Clones do ELM327 às vezes só aceitam o canal 1 direto.
            try {
                Method m = dev.getClass().getMethod("createRfcommSocket", int.class);
                BluetoothSocket s = (BluetoothSocket) m.invoke(dev, 1);
                s.connect();
                return s;
            } catch (Exception second) {
                throw first;
            }
        }
    }

    /** Rotação e velocidade num pedido só ("010C0D"); falso se o ELM327/ECU não aceitar (clones). */
    private boolean combined;

    /**
     * Rotação e velocidade do mesmo instante: num pedido só as duas vêm da mesma resposta da ECU;
     * em dois pedidos seguidos há ~100 ms entre elas, e acelerando em 1ª isso já desvia a relação
     * rpm/km/h usada na marcha. Se o pedido duplo não trouxer as duas (a EcoSport responde NO DATA),
     * lê rotação, velocidade e rotação de novo e usa a média das duas rotações.
     */
    private void readRpmSpeed(InputStream in, OutputStream out) throws IOException {
        int[] rpm = null;
        int[] spd = null;
        if (combined) {
            String reply = command(in, out, "010C0D", TIMEOUT_MS);
            rpm = bytes(reply, "410C", 2);
            // A velocidade vem logo depois dos 2 bytes da rotação, na mesma resposta.
            if (rpm != null) spd = bytes(reply, String.format(Locale.US, "410C%02X%02X0D", rpm[0], rpm[1]), 1);
            if (rpm == null || spd == null) {
                combined = false;
                Log.i(TAG, "pedido duplo não aceito (" + reply + "), lendo separado");
                rpm = spd = null;
            }
        }
        if (!combined) {
            // Rotação antes e depois da velocidade: a média fica no instante da velocidade.
            int[] before = bytes(command(in, out, "010C", TIMEOUT_MS), "410C", 2);
            spd = bytes(command(in, out, "010D", TIMEOUT_MS), "410D", 1);
            int[] after = bytes(command(in, out, "010C", TIMEOUT_MS), "410C", 2);
            if (before != null && after != null) {
                data.rpm = (rpm(before) + rpm(after)) / 2;
            } else if (before != null || after != null) {
                data.rpm = rpm(before != null ? before : after);
            }
        } else {
            data.rpm = rpm(rpm);
        }
        if (spd != null) data.speed = spd[0];
        data.gear = gears.update(data.rpm, data.speed);
        data.shift = shifts.update(data.gear, data.rpm, data.speed);
    }

    private void readSlow(InputStream in, OutputStream out) throws IOException {
        String v = command(in, out, "ATRV", TIMEOUT_MS).replaceAll("[^0-9.]", "");
        try {
            if (!v.isEmpty()) data.volts = Float.parseFloat(v);
        } catch (NumberFormatException ignored) {
        }
        int[] t = bytes(command(in, out, "0105", TIMEOUT_MS), "4105", 1);
        if (t != null) data.coolant = t[0] - 40;
        int[] f = bytes(command(in, out, "012F", TIMEOUT_MS), "412F", 1);
        data.fuel = f != null ? Math.round(f[0] * 100f / 255f) : -1;
    }

    /**
     * Luz de injeção (0101), confirmados (03) e pendentes (07). Uma resposta que não veio mantém a
     * leitura anterior, para não dar o código como apagado (e avisar de novo) por um erro de leitura.
     */
    private void readDtc(InputStream in, OutputStream out) throws IOException {
        int[] status = bytes(command(in, out, "0101", TIMEOUT_MS), "4101", 4);
        boolean milBefore = data.mil;
        if (status != null) data.mil = (status[0] & 0x80) != 0;
        List<String> stored = Dtc.parse(command(in, out, "03", TIMEOUT_MS), "43");
        List<String> pending = Dtc.parse(command(in, out, "07", TIMEOUT_MS), "47");
        if (stored == null || pending == null) return;
        List<Dtc> list = new ArrayList<>();
        for (String c : stored) list.add(new Dtc(c, false));
        for (String c : pending) {
            if (!stored.contains(c)) list.add(new Dtc(c, true));
        }
        Dtc[] now = withTest(list);
        String before = describe(data.dtcs);
        data.dtcs = now;
        String after = describe(now);
        if (!after.equals(before) || data.mil != milBefore) Log.i(TAG, "códigos de defeito: " + after + (data.mil ? " (luz acesa)" : ""));
    }

    /** Acrescenta os códigos de {@link #PROP_TEST_DTC}. */
    private static Dtc[] withTest(List<Dtc> list) {
        for (String c : SystemProps.get(PROP_TEST_DTC, "").split(",")) {
            c = c.trim().toUpperCase(Locale.US);
            if (c.matches("[PCBU][0-9A-F]{4}")) list.add(new Dtc(c, false));
        }
        return list.toArray(new Dtc[0]);
    }

    private static String describe(Dtc[] dtcs) {
        if (dtcs == null) return "?";
        if (dtcs.length == 0) return "nenhum";
        StringBuilder sb = new StringBuilder();
        for (Dtc d : dtcs) sb.append(sb.length() > 0 ? " " : "").append(d.key());
        return sb.toString();
    }

    /** Respostas seguidas sem o prompt (só a thread atual mexe). */
    private int silent;

    /**
     * Manda o comando e lê até o prompt ">" do ELM327. Sem o prompt {@link #MAX_SILENT} vezes
     * seguidas, o ELM327 travou: derruba a conexão para reconectar. Parado, sai na hora.
     */
    private String command(InputStream in, OutputStream out, String cmd, int timeoutMs) throws IOException {
        if (!alive()) throw new IOException("parado");
        out.write((cmd + "\r").getBytes("US-ASCII"));
        out.flush();
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + timeoutMs;
        boolean prompt = false;
        while (System.currentTimeMillis() < end) {
            if (in.available() > 0) {
                int c = in.read();
                if (c < 0) throw new IOException("fim da conexão");
                if (c == '>') {
                    prompt = true;
                    break;
                }
                sb.append((char) c);
            } else {
                if (!alive()) throw new IOException("parado");
                sleep(15);
            }
        }
        silent = prompt ? 0 : silent + 1;
        if (silent >= MAX_SILENT) throw new IOException("ELM327 sem resposta (" + cmd + ")");
        return sb.toString().replace("\r", " ").replace("\n", " ").trim();
    }

    private static int rpm(int[] b) {
        return (b[0] * 256 + b[1]) / 4;
    }

    /**
     * Uma vez por conexão, só no log: protocolo e os PIDs que a ECU diz ter (bitmaps 0100, 0120,
     * 0140), para conferir no carro que rotação, velocidade, temperatura e combustível existem.
     */
    private void logSupported(InputStream in, OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder("protocolo ").append(command(in, out, "ATDPN", TIMEOUT_MS));
        sb.append(", PIDs:");
        for (int base = 0; base <= 0x40; base += 0x20) {
            String pid = String.format(Locale.US, "%02X", base);
            int[] b = bytes(command(in, out, "01" + pid, TIMEOUT_MS), "41" + pid, 4);
            if (b == null) break;
            for (int i = 0; i < 32; i++) {
                if ((b[i / 8] & (0x80 >> (i % 8))) != 0) sb.append(String.format(Locale.US, " %02X", base + i + 1));
            }
            if ((b[3] & 1) == 0) break;
        }
        Log.i(TAG, sb.toString());
    }

    /** Bytes de dados depois do cabeçalho (ex.: "410C"); null se não veio ("NO DATA", erro). */
    private static int[] bytes(String reply, String header, int count) {
        String hex = reply.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        int at = hex.indexOf(header);
        if (at < 0 || hex.length() < at + header.length() + count * 2) return null;
        int[] b = new int[count];
        for (int i = 0; i < count; i++) {
            int p = at + header.length() + i * 2;
            b[i] = Integer.parseInt(hex.substring(p, p + 2), 16);
        }
        return b;
    }

    private void setState(ObdData.State s) {
        data.state = s;
        post();
    }

    private void post() {
        final ObdData copy = data.copy();
        main.post(() -> {
            if (listener != null) listener.onObd(copy);
        });
    }

    private void closeSocket() {
        BluetoothSocket s = socket;
        socket = null;
        closeQuietly(s);
    }

    private static void closeQuietly(BluetoothSocket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
