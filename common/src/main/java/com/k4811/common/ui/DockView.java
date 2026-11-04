package com.k4811.common.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.Apps;
import com.k4811.common.R;
import com.k4811.common.config.LauncherConfig;
import com.k4811.common.system.ExternalApps;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        /** App de navegação escolhido nas Configurações (Waze, Maps...); o rótulo é o nome dele. */
        NAV("nav", R.string.home_dock_nav, R.string.home_dock_nav_title, IconView.Icon.NAVIGATION, true, null),
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

        /** Short label shown in the dock. */
        public String label(Context c) {
            if (this == ZLINK) return LauncherConfig.ZLINK_NAMES[LauncherConfig.zlinkName(c)].toUpperCase();
            if (this == NAV) {
                String pkg = LauncherConfig.navApp(c);
                if (!pkg.isEmpty()) return ExternalApps.appLabel(c, pkg).toUpperCase();
            }
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
            return this == AUX || this == CAMERA_360 || this == VIDEO || this == ZLINK || this == NAV;
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
                case NAV:
                    return !LauncherConfig.navApp(c).isEmpty();
                default:
                    // App da suíte: some do dock se não estiver instalado (o próprio app sempre aparece).
                    if (pkg == null || pkg.equals(c.getPackageName())) return true;
                    return ExternalApps.installed(c, pkg);
            }
        }
    }

    public interface Listener {
        void onDockItem(Item item);
    }

    /** Total height reserved by the dock (panel + margins), in design dp. */
    public static final int HEIGHT_DP = 160;

    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint underline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final View[] slots = new View[Item.values().length];
    private final IconView[] icons = new IconView[Item.values().length];
    private final TextView[] labels = new TextView[Item.values().length];
    private Item active;
    private Listener listener;
    /** Signature of the shown items (and the Z-Link name and icon), so we only rebuild when they change. */
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

        refresh();
    }

    /** Re-reads the dock selection and rebuilds the items if it changed. */
    public void refresh() {
        Context c = getContext();
        Set<Item> toggles = LauncherConfig.dockToggles(c);
        List<Item> shown = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        for (Item item : Item.values()) {
            if (item.toggleable && !(toggles.contains(item) && item.isAvailable(c))) continue;
            shown.add(item);
            key.append(item.id).append(',');
        }
        key.append(LauncherConfig.zlinkName(c)); // Z-Link's label and icon are a setting too
        if (shown.contains(Item.NAV)) key.append(',').append(LauncherConfig.navApp(c)); // rótulo = app escolhido
        if (key.toString().equals(shownKey)) return;
        shownKey = key.toString();

        removeAllViews();
        for (int i = 0; i < slots.length; i++) {
            slots[i] = null;
            icons[i] = null;
            labels[i] = null;
        }
        for (Item item : shown) {
            LinearLayout slot = new LinearLayout(c);
            slot.setOrientation(VERTICAL);
            slot.setGravity(Gravity.CENTER);
            slot.setClickable(true);
            slot.setContentDescription(item.label(c));
            ThemeDrawables.setBackground(slot, com.k4811.common.R.drawable.dock_item);

            IconView icon = new IconView(c);
            icon.set(item.icon(c), IconView.Style.NONE);
            icon.setClickable(false);
            icon.setDuplicateParentStateEnabled(true);
            icon.color(Ui.TEXT_MUTED);
            int iconSize = Math.round(Ui.dp(c, 62));
            slot.addView(icon, new LayoutParams(iconSize, iconSize));

            TextView label = new TextView(c);
            label.setText(item.label(c));
            // Portrait units are narrower: about 30% smaller labels so they fit side by side.
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.dp(c, Ui.isPortrait(c) ? 15 : 22));
            label.setTextColor(Ui.TEXT_MUTED);
            label.setSingleLine(true);
            label.setTypeface(Ui.medium(c));
            label.setIncludeFontPadding(false);
            slot.addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            slot.setOnClickListener(v -> {
                if (listener != null) listener.onDockItem(item);
            });
            addView(slot, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            slots[item.ordinal()] = slot;
            icons[item.ordinal()] = icon;
            labels[item.ordinal()] = label;
        }
        setActive(active);
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

    /** Highlighted item (the current screen, or on the home screen the playing source). null = none. */
    public void setActive(Item item) {
        active = item;
        for (Item i : Item.values()) {
            if (icons[i.ordinal()] == null) continue;
            boolean on = i == item;
            icons[i.ordinal()].color(on ? Ui.ACCENT : Ui.TEXT_MUTED);
            labels[i.ordinal()].setTextColor(on ? Ui.ACCENT : Ui.TEXT_MUTED);
            labels[i.ordinal()].setTypeface(on ? Ui.semiBold(getContext()) : Ui.medium(getContext()));
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
        super.onDraw(c);
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (active == null || slots[active.ordinal()] == null) return;
        View slot = slots[active.ordinal()];
        float w = Ui.dp(getContext(), 88);
        float cx = slot.getLeft() + slot.getWidth() / 2f;
        float y = getHeight() - Ui.dp(getContext(), 26);
        float h = Ui.dp(getContext(), 4);
        rect.set(cx - w / 2f, y - h, cx + w / 2f, y);
        c.drawRoundRect(rect, h / 2f, h / 2f, underline);
    }
}
