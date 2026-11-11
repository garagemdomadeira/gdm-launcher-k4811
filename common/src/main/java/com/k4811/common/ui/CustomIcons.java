package com.k4811.common.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.k4811.common.home.HomeImage;

import java.io.File;

/**
 * Ícones personalizados que o motorista larga em {@code K4811/icons/}, nas mesmas raízes do
 * {@link HomeImage} (armazenamento interno da NWD e depois o do Android). Cada arquivo substitui o
 * ícone de um app na gaveta e no dock. Sem arquivo, tudo continua com o ícone padrão.
 *
 * <p>Nomes aceitos (o primeiro que existir vence):
 * <ul>
 *   <li>{@code icons/dock_<id>.<ext>} — só para aquele item do dock (ex.: {@code dock_radio.png},
 *       {@code dock_cam360.png}, {@code dock_zlink.png}); serve também para itens sem app fixo.</li>
 *   <li>{@code icons/<package>.<ext>} — para o app, valendo na gaveta e no dock
 *       (ex.: {@code com.nwd.radio.png}, {@code com.waze.png}).</li>
 * </ul>
 * Extensões: {@code .png}, {@code .webp}, {@code .jpg}, {@code .jpeg} (as mesmas do {@link HomeImage}).
 */
public final class CustomIcons {
    private CustomIcons() {
    }

    /** Subpasta dentro de cada raiz {@code K4811/}. */
    public static final String FOLDER = "icons";
    private static final String[] EXTENSIONS = {".png", ".webp", ".jpg", ".jpeg"};

    /** Ícone do app para a gaveta: {@code icons/<package>.<ext>}, em {@code sizePx}; null se não houver. */
    public static Bitmap forPackage(String pkg, int sizePx) {
        return load(pkg, sizePx);
    }

    /**
     * Como {@link #forPackage(String, int)}, mas primeiro tenta um ícone por atividade
     * {@code icons/<package>@<classe>.<ext>} (ex.: o toolallinone tem Reiniciar, Trava de tela, Volante e
     * Ferramentas como entradas separadas, cada uma com o seu ícone). Cai em {@code icons/<package>} depois.
     */
    public static Bitmap forPackage(String pkg, String className, int sizePx) {
        if (className != null && !className.isEmpty()) {
            Bitmap byClass = load(pkg + "@" + className, sizePx);
            if (byClass != null) return byClass;
        }
        return load(pkg, sizePx);
    }

    /**
     * Ícone de um item do dock: tenta {@code icons/dock_<id>} e, se não houver, {@code icons/<package>}.
     * @param dockId id do item no dock (ex.: {@code radio}); nunca null
     * @param pkg app que o item abre, ou null para itens sem app fixo (Início, Apps, 360, Z-Link)
     * @return o ícone em {@code sizePx}, ou null para cair no vetor desenhado
     */
    public static Bitmap forDock(String dockId, String pkg, int sizePx) {
        Bitmap byId = load("dock_" + dockId, sizePx);
        if (byId != null) return byId;
        return pkg == null ? null : load(pkg, sizePx);
    }

    /**
     * Ícone de launcher do próprio app ({@code @mipmap/ic_launcher}), colorido, como padrão do dock
     * quando não há arquivo em {@code K4811/icons}. Null se {@code pkg} for null ou não estiver instalado.
     */
    public static Bitmap appIcon(Context c, String pkg, int sizePx) {
        if (pkg == null || pkg.isEmpty()) return null;
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            return render(d, sizePx);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Desenha um {@link Drawable} num bitmap quadrado de {@code sizePx}; null em falha. */
    public static Bitmap render(Drawable d, int sizePx) {
        if (d == null) return null;
        int px = Math.max(sizePx, 1);
        try {
            Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, px, px);
            d.draw(cv);
            return b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap load(String base, int sizePx) {
        if (base == null || base.isEmpty()) return null;
        File f = find(base);
        if (f == null) return null;
        int px = Math.max(sizePx, 1);
        return HomeImage.load(f, px, px);
    }

    /** Primeiro {@code icons/<base>.<ext>} que existir, nas raízes do {@code K4811}. */
    private static File find(String base) {
        for (File root : HomeImage.roots()) {
            File dir = new File(root, FOLDER);
            for (String ext : EXTENSIONS) {
                File f = new File(dir, base + ext);
                if (f.isFile()) return f;
            }
        }
        return null;
    }
}
