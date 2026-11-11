package com.k4811.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.k4811.launcher.home.AppCatalog;
import com.k4811.launcher.home.DockEditor;
import com.k4811.common.ui.DockNavigator;
import com.k4811.common.ui.DockView;
import com.k4811.common.ui.IconView;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Every installed app in a 5-column grid that scrolls down until the last one (no pages). The
 * icons come in with a short cascade the first time, sink a little under the finger and the app
 * opens zooming out of its icon. Back returns to the home screen.
 *
 * <p>Abas Todos / Favoritos e filtro por categoria ({@link AppCatalog#groupOf}). Tocar e segurar um
 * app abre o menu (adicionar ou tirar dos favoritos, informações do app). Os favoritos e a última aba
 * ficam no SharedPreferences do launcher (só ele usa).
 */
public class AppsActivity extends Activity {

    /** Rows visible at once: two and a half, so the cut row shows there is more below. */
    private static final float VISIBLE_ROWS = 2.5f;
    private static final float PRESSED_SCALE = 0.93f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    /** Todos os apps carregados (donos dos ícones). */
    private final List<AppCatalog.Entry> all = new ArrayList<>();
    /** O que a grade mostra: a aba e o filtro aplicados sobre {@link #all}. */
    private final List<AppCatalog.Entry> apps = new ArrayList<>();
    private static final String PREFS = "apps_drawer";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_TAB = "tab";
    private static final int TAB_ALL = 0;
    private static final int TAB_FAV = 1;
    private static final int[] GROUP_NAMES = {R.string.home_apps_cat_all, R.string.home_apps_cat_car,
        R.string.home_apps_cat_nav, R.string.home_apps_cat_media, R.string.home_apps_cat_social,
        R.string.home_apps_cat_tools, R.string.home_apps_cat_games, R.string.home_apps_cat_other};
    private final java.util.Set<String> favorites = new java.util.LinkedHashSet<>();
    private int tab;
    private int group = AppCatalog.GROUP_ALL;
    private TextView tabAll;
    private TextView tabFav;
    private View filters;
    private android.widget.LinearLayout filterRow;
    private android.app.Dialog menu;

    private GridView grid;
    private TextView countView;
    private TextView empty;
    private DockView dock;
    private AppsAdapter adapter;
    private int rowHeight;
    private int loadGen;
    /** The cascade only plays when the drawer opens, not when coming back from an app. */
    private boolean animatedIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        Ui.applyFont(findViewById(R.id.apps_root));

        ((TextView) findViewById(R.id.apps_title)).setTypeface(Ui.semiBold(this));
        countView = (TextView) findViewById(R.id.apps_count);
        empty = (TextView) findViewById(R.id.apps_empty);

        IconView back = (IconView) findViewById(R.id.apps_back);
        back.set(IconView.Icon.CHEVRON_LEFT, IconView.Style.NONE);
        back.setOnClickListener(v -> finish());

        grid = (GridView) findViewById(R.id.apps_grid);
        // A longer, softer fling than the default: the list glides to a stop.
        grid.setFriction(ViewConfiguration.getScrollFriction() * 0.7f);
        grid.setVelocityScale(1.15f);
        grid.setLayoutAnimation(cascade());
        adapter = new AppsAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> open(view, position));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) return false;
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showMenu(apps.get(position));
            return true;
        });

        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet(KEY_FAVORITES, new java.util.HashSet<>()));
        tab = prefs.getInt(KEY_TAB, TAB_ALL);
        tabAll = (TextView) findViewById(R.id.apps_tab_all);
        tabFav = (TextView) findViewById(R.id.apps_tab_fav);
        tabAll.setTypeface(Ui.medium(this));
        tabFav.setTypeface(Ui.medium(this));
        tabAll.setOnClickListener(v -> setTab(TAB_ALL));
        tabFav.setOnClickListener(v -> setTab(TAB_FAV));
        filters = findViewById(R.id.apps_filters);
        filterRow = (android.widget.LinearLayout) findViewById(R.id.apps_filter_row);
        // Row height follows the area the grid really got.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (h <= 0 || h == ob - ot) return;
            int usable = h - grid.getPaddingTop() - Math.round(Ui.dp(this, 8)) * 2;
            // Na vertical a tela é alta: a linha segue a altura do conteúdo (ícone + nome), compacta, em vez de
            // esticar para caber só 2,5 linhas (o que deixava vãos enormes entre os apps). Horizontal fica igual.
            int next = Ui.isPortrait(this)
                ? Math.round(Ui.dp(this, 168))
                : Math.round(usable / VISIBLE_ROWS);
            if (next != rowHeight) {
                rowHeight = next;
                v.post(adapter::notifyDataSetChanged);
            }
        });
        Touch.install(findViewById(R.id.apps_back));
        Touch.install(tabAll);
        Touch.install(tabFav);

        // Dock embaixo da gaveta: alvo do arraste e também navega. Segurar um app começa o arraste.
        dock = DockNavigator.attachDock(this, DockView.Item.APPS);
        dock.setListener(this::onDockNav);
        dock.setEditor(new DockEditor(this, dock));
        grid.setOnItemLongClickListener((parent, view, position, id) -> startAppDrag(view, position));
    }

    /** Navegação pelo dock dentro da gaveta: Início/Apps fecham a gaveta; o resto abre e fecha. */
    private void onDockNav(DockView.Item item, String pkg) {
        if (item == DockView.Item.APPS) return; // já estamos na gaveta
        if (item == DockView.Item.HOME) {
            finish();
            return;
        }
        if (item != null) DockNavigator.navigate(this, null, item);
        else DockNavigator.openApp(this, pkg);
        finish();
    }

    /** Segurar um app na gaveta começa o arraste; soltar sobre um slot do dock o substitui. */
    private boolean startAppDrag(View cell, int position) {
        if (position < 0 || position >= apps.size()) return false;
        AppCatalog.Entry entry = apps.get(position);
        cell.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        ClipData data = ClipData.newPlainText("app", entry.packageName);
        try {
            cell.startDrag(data, new View.DragShadowBuilder(cell), null, 0);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.hideSystemBars(getWindow());
        reload();
    }

    @Override
    protected void onDestroy() {
        loadGen++;
        loader.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        if (menu != null) menu.dismiss();
        grid.setAdapter(null);
        apps.clear();
        AppCatalog.recycle(new ArrayList<>(all));
        all.clear();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Ui.applyDesignDensity(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(getWindow());
    }

    private void reload() {
        final int gen = ++loadGen;
        final int iconPx = Math.round(Ui.dp(this, 88) * com.k4811.launcher.home.IconStyle.scale(this));
        loader.execute(() -> {
            List<AppCatalog.Entry> loaded = AppCatalog.load(getApplicationContext(), iconPx);
            handler.post(() -> {
                if (isFinishing() || gen != loadGen) {
                    AppCatalog.recycle(loaded);
                    return;
                }
                List<AppCatalog.Entry> previous = new ArrayList<>(all);
                all.clear();
                all.addAll(loaded);
                buildFilters();
                show();
                // The grid redraws on the next frames: free the old icons only after that.
                handler.postDelayed(() -> AppCatalog.recycle(previous), 1500);
            });
        });
    }

    private void show() {
        apps.clear();
        for (AppCatalog.Entry e : all) {
            if (tab == TAB_FAV ? favorites.contains(e.key()) : group == AppCatalog.GROUP_ALL || e.group == group) apps.add(e);
        }
        tabAll.setActivated(tab == TAB_ALL);
        tabFav.setActivated(tab == TAB_FAV);
        filters.setVisibility(tab == TAB_ALL && filterRow.getChildCount() > 2 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < filterRow.getChildCount(); i++) {
            View chip = filterRow.getChildAt(i);
            chip.setActivated(Integer.valueOf(group).equals(chip.getTag()));
        }
        boolean none = apps.isEmpty();
        grid.setVisibility(none ? View.GONE : View.VISIBLE);
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        empty.setText(tab == TAB_FAV && !all.isEmpty() ? R.string.home_apps_fav_empty : R.string.home_apps_empty);
        countView.setText(none ? "" : getResources().getQuantityString(R.plurals.home_apps_count, apps.size(), apps.size()));
        adapter.notifyDataSetChanged();
        if (!none && !animatedIn) {
            animatedIn = true;
            grid.scheduleLayoutAnimation();
        }
    }

    private void setTab(int t) {
        if (t == tab) return;
        tab = t;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_TAB, tab).apply();
        show();
        grid.setSelection(0);
        grid.scheduleLayoutAnimation();
    }

    /** Um botão por categoria que tem apps, mais "Todos"; com só uma categoria a linha some. */
    private void buildFilters() {
        int[] count = new int[AppCatalog.GROUPS];
        for (AppCatalog.Entry e : all) count[e.group]++;
        if (group != AppCatalog.GROUP_ALL && count[group] == 0) group = AppCatalog.GROUP_ALL;
        filterRow.removeAllViews();
        for (int g = 0; g < AppCatalog.GROUPS; g++) {
            if (g != AppCatalog.GROUP_ALL && count[g] == 0) continue;
            TextView chip = new TextView(this);
            chip.setText(GROUP_NAMES[g]);
            chip.setTextColor(Ui.TEXT);
            chip.setTextSize(18);
            chip.setTypeface(Ui.medium(this));
            chip.setGravity(android.view.Gravity.CENTER);
            int h = Math.round(Ui.dp(this, 18));
            chip.setPadding(h, 0, h, 0);
            com.k4811.common.ui.ThemeDrawables.setBackground(chip, R.drawable.pill);
            chip.setTag(g);
            int target = g;
            chip.setOnClickListener(v -> {
                group = target;
                show();
                grid.setSelection(0);
            });
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(Ui.dp(this, 44)));
            lp.rightMargin = Math.round(Ui.dp(this, 10));
            filterRow.addView(chip, lp);
            Touch.install(chip);
        }
    }

    /** Tocar e segurar: favoritos e informações do app, num painel no meio da tela. */
    private void showMenu(AppCatalog.Entry entry) {
        if (menu != null) menu.dismiss();
        boolean fav = favorites.contains(entry.key());
        android.widget.LinearLayout panel = new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = Math.round(Ui.dp(this, 22));
        panel.setPadding(p, p, p, p);
        com.k4811.common.ui.ThemeDrawables.setBackground(panel, R.drawable.panel);
        TextView title = new TextView(this);
        title.setText(entry.label);
        title.setTextColor(Ui.TEXT);
        title.setTextSize(26);
        title.setTypeface(Ui.semiBold(this));
        title.setSingleLine(true);
        panel.addView(title);
        menuButton(panel, fav ? R.string.home_apps_fav_remove : R.string.home_apps_fav_add, () -> {
            if (fav) favorites.remove(entry.key());
            else favorites.add(entry.key());
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(KEY_FAVORITES, new java.util.HashSet<>(favorites)).apply();
            show();
        });
        menuButton(panel, R.string.home_icon_change, () -> com.k4811.launcher.home.IconPicker.show(this,
            entry.packageName, entry.className, entry.label, this::reload));
        menuButton(panel, R.string.home_apps_info, () -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", entry.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Toast.makeText(this, R.string.home_apps_not_found, Toast.LENGTH_SHORT).show();
            }
        });
        Ui.applyFont(panel);
        menu = new android.app.Dialog(this);
        menu.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        menu.setContentView(panel, new ViewGroup.LayoutParams(Math.round(Ui.dp(this, 460)), ViewGroup.LayoutParams.WRAP_CONTENT));
        if (menu.getWindow() != null) {
            menu.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        }
        menu.show();
        if (menu.getWindow() != null) Ui.hideSystemBars(menu.getWindow());
    }

    private void menuButton(android.widget.LinearLayout panel, int text, Runnable action) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Ui.TEXT);
        b.setTextSize(22);
        b.setTypeface(Ui.medium(this));
        b.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int h = Math.round(Ui.dp(this, 20));
        b.setPadding(h, 0, h, 0);
        com.k4811.common.ui.ThemeDrawables.setBackground(b, R.drawable.pill);
        b.setOnClickListener(v -> {
            if (menu != null) menu.dismiss();
            action.run();
        });
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.round(Ui.dp(this, 64)));
        lp.topMargin = Math.round(Ui.dp(this, 14));
        panel.addView(b, lp);
        Touch.install(b);
    }

    /** Each icon rises a little, grows from 92% and fades in, one shortly after the other. */
    private LayoutAnimationController cascade() {
        AnimationSet set = new AnimationSet(true);
        set.addAnimation(new AlphaAnimation(0f, 1f));
        set.addAnimation(new TranslateAnimation(0, 0, Ui.dp(this, 36), 0));
        set.addAnimation(new ScaleAnimation(0.92f, 1f, 0.92f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f));
        set.setDuration(320);
        set.setInterpolator(new DecelerateInterpolator(1.6f));
        LayoutAnimationController c = new LayoutAnimationController(set, 0.12f);
        c.setOrder(LayoutAnimationController.ORDER_NORMAL);
        return c;
    }

    private void open(View cell, int position) {
        if (position < 0 || position >= apps.size()) return;
        AppCatalog.Entry entry = apps.get(position);
        cell.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(entry.packageName, entry.className));
            // Our own screens (e.g. Ventoinha) have no LAUNCHER category; external apps do.
            if (!getPackageName().equals(entry.packageName)) {
                i.setAction(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // The app grows out of its icon.
            Bundle zoom = ActivityOptions.makeScaleUpAnimation(cell, 0, 0, cell.getWidth(), cell.getHeight()).toBundle();
            startActivity(i, zoom);
        } catch (Exception e) {
            Toast.makeText(this, R.string.home_apps_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private final class AppsAdapter extends BaseAdapter {
        private final OvershootInterpolator spring = new OvershootInterpolator(3f);
        private final DecelerateInterpolator press = new DecelerateInterpolator();

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View cell = convertView;
            if (cell == null) {
                cell = getLayoutInflater().inflate(R.layout.app_cell, parent, false);
                Ui.applyFont(cell);
                ((TextView) cell.findViewById(R.id.app_label)).setTypeface(Ui.medium(AppsActivity.this));
                // Sinks under the finger and springs back; the grid still gets the tap and the scroll.
                cell.setOnTouchListener((v, e) -> {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            v.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                                .setDuration(90).setInterpolator(press).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(spring).start();
                            break;
                        default:
                            break;
                    }
                    return false;
                });
            }
            if (rowHeight > 0) {
                ViewGroup.LayoutParams lp = cell.getLayoutParams();
                if (lp == null || lp.height != rowHeight) {
                    cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
                }
            }
            AppCatalog.Entry entry = apps.get(position);
            cell.setContentDescription(entry.label);
            ((TextView) cell.findViewById(R.id.app_label)).setText(entry.label);
            ImageView icon = (ImageView) cell.findViewById(R.id.app_icon);
            int iconSize = Math.round(Ui.dp(AppsActivity.this, 88) * com.k4811.launcher.home.IconStyle.scale(AppsActivity.this));
            if (icon.getLayoutParams().width != iconSize) {
                icon.getLayoutParams().width = iconSize;
                icon.getLayoutParams().height = iconSize;
            }
            if (entry.icon != null) icon.setImageBitmap(entry.icon);
            else icon.setImageDrawable(null);
            return cell;
        }
    }
}
