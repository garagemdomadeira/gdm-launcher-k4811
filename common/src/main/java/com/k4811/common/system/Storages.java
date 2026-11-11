package com.k4811.common.system;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Armazenamentos legíveis: a memória interna do Android, a da NWD ({@code ro.nwd.internal.path}) e
 * os pen drives e cartões montados (onde a NWD e o Android puro costumam montar).
 */
public final class Storages {
    private static final String PROP_INTERNAL_PATH = "ro.nwd.internal.path";
    private static final String[] MOUNT_PARENTS = {"/storage", "/mnt/media_rw"};
    private static final String[] MOUNTS = {
        "/mnt/usb_storage", "/mnt/usb_storage1", "/mnt/usb_storage2", "/mnt/external_sd",
        "/mnt/extsd", "/mnt/ext_sdcard",
    };

    private Storages() {
    }

    public static List<File> roots() {
        Set<String> seen = new LinkedHashSet<>();
        List<File> out = new ArrayList<>();
        List<File> candidates = new ArrayList<>();
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null) candidates.add(ext);
        String internal = SystemProps.get(PROP_INTERNAL_PATH, "");
        if (!internal.isEmpty()) candidates.add(new File(internal));
        for (String parent : MOUNT_PARENTS) {
            File[] mounts = new File(parent).listFiles();
            if (mounts == null) continue;
            for (File m : mounts) {
                String name = m.getName().toLowerCase(Locale.US);
                if (name.equals("emulated") || name.equals("self")) continue;
                candidates.add(m);
            }
        }
        for (String m : MOUNTS) candidates.add(new File(m));
        for (File f : candidates) {
            if (!f.isDirectory() || f.listFiles() == null) continue;
            if (seen.add(canonical(f))) out.add(f);
        }
        return out;
    }

    /**
     * Pen drives e cartões montados, como o DspAdjust3 original acha o pen drive: volumes
     * removíveis do StorageManager no estado "mounted". Sem eles (Android antigo), as raízes de
     * {@link #roots()} fora da memória interna.
     */
    public static List<File> removable(Context c) {
        List<File> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                StorageManager sm = (StorageManager) c.getSystemService(Context.STORAGE_SERVICE);
                for (Object v : (List<?>) StorageManager.class.getMethod("getStorageVolumes").invoke(sm)) {
                    Class<?> k = v.getClass();
                    boolean removable = (Boolean) k.getMethod("isRemovable").invoke(v);
                    String state = (String) k.getMethod("getState").invoke(v);
                    String path = (String) k.getMethod("getPath").invoke(v);
                    if (removable && "mounted".equalsIgnoreCase(state) && path != null) out.add(new File(path));
                }
                return out;
            } catch (Exception ignored) {
                out.clear();
            }
        }
        String inner = canonical(Environment.getExternalStorageDirectory());
        for (File r : roots()) {
            if (!canonical(r).equals(inner)) out.add(r);
        }
        return out;
    }

    public static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (Exception e) {
            return f.getAbsolutePath();
        }
    }
}
