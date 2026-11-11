package com.k4811.launcher.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.k4811.launcher.R;
import com.k4811.common.ui.CustomIcons;
import com.k4811.common.ui.Ui;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Apps with a launcher icon, excluding keyboards and whatever the head unit hardware lacks. */
public final class AppCatalog {

    public static final class Entry {
        public final String label;
        public final String packageName;
        public final String className;
        public final Bitmap icon;
        /** Categoria da gaveta ({@link #GROUP_CAR}...). */
        public final int group;

        Entry(String label, String packageName, String className, Bitmap icon, int group) {
            this.label = label;
            this.packageName = packageName;
            this.className = className;
            this.icon = icon;
            this.group = group;
        }

        /** Identifica o app nos favoritos: package/classe. */
        public String key() {
            return packageName + "/" + className;
        }
    }

    // Categorias da gaveta, na ordem dos filtros. 0 = todas.
    public static final int GROUP_ALL = 0;
    /** Apps da central (NWD, suíte, Z-Link, câmera...). */
    public static final int GROUP_CAR = 1;
    public static final int GROUP_NAV = 2;
    public static final int GROUP_MEDIA = 3;
    public static final int GROUP_SOCIAL = 4;
    public static final int GROUP_TOOLS = 5;
    public static final int GROUP_GAMES = 6;
    public static final int GROUP_OTHER = 7;
    public static final int GROUPS = 8;

    /**
     * Categoria do app: a da Play Store ({@code ApplicationInfo.category}, Android 8+), com os apps
     * da central à parte e alguns apps conhecidos sem categoria corrigidos.
     */
    static int groupOf(android.content.pm.ApplicationInfo ai, String pkg) {
        if (pkg.startsWith("com.nwd.") || pkg.startsWith("com.bt") || pkg.equals("com.android.car.setting")
            || pkg.equals("com.zjinnova.zlink") || pkg.equals("com.ivicar.avm") || pkg.startsWith("com.garagemdomadeira.")
            || pkg.startsWith("com.txznet.")) return GROUP_CAR;
        if (pkg.equals("com.waze") || pkg.startsWith("com.google.android.apps.maps")) return GROUP_NAV;
        if (pkg.equals("com.spotify.music") || pkg.equals("deezer.android.app") || pkg.startsWith("com.google.android.youtube")
            || pkg.startsWith("com.google.android.apps.youtube") || pkg.equals("com.amazon.mp3")) return GROUP_MEDIA;
        if (pkg.equals("com.whatsapp") || pkg.equals("org.telegram.messenger")) return GROUP_SOCIAL;
        if (pkg.equals("com.android.settings") || pkg.equals("com.android.vending") || pkg.equals("com.android.chrome")
            || pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.")) return GROUP_TOOLS;
        int cat = ai == null || android.os.Build.VERSION.SDK_INT < 26 ? -1 : ai.category;
        switch (cat) {
            case 6: // CATEGORY_MAPS
                return GROUP_NAV;
            case 1: // CATEGORY_AUDIO
            case 2: // CATEGORY_VIDEO
            case 3: // CATEGORY_IMAGE
                return GROUP_MEDIA;
            case 4: // CATEGORY_SOCIAL
            case 5: // CATEGORY_NEWS
                return GROUP_SOCIAL;
            case 7: // CATEGORY_PRODUCTIVITY
                return GROUP_TOOLS;
            case 0: // CATEGORY_GAME
                return GROUP_GAMES;
            default:
                return GROUP_OTHER;
        }
    }

    private static final String FEATURE = "factory_feature";

    private AppCatalog() {
    }

    public static List<Entry> load(Context context, int iconPx) {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(query, 0);
        } catch (Exception e) {
            found = null;
        }
        if (found == null) return new ArrayList<>();

        Set<String> keyboards = keyboards(context);
        String ours = context.getPackageName();
        int features = features(context);
        int size = Math.max(iconPx, 1);
        List<Entry> apps = new ArrayList<>();
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (pkg == null || cls == null) continue;
            if (pkg.equals(ours) || keyboards.contains(pkg)) continue;
            if (!hardwareAllows(pkg, cls, features)) continue;
            CharSequence title = info.loadLabel(pm);
            String label = title == null ? pkg : title.toString().trim();
            if (label.isEmpty()) label = pkg;
            // Ícone personalizado (K4811/icons/<package>.png) na frente; senão o do app no formato escolhido.
            Bitmap custom = CustomIcons.forPackage(pkg, size);
            Bitmap bmp = custom != null ? custom : icon(context, info, pm, size);
            apps.add(new Entry(label, pkg, cls, bmp, groupOf(info.activityInfo.applicationInfo, pkg)));
        }
        Collator order = Collator.getInstance(Locale.getDefault());
        order.setStrength(Collator.PRIMARY);
        Collections.sort(apps, (a, b) -> order.compare(a.label, b.label));
        return apps;
    }

    public static void recycle(List<Entry> apps) {
        if (apps == null) return;
        for (Entry e : apps) {
            if (e.icon != null && !e.icon.isRecycled()) e.icon.recycle();
        }
    }

    /** -1 when the head unit didn't report its hardware: in that case we don't hide by bit. */
    private static int features(Context context) {
        try {
            return Settings.System.getInt(context.getContentResolver(), FEATURE);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
    }

    /**
     * Factory feature bits: DVD 4, 360 camera 16, AUX 4096, TV 8192,
     * rear AUX 16384, DVR 32768.
     */
    private static boolean hardwareAllows(String pkg, String cls, int features) {
        if (features < 0) return true;
        int bit = -1;
        if ("com.nwd.auxin.rear".equals(pkg)) bit = 16384;
        else if ("com.nwd.auxin".equals(pkg) || "com.nwd.camera.app.AuxActivity".equals(cls)) bit = 4096;
        else if ("com.nwd.tv".equals(pkg) || "com.nwd.camera.app.TvActivity".equals(cls)) bit = 8192;
        else if ("com.nwd.carkit360camera".equals(pkg) || "com.ivicar.avm".equals(pkg)) bit = 16;
        else if ("com.nwd.dvd".equals(pkg)) bit = 4;
        else if ("com.nwd.dvr.nomal".equals(pkg)) bit = 32768;
        if (bit < 0) return true;
        return (features & bit) == bit;
    }

    private static Set<String> keyboards(Context context) {
        Set<String> names = new HashSet<>();
        try {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return names;
            List<InputMethodInfo> list = imm.getInputMethodList();
            if (list == null) return names;
            for (InputMethodInfo info : list) {
                if (info.getPackageName() != null) names.add(info.getPackageName());
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    /** Ícone do app no formato escolhido nas Configurações (IconStyle). O personalizado já vem antes. */
    private static Bitmap icon(Context c, ResolveInfo info, PackageManager pm, int size) {
        Drawable d = info.loadIcon(pm);
        Bitmap b = null;
        try {
            b = IconStyle.render(c, d, size, false);
        } catch (Exception ignored) {
        }
        return b != null ? b : icon(d, size);
    }

    private static Bitmap icon(Drawable drawable, int size) {
        if (drawable == null) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}
