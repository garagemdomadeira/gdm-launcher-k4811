package com.k4811.common.ui;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.k4811.common.Apps;
import com.k4811.common.R;
import com.k4811.common.config.DockConfig;
import com.k4811.common.config.LauncherConfig;
import com.k4811.common.system.ExternalApps;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom bar (dock) shared by the launcher and every suite app: rounded dark panel with the shortcuts.
 * Active item: white icon and text, bold text and a white underline.
 * Home and Apps are fixed; the middle ones come from launcher.json (Settings › Dock icons, up to 5), re-read
 * whenever the screen becomes visible again.
 */
public class DockView extends LinearLayout {

    public enum Item {
        // The order here is the order in the dock and in the Settings list.
        HOME("home", R.string.home_dock_home, R.string.home_dock_home_title, IconView.Icon.HOME, false, null),
        RADIO("radio", R.string.home_dock_radio, R.string.home_dock_radio_title, IconView.Icon.RADIO, true, Apps.RADIO),
        MUSIC("music", R.string.home_dock_music, R.string.home_dock_music_title, IconView.Icon.MUSIC, true, Apps.MUSIC),
        BT_MUSIC("bt_music", R.string.home_dock_bt_music, R.string.home_dock_bt_music_title,
            IconView.Icon.BLUETOOTH, true, Apps.BT_MUSIC),
        PHONE("phone", R.string.home_dock_phone, R.string.home_dock_phone_title, IconView.Icon.PHONE, true, Apps.PHONE),
        EQ("eq", R.string.home_dock_eq, R.string.home_dock_eq_title, IconView.Icon.EQUALIZER, true, Apps.EQ),
        AUX("aux", R.string.home_dock_aux, R.string.home_dock_aux_title, IconView.Icon.AUX, true, ExternalApps.PKG_AUX),
        CAMERA_360("cam360", R.string.home_dock_cam360, R.string.home_dock_cam360_title,
            IconView.Icon.CAMERA_360, true, null),
        VIDEO("video", R.string.home_dock_video, R.string.home_dock_video_title, IconView.Icon.VIDEO, true, ExternalApps.PKG_VIDEO),
        /** Z-Link (CarPlay / Android Auto); its dock label is chosen in Settings (see LauncherConfig.ZLINK_NAMES). */
        ZLINK("zlink", R.string.home_dock_zlink, R.string.home_dock_zlink_title, IconView.Icon.PHONE_LINK, true, null),
        APPS("apps", R.string.home_dock_apps, R.string.home_dock_apps_title, IconView.Icon.APPS, false, null);

        /** Id gravado em launcher.json (dock_toggles). */
        public final String id;
        /** Short label shown in the dock (string resource). */
        public final int labelRes;
        /** Full name, for the Settings list (string resource). */
        public final int titleRes;
        public final IconView.Icon icon;
        /** false = fixed in the dock (Home and Apps). */
        public final boolean toggleable;
        /** App da suíte (ou da central) que o item abre. null = Início, Apps, 360 e Z-Link (tratados no DockNavigator). */
        public final String pkg;

        Item(String id, int labelRes, int titleRes, IconView.Icon icon, boolean toggleable, String pkg) {
            this.id = id;
            this.labelRes = labelRes;
            this.titleRes = titleRes;
            this.icon = icon;
            this.toggleable = toggleable;
            this.pkg = pkg;
        }

        public static Item byId(String id) {
            for (Item i : values()) if (i.id.equals(id)) return i;
            return null;
        }

        /** Item embutido que abre este package (para manter o comportamento da suíte ao arrastar). */
        public static Item byPackage(String pkg) {
            if (pkg == null) return null;
            for (Item i : values()) if (pkg.equals(i.pkg)) return i;
            return null;
        }

        /** Short label shown in the dock. */
        public String label(Context c) {
            if (this == ZLINK) return LauncherConfig.ZLINK_NAMES[LauncherConfig.zlinkName(c)].toUpperCase();
            return c.getString(labelRes);
        }

        /** Icon in the dock: Z-Link's follows its chosen name (Z-Link, CarPlay or Auto). */
        public IconView.Icon icon(Context c) {
            if (this != ZLINK) return icon;
            switch (LauncherConfig.zlinkName(c)) {
                case 1:
                    return IconView.Icon.CARPLAY;
                case 2:
                    return IconView.Icon.ANDROID_AUTO;
                default:
                    return IconView.Icon.PHONE_LINK;
            }
        }

