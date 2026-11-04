package com.k4811.audioset.eq;

import com.k4811.common.eq.EqModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Detection and sending to the ARM DSP via android.os.Hardware. */
public final class ArmDsp {

    public static final String PATH_7604 = "/sys/devices/virtual/misc/ak7604-dsp/user_dsp_exist";
    public static final String PATH_7735 = "/sys/devices/virtual/misc/ak7735-dsp/user_dsp_exist";
    public static final String PATH_DTS = "/sys/devices/virtual/misc/ak7604-dsp/user_dts_exist";

    public interface Log {
        void log(String msg);
    }

    public static final class PresetFile {
        public final int[] frequencies;
        public final int[] q;
        public final int[] gainsWire;
        public final String path;

        PresetFile(int[] frequencies, int[] q, int[] gainsWire, String path) {
            this.frequencies = frequencies;
            this.q = q;
            this.gainsWire = gainsWire;
            this.path = path;
        }
    }

    private final Log log;
    private Method method;
    private boolean exemptionTried;

    public ArmDsp(Log log) {
        this.log = log;
    }

    public EqModel.HardwareInfo detect() {
        EqModel.HardwareInfo info = new EqModel.HardwareInfo();
        String config = systemProperty("ro.nwd.config.path", "/config");
        info.configPath = config == null || config.isEmpty() ? "/config" : config;
        info.ak7604 = new File(PATH_7604).exists();
        info.ak7735 = new File(PATH_7735).exists();
        info.dts = new File(PATH_DTS).exists();
        Method m = resolveMethod();
        info.hardwareMethod = m == null ? null : m.getName();
        return info;
    }

    public boolean send(byte[] frame) {
        String hex = DspFrames.toHex(frame);
        Method m = resolveMethod();
        if (m == null) {
            log.log("DSP: method android.os.Hardware.setDSP_*_status unavailable (" + hex + ")");
            return false;
        }
        try {
            Object result = m.invoke(null, hex);
            boolean ok = result instanceof Integer && ((Integer) result) == 0;
            log.log("DSP → " + hex + (ok ? " OK" : " returned " + result));
            return ok;
        } catch (Exception e) {
            Throwable t = e.getCause() != null ? e.getCause() : e;
            log.log("DSP → " + hex + " failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    public int readEqMax(String configPath) {
        Map<String, String> ini = readIni(new File(configPath, "app/UartConfig.ini"));
        String raw = ini.get("dsp3_eq_max");
        if (raw == null) return 30;
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : 30;
        } catch (Exception e) {
            return 30;
        }
    }

    public PresetFile readPresetFile(String configPath, String key) {
        String[] dirs = {"app/eq3", "app/eq", "app/eq_customize"};
        File file = null;
        for (String dir : dirs) {
            File f = new File(new File(configPath, dir), key);
            if (f.canRead()) {
                file = f;
                break;
            }
        }
        if (file == null) return null;
        return readPresetFile(file);
    }

    /** Lê um arquivo de EQ no formato do original (Properties com as 32 frequências, ganhos e Q). */
    public PresetFile readPresetFile(File file) {
        try {
            Properties props = new Properties();
            FileInputStream in = new FileInputStream(file);
            try {
                props.load(in);
            } finally {
                in.close();
            }
            int[] freqs = ints(props, "key_7604_frequence", "key_7602_frequence");
            int[] gains = ints(props, "key_7735_gain");
            int[] qs = ints(props, "key_7735_qvalue");
            if (freqs != null && freqs.length == EqModel.EQ32_BANDS
                    && gains != null && gains.length == EqModel.EQ32_BANDS) {
                int[] q = qs != null && qs.length == EqModel.EQ32_BANDS ? qs : null;
                return new PresetFile(freqs, q, gains, file.getPath());
            }
        } catch (Exception e) {
            log.log("Failed reading " + file.getPath() + ": " + e.getMessage());
        }
        return null;
    }

    private int[] ints(Properties props, String... keys) {
        for (String key : keys) {
            String raw = props.getProperty(key);
            if (raw == null) continue;
            String[] parts = raw.split(",");
            int[] out = new int[parts.length];
            try {
                for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
                return out;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Method resolveMethod() {
        if (method != null) return method;
        exemptHiddenApis();
        Class<?> cls;
        try {
            cls = Class.forName("android.os.Hardware");
        } catch (Exception e) {
            return null;
        }
        String[] names = {"setDSP_ak7604_status", "setDSP_ak7735_status"};
        for (String name : names) {
            try {
                Method m = cls.getDeclaredMethod(name, String.class);
                m.setAccessible(true);
                method = m;
                return m;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void exemptHiddenApis() {
        if (exemptionTried) return;
        exemptionTried = true;
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod(
                "getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntime = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntime, "getRuntime", null);
            Method setExemptions = (Method) getDeclaredMethod.invoke(
                vmRuntime, "setHiddenApiExemptions", new Class[]{String[].class});
            setExemptions.invoke(getRuntime.invoke(null), new Object[]{new String[]{"L"}});
            log.log("Hidden APIs unlocked for this app");
        } catch (Exception e) {
            Throwable t = e.getCause() != null ? e.getCause() : e;
            log.log("Could not unlock hidden APIs: " + t.getClass().getSimpleName());
        }
    }

    public static String systemProperty(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) sp.getMethod("get", String.class, String.class).invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static Map<String, String> readIni(File file) {
        Map<String, String> map = new HashMap<>();
        if (!file.canRead()) return map;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    int semi = line.indexOf(';');
                    if (semi >= 0) line = line.substring(0, semi);
                    line = line.trim();
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}