        /** Full name, for the Settings list. */
        public String title(Context c) {
            return c.getString(titleRes);
        }

        /** Item that opens a head-unit app instead of one of our screens. */
        public boolean isExternal() {
            return this == AUX || this == CAMERA_360 || this == VIDEO || this == ZLINK;
        }

        /** false if the item's app or hardware doesn't exist on this head unit (hidden from dock and Settings). */
        public boolean isAvailable(Context c) {
            switch (this) {
                case AUX:
                    return ExternalApps.hasAux(c);
                case CAMERA_360:
                    return ExternalApps.has360(c);
                case VIDEO:
                    return ExternalApps.hasVideo(c);
                case ZLINK:
                    return ExternalApps.hasZlink(c);
                default:
                    // App da suíte: some do dock se não estiver instalado (o próprio app sempre aparece).
                    if (pkg == null || pkg.equals(c.getPackageName())) return true;
                    return ExternalApps.installed(c, pkg);
            }
        }
    }

    public interface Listener {
        /** {@code item != null} para itens embutidos; senão {@code pkg != null} para um atalho de app. */
        void onDockSelect(Item item, String pkg);
    }

    /**
     * Edição do dock (só o launcher liga, pois é quem grava o {@code dock.json}): soltar um app da
     * gaveta num slot, reordenar um ícone do dock, ou removê-lo ao soltar fora do dock.
     */
    public interface Editor {
        /** App arrastado da gaveta solto na posição {@code atStoredIndex} (insere). */
        void onAdd(int atStoredIndex, String pkg);
        /** Ícone do dock movido de {@code fromStoredIndex} para {@code toStoredIndex}. */
        void onReorder(int fromStoredIndex, int toStoredIndex);
        /** Ícone do dock solto fora do dock: remover a posição {@code fromStoredIndex}. */
        void onRemove(int fromStoredIndex);
    }

    /** Um slot renderizado no dock. */
    private static final class Slot {
        final int storedIndex;   // índice em DockConfig.slots (pode pular itens escondidos)
        final String token;
        final Item item;         // item embutido (comportamento da suíte), ou null para app comum
        View view;
        View iconView;           // IconView (vetor) ou ImageView (ícone colorido)

        Slot(int storedIndex, String token, Item item) {
            this.storedIndex = storedIndex;
            this.token = token;
            this.item = item;
        }
    }

    /** Total height reserved by the dock (panel + margins), in design dp. */
    public static final int HEIGHT_DP = 128;

    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint underline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<Slot> rendered = new ArrayList<>();
    private Item active;
    private Listener listener;
    private Editor editor;
    /** Slot sob o ícone sendo arrastado: fica sombreado para mostrar qual ícone será substituído. */
    private View dropTarget;
    /** Assinatura dos slots mostrados (e o nome do Z-Link), para só reconstruir quando muda. */
    private String shownKey;

    public DockView(Context c) {
        this(c, null);
    }

    public DockView(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        int side = Math.round(Ui.dp(c, 44));
        setPadding(side, Math.round(Ui.dp(c, 14)), side, Math.round(Ui.dp(c, 26)));
        panel.setColor(Ui.BG_SOFT);
        border.setColor(Ui.HAIRLINE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Ui.dp(c, 1.5f));
        underline.setColor(Ui.ACCENT);
        dropBorder.setStyle(Paint.Style.STROKE);
        dropBorder.setStrokeWidth(Ui.dp(c, 2f));

        refresh();
    }

    /** Relê os slots (DockConfig) e reconstrói se mudaram. */
    public void refresh() {
        Context c = getContext();
        List<String> tokens = DockConfig.slots(c);
        PackageManager pm = c.getPackageManager();

        StringBuilder key = new StringBuilder();
        key.append(LauncherConfig.zlinkName(c)).append('|'); // nome/ícone do Z-Link é configurável
        for (String t : tokens) key.append(t).append(',');
        if (key.toString().equals(shownKey)) return;
        shownKey = key.toString();

        removeAllViews();
        rendered.clear();

        int iconSize = Math.round(Ui.dp(c, 72)); // dock só com ícone (sem texto): um pouco maior
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            Item item = Item.byId(token);
            if (item == null) item = Item.byPackage(token); // app da suíte arrastado mantém a lógica
            String pkg;
            String labelText;
            if (item != null) {
                if (!item.isAvailable(c)) continue;
                pkg = item.pkg;
                labelText = item.label(c);
            } else {
                pkg = token; // app comum: só abre
                if (pm.getLaunchIntentForPackage(pkg) == null) continue; // não instalado
                labelText = appLabel(pm, pkg);
            }
            // Item embutido: o dock mantém o ícone VETORIAL da suíte (estilo do Madeira). Só um PNG explícito
            // "dock_<id>.png" troca; NÃO usa o "<pacote>.png" (esse é o ícone personalizado só da gaveta). App
            // comum no dock: usa o PNG personalizado do pacote e, por fim, o ícone do app.
            Bitmap custom = item != null
                ? CustomIcons.forDock(item.id, null, iconSize)
                : CustomIcons.forPackage(pkg, iconSize);
            if (custom == null && item == null) custom = CustomIcons.appIcon(c, pkg, iconSize);

            Slot slot = new Slot(i, token, item);
            buildSlot(c, slot, labelText, custom, iconSize);
            addView(slot.view, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            rendered.add(slot);
        }
        applyEditTargets();
        setActive(active);
    }

    private void buildSlot(Context c, Slot slot, String labelText, Bitmap bmp, int iconSize) {
        LinearLayout view = new LinearLayout(c);
        view.setOrientation(VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setContentDescription(labelText); // só para acessibilidade; o dock não mostra texto
        ThemeDrawables.setBackground(view, com.k4811.common.R.drawable.dock_item);

        View iconView;
        if (bmp != null) {
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(bmp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setClickable(false);
            iv.setDuplicateParentStateEnabled(true);
            iconView = iv;
        } else {
            IconView ic = new IconView(c);
            ic.set(slot.item != null ? slot.item.icon(c) : IconView.Icon.APPS, IconView.Style.NONE);
            ic.setClickable(false);
            ic.setDuplicateParentStateEnabled(true);
            ic.color(Ui.TEXT_MUTED);
            iconView = ic;
        }
        view.addView(iconView, new LayoutParams(iconSize, iconSize));

        final Item item = slot.item;
        final String pkg = slot.item != null ? null : slot.token;
        view.setOnClickListener(v -> {
            if (listener != null) listener.onDockSelect(item, pkg);
        });

        slot.view = view;
        slot.iconView = iconView;
    }

    private static String appLabel(PackageManager pm, String pkg) {
        try {
            CharSequence l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (l != null && l.length() > 0) return l.toString();
        } catch (Exception ignored) {
        }
        return pkg;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // When returning from Settings (or any screen), apply the new dock selection.
        if (visibility == VISIBLE) refresh();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Liga a edição do dock (soltar da gaveta, reordenar, remover). null desliga. Só o launcher usa. */
    public void setEditor(Editor e) {
        editor = e;
        applyEditTargets();
    }

    private void applyEditTargets() {
        for (Slot s : rendered) {
            final Slot fs = s;
            if (editor == null) {
                s.view.setOnDragListener(null);
                s.view.setOnLongClickListener(null);
                s.view.setLongClickable(false);
                continue;
            }
            // Todos os slots são alvo de drop (inclusive Início/Apps, que inserem/reordenam ao lado);
            // só soltar FORA de qualquer slot é que remove.
            s.view.setOnDragListener((v, ev) -> onDrag(fs, v, ev));
            // Segurar um ícone do meio começa a arrastá-lo (reordenar/remover). Início e Apps não.
            if (!DockConfig.isFixed(s.token)) {
                s.view.setOnLongClickListener(v -> startSlotDrag(fs));
            } else {
                s.view.setOnLongClickListener(null);
                s.view.setLongClickable(false);
            }
        }
    }

    /** Começa a arrastar um ícone do dock; o localState (Integer) marca de qual posição ele saiu. */
    private boolean startSlotDrag(Slot slot) {
        View v = slot.view;
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ClipData data = ClipData.newPlainText("dockmove", slot.token);
        try {
            v.startDrag(data, new View.DragShadowBuilder(v), Integer.valueOf(slot.storedIndex), 0);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private boolean onDrag(Slot slot, View v, DragEvent e) {
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                ClipDescription cd = e.getClipDescription();
                return cd != null && cd.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
            case DragEvent.ACTION_DRAG_ENTERED:
                setDropTarget(v);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                if (dropTarget == v) setDropTarget(null);
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                setDropTarget(null);
                // Arraste vindo do dock que não caiu em nenhum slot (soltou fora): remove. Só o slot
                // de origem dispara, para rodar uma vez.
                if (editor != null && e.getLocalState() instanceof Integer
                        && !e.getResult() && (Integer) e.getLocalState() == slot.storedIndex) {
                    editor.onRemove(slot.storedIndex);
                }
                return true;
            case DragEvent.ACTION_DROP:
                setDropTarget(null);
                if (editor == null) return true;
                Object ls = e.getLocalState();
                if (ls instanceof Integer) { // reordenar (veio de um ícone do dock)
                    int from = (Integer) ls;
                    if (from != slot.storedIndex) editor.onReorder(from, slot.storedIndex);
                } else { // adicionar (veio da gaveta)
                    CharSequence p = e.getClipData() != null && e.getClipData().getItemCount() > 0
                        ? e.getClipData().getItemAt(0).getText() : null;
                    if (p != null && p.length() > 0) editor.onAdd(slot.storedIndex, p.toString());
                }
                return true;
            default:
                return true;
        }
    }

    /** Marca o slot sob o arraste: escurece o ícone atual e pinta o sombreado (ver onDraw). */
    private void setDropTarget(View v) {
        if (dropTarget == v) return;
        if (dropTarget != null) dropTarget.setAlpha(1f);
        dropTarget = v;
        if (dropTarget != null) dropTarget.setAlpha(0.35f);
        invalidate();
    }

    /** Highlighted item (the current screen, or on the home screen the playing source). null = none. */
    public void setActive(Item item) {
        active = item;
        for (Slot s : rendered) {
            boolean on = s.item != null && s.item == item;
            if (s.iconView instanceof IconView) {
                ((IconView) s.iconView).color(on ? Ui.ACCENT : Ui.TEXT_MUTED);
            } else {
                // Ícone colorido: destaca por opacidade (o sublinhado marca o ativo).
                s.iconView.setAlpha(on ? 1f : 0.6f);
            }
        }
        invalidate();
    }

    public Item getActive() {
        return active;
    }

    @Override
    protected void onDraw(Canvas c) {
        // Rounded panel with a thin border, inside the side/bottom margins.
        float inset = Ui.dp(getContext(), 26);
        float top = Ui.dp(getContext(), 4);
        float bottom = getHeight() - Ui.dp(getContext(), 14);
        float r = Ui.dp(getContext(), 26);
        rect.set(inset, top, getWidth() - inset, bottom);
        c.drawRoundRect(rect, r, r, panel);
        c.drawRoundRect(rect, r, r, border);

        // Sombreado no slot de destino do arraste: mostra onde o ícone vai cair (e quem substitui).
        if (dropTarget != null) {
            float pad = Ui.dp(getContext(), 8);
            float vpad = Ui.dp(getContext(), 10);
            float rr = Ui.dp(getContext(), 18);
            dropFill.setColor(Ui.ACCENT);
            dropFill.setAlpha(70);
            dropBorder.setColor(Ui.ACCENT);
            rect.set(dropTarget.getLeft() + pad, top + vpad, dropTarget.getRight() - pad, bottom - vpad);
            c.drawRoundRect(rect, rr, rr, dropFill);
            c.drawRoundRect(rect, rr, rr, dropBorder);
        }
        super.onDraw(c);
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        View slot = null;
        for (Slot s : rendered) {
            if (s.item != null && s.item == active) {
                slot = s.view;
                break;
            }
        }
        if (slot == null) return;
        float w = Ui.dp(getContext(), 88);
        float cx = slot.getLeft() + slot.getWidth() / 2f;
        float y = getHeight() - Ui.dp(getContext(), 26);
        float h = Ui.dp(getContext(), 4);
        rect.set(cx - w / 2f, y - h, cx + w / 2f, y);
        c.drawRoundRect(rect, h / 2f, h / 2f, underline);
    }
}
