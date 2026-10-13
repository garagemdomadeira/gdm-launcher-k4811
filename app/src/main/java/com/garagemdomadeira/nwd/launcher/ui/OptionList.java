package com.garagemdomadeira.nwd.launcher.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.garagemdomadeira.nwd.launcher.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Option rows for Settings and Advanced, built in code: upper-case
 * title per group, rows separated by thin lines, On/Off, − value + (hold repeats), pick
 * from a list, shortcuts and read-only rows.
 *
 * Each row reads the real state in {@link #renderAll()} (the screen calls it again when the system
 * reports a change). After applying, checks after 1.5 s; if unchanged, shows "not applied" and,
 * if available, the adb command. A row whose value is null (the device lacks the option) is hidden.
 */
public final class OptionList {

    public interface Bool {
        /** null = option not present on this device. */
        Boolean get();
    }

    public interface BoolSetter {
        void set(boolean on);
    }

    public interface Int {
        /** null = option not present on this device. */
        Integer get();
    }

    public interface IntSetter {
        void set(int value);
    }

    public interface Text {
        String get();
    }

    public interface Check {
        boolean ok();
    }

    public interface Format {
        String format(int value);
    }

    /** adb command to apply the value by hand, shown when the system didn't apply it. */
    public interface Hint {
        String forValue(String value);
    }

    public static final long VERIFY_MS = 1500;
    private static final long REPEAT_DELAY_MS = 400;
    private static final long REPEAT_MS = 110;

    private final Activity activity;
    private LinearLayout container;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Row> rows = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private Section current;
    private Dialog dialog;
    private Runnable afterRender;

    public OptionList(Activity activity, LinearLayout container) {
        this.activity = activity;
        this.container = container;
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        if (dialog != null) dialog.dismiss();
    }

    /** Called at the end of each {@link #renderAll()}, so the screen updates non-row items (counters…). */
    public void setAfterRender(Runnable r) {
        afterRender = r;
    }

    /** Re-reads all rows. */
    public void renderAll() {
        for (Row r : rows) r.refresh();
        for (Section s : sections) {
            boolean any = false;
            for (Row r : s.rows) any |= r.view.getVisibility() == View.VISIBLE;
            s.header.setVisibility(any ? View.VISIBLE : View.GONE);
            for (View n : s.notes) n.setVisibility(any ? View.VISIBLE : View.GONE);
        }
        if (afterRender != null) afterRender.run();
    }

    // ------------------------------------------------------------------ building

    /** Starts a group (SOUND, DISPLAY, REVERSE…). Hides itself if none of its rows are visible. */
    public void section(String title) {
        TextView t = new TextView(activity);
        t.setText(title.toUpperCase());
        t.setTextColor(Ui.TEXT_MUTED);
        t.setTextSize(15);
        if (android.os.Build.VERSION.SDK_INT >= 21) t.setLetterSpacing(0.18f);
        t.setTypeface(Ui.semiBold(activity));
        t.setPadding(0, px(34), 0, px(10));
        container.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        current = new Section(t);
        sections.add(current);
    }

    /** From here on, groups and rows go into {@code panel} (Settings builds one panel per side tab). */
    public void into(LinearLayout panel) {
        container = panel;
        current = null;
    }

    /** Free text (warning, explanation) inside the current group. */
    public TextView note(String text, int color) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(18);
        t.setTypeface(Ui.medium(activity));
        t.setPadding(0, px(6), 0, px(14));
        container.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (current != null) current.notes.add(t);
        return t;
    }

    public Row toggle(String title, String subtitle, Bool get, BoolSetter set) {
        return add(new ToggleRow(title, subtitle, get, set));
    }

    public Row stepper(String title, String subtitle, Int min, Int max, Int get, IntSetter set, Format format) {
        return add(new StepperRow(title, subtitle, min, max, get, set, format));
    }

    public Row choice(String title, String subtitle, String[] labels, int[] values, Int get, IntSetter set) {
        return add(new ChoiceRow(title, subtitle, labels, values, get, set));
    }

    public Row action(String title, String subtitle, String button, Runnable onClick) {
        return add(new ActionRow(title, subtitle, button, onClick));
    }

    /** Row with only title and subtitle; buttons are added later with {@link Row#addButton}. */
    public Row plain(String title, String subtitle) {
        return add(new ActionRow(title, subtitle, null, null));
    }

    /** Row with a custom control on the right (e.g. color swatches). */
    public Row custom(String title, String subtitle, View content) {
        Row r = new ActionRow(title, subtitle, null, null);
        r.controls.addView(content);
        return add(r);
    }

    /** Read-only row: title on the left, value on the right. Hidden if the value is null. */
    public Row info(String title, Text value) {
        return add(new InfoRow(title, value));
    }

    private Row add(Row r) {
        rows.add(r);
        if (current != null) current.rows.add(r);
        container.addView(r.view);
        View line = new View(activity);
        line.setBackgroundColor(Ui.HAIRLINE);
        container.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1)));
        r.divider = line;
        r.refresh();
        return r;
    }

    private int px(float dp) {
        return Math.round(Ui.dp(activity, dp));
    }

    private TextView button(ViewGroup parent, String text) {
        TextView b = (TextView) activity.getLayoutInflater().inflate(R.layout.option_button, parent, false);
        b.setText(text);
        b.setTypeface(Ui.medium(activity));
        return b;
    }

    // ------------------------------------------------------------------ rows

    private static final class Section {
        final TextView header;
        final List<Row> rows = new ArrayList<>();
        final List<View> notes = new ArrayList<>();

        Section(TextView header) {
            this.header = header;
        }
    }

    /** A row: title, subtitle, status note and controls on the right. */
    public abstract class Row {
        final LinearLayout view;
        final LinearLayout controls;
        final TextView titleView;
        final TextView subtitleView;
        final TextView noteView;
        View divider;
        private Check visibleWhen;
        private Check enabledWhen;
        Hint hint;
        boolean local;
        private Runnable verify;

        Row(String title, String subtitle) {
            view = new LinearLayout(activity);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setMinimumHeight(px(96));
            view.setPadding(0, px(12), 0, px(12));
            view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout texts = new LinearLayout(activity);
            texts.setOrientation(LinearLayout.VERTICAL);
            view.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            titleView = new TextView(activity);
            titleView.setText(title);
            titleView.setTextColor(Ui.TEXT);
            titleView.setTextSize(26);
            titleView.setTypeface(Ui.medium(activity));
            texts.addView(titleView);

            subtitleView = new TextView(activity);
            subtitleView.setTextColor(Ui.TEXT_MUTED);
            subtitleView.setTextSize(18);
            subtitleView.setTypeface(Ui.medium(activity));
            subtitleView.setPadding(0, px(4), px(16), 0);
            texts.addView(subtitleView);
            setSubtitle(subtitle);

            noteView = new TextView(activity);
            noteView.setTextSize(16);
            noteView.setTypeface(Ui.medium(activity));
            noteView.setPadding(0, px(4), px(16), 0);
            noteView.setTextIsSelectable(true);
            noteView.setVisibility(View.GONE);
            texts.addView(noteView);

            controls = new LinearLayout(activity);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER_VERTICAL);
            view.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        /** The row only shows when the condition holds (e.g. package installed). */
        public Row visibleWhen(Check c) {
            visibleWhen = c;
            refresh();
            return this;
        }

        /** When the condition is false, the row is greyed out and unresponsive. */
        public Row enabledWhen(Check c) {
            enabledWhen = c;
            refresh();
            return this;
        }

        /** Garagem's own preference: applies immediately, without checking or showing "not applied". */
        public Row local() {
            local = true;
            return this;
        }

        /** Extra button on the right of the row. */
        public TextView addButton(String text, Runnable onClick) {
            TextView b = button(controls, text);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
            if (lp == null) lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(56));
            lp.leftMargin = px(14);
            controls.addView(b, lp);
            b.setOnClickListener(v -> onClick.run());
            return b;
        }

        /** adb command shown alongside "not applied". */
        public Row hint(Hint h) {
            hint = h;
            return this;
        }

        public void setSubtitle(String text) {
            subtitleView.setText(text == null ? "" : text);
            subtitleView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
        }

        /** Status note below the subtitle (✓, "not applied", warning). null clears it. */
        public void setNote(String text, boolean error) {
            noteView.setText(text == null ? "" : text);
            noteView.setTextColor(error ? Ui.DANGER : Ui.TEXT_MUTED);
            noteView.setVisibility(text == null ? View.GONE : View.VISIBLE);
        }

        final void refresh() {
            boolean visible = (visibleWhen == null || visibleWhen.ok()) && render();
            int vis = visible ? View.VISIBLE : View.GONE;
            view.setVisibility(vis);
            if (divider != null) divider.setVisibility(vis);
            boolean enabled = enabledWhen == null || enabledWhen.ok();
            view.setAlpha(enabled ? 1f : 0.4f);
            setEnabledDeep(view, enabled);
        }

        private void setEnabledDeep(View v, boolean enabled) {
            v.setEnabled(enabled);
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), enabled);
            }
        }

        /** Updates the controls with the read state. Returns false if the option doesn't exist (row hides). */
        abstract boolean render();

        /** Checks after 1.5 s whether the system applied {@code expected}; otherwise shows "not applied". */
        void verifyLater(Check applied, String value) {
            if (local) {
                renderAll();
                return;
            }
            if (verify != null) handler.removeCallbacks(verify);
            setNote(activity.getString(R.string.settings_applying), false);
            verify = () -> {
                verify = null;
                if (activity.isFinishing()) return;
                if (applied.ok()) {
                    setNote(activity.getString(R.string.settings_check_mark), false);
                    handler.postDelayed(() -> {
                        if (verify == null) setNote(null, false);
                    }, 2500);
                } else {
                    String adb = hint != null ? hint.forValue(value) : null;
                    setNote(adb != null ? activity.getString(R.string.settings_not_applied_adb, adb)
                        : activity.getString(R.string.settings_not_applied), true);
                }
                refresh();
            };
            handler.postDelayed(verify, VERIFY_MS);
        }
    }

    private final class ToggleRow extends Row {
        private final Bool get;
        private final TextView button;

        ToggleRow(String title, String subtitle, Bool get, BoolSetter set) {
            super(title, subtitle);
            this.get = get;
            button = button(controls, "");
            controls.addView(button);
            View.OnClickListener toggle = v -> {
                Boolean now = get.get();
                if (now == null) return;
                boolean want = !now;
                set.set(want);
                verifyLater(() -> {
                    Boolean after = get.get();
                    return after != null && after == want;
                }, want ? "1" : "0");
                render();
            };
            button.setOnClickListener(toggle);
            // The whole row also toggles (bigger finger target while the car is moving).
            view.setOnClickListener(toggle);
        }

        @Override
        boolean render() {
            Boolean on = get.get();
            if (on == null) return false;
            button.setText(on ? R.string.settings_on : R.string.settings_off);
            button.setActivated(on);
            return true;
        }
    }

    private final class StepperRow extends Row {
        private final Int min;
        private final Int max;
        private final Int get;
        private final IntSetter set;
        private final Format format;
        private final TextView value;
        /** Requested value not yet confirmed (so consecutive taps add up). */
        private Integer target;

        StepperRow(String title, String subtitle, Int min, Int max, Int get, IntSetter set, Format format) {
            super(title, subtitle);
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
            this.format = format;
            IconView minus = stepButton(IconView.Icon.MINUS, -1);
            value = new TextView(activity);
            value.setTextColor(Ui.TEXT);
            value.setTextSize(26);
            value.setTypeface(Ui.semiBold(activity));
            value.setGravity(Gravity.CENTER);
            value.setMinWidth(px(110));
            IconView plus = stepButton(IconView.Icon.PLUS, 1);
            controls.addView(minus, new LinearLayout.LayoutParams(px(64), px(64)));
            controls.addView(value);
            controls.addView(plus, new LinearLayout.LayoutParams(px(64), px(64)));
        }

        @SuppressLint("ClickableViewAccessibility")
        private IconView stepButton(IconView.Icon icon, int delta) {
            IconView b = new IconView(activity);
            b.set(icon, IconView.Style.OUTLINE_CIRCLE);
            final Runnable[] repeat = new Runnable[1];
            repeat[0] = () -> {
                step(delta);
                handler.postDelayed(repeat[0], REPEAT_MS);
            };
            // Custom touch (hold repeats): Touch.install must not replace this listener.
            b.setTag(R.id.touch_installed, Boolean.TRUE);
            b.setOnTouchListener((v, e) -> {
                if (!v.isEnabled()) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        Touch.haptic(v);
                        v.setPressed(true);
                        step(delta);
                        handler.postDelayed(repeat[0], REPEAT_DELAY_MS);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        handler.removeCallbacks(repeat[0]);
                        return true;
                    default:
                        return true;
                }
            });
            return b;
        }

        private void step(int delta) {
            Integer now = target != null ? target : get.get();
            Integer lo = min.get();
            Integer hi = max.get();
            if (now == null || lo == null || hi == null) return;
            int next = Math.max(lo, Math.min(hi, now + delta));
            if (next == now) return;
            target = next;
            set.set(next);
            value.setText(format.format(next));
            final int want = next;
            verifyLater(() -> {
                Integer after = get.get();
                boolean ok = after != null && after == want;
                if (target != null && target == want) target = null;
                return ok;
            }, String.valueOf(next));
        }

        @Override
        boolean render() {
            Integer v = get.get();
            if (v == null) return false;
            value.setText(format.format(target != null ? target : v));
            return true;
        }
    }

    private final class ChoiceRow extends Row {
        private final String[] labels;
        private final int[] values;
        private final Int get;
        private final TextView button;

        ChoiceRow(String title, String subtitle, String[] labels, int[] values, Int get, IntSetter set) {
            super(title, subtitle);
            this.labels = labels;
            this.values = values;
            this.get = get;
            button = button(controls, "");
            button.setMinWidth(px(190));
            controls.addView(button);
            View.OnClickListener open = v -> {
                Integer now = get.get();
                int selected = -1;
                for (int i = 0; i < values.length; i++) if (now != null && values[i] == now) selected = i;
                showChoices(titleView.getText().toString(), labels, null, selected, i -> {
                    int want = values[i];
                    set.set(want);
                    verifyLater(() -> {
                        Integer after = get.get();
                        return after != null && after == want;
                    }, String.valueOf(want));
                    render();
                });
            };
            button.setOnClickListener(open);
            view.setOnClickListener(open);
        }

        @Override
        boolean render() {
            Integer v = get.get();
            if (v == null) return false;
            String label = String.valueOf(v);
            for (int i = 0; i < values.length; i++) if (values[i] == v) label = labels[i];
            button.setText(label);
            return true;
        }
    }

    private final class ActionRow extends Row {
        ActionRow(String title, String subtitle, String buttonText, Runnable onClick) {
            super(title, subtitle);
            if (buttonText == null) return;
            TextView b = button(controls, buttonText);
            controls.addView(b);
            b.setOnClickListener(v -> onClick.run());
            view.setOnClickListener(v -> onClick.run());
        }

        @Override
        boolean render() {
            return true;
        }
    }

    private final class InfoRow extends Row {
        private final Text value;
        private final TextView valueView;

        InfoRow(String title, Text value) {
            super(title, null);
            this.value = value;
            view.setMinimumHeight(px(64));
            titleView.setTextSize(22);
            valueView = new TextView(activity);
            valueView.setTextColor(Ui.TEXT_MUTED);
            valueView.setTextSize(20);
            valueView.setTypeface(Ui.medium(activity));
            valueView.setGravity(Gravity.END);
            valueView.setMaxWidth(px(700));
            valueView.setTextIsSelectable(true);
            controls.addView(valueView);
        }

        @Override
        boolean render() {
            String v = value.get();
            if (v == null) return false;
            valueView.setText(v);
            return true;
        }
    }

    // ------------------------------------------------------------------ choice dialog

    public interface OnPick {
        void pick(int index);
    }

    /**
     * List of options in a panel over the screen. {@code icons} (optional) puts an icon to the
     * left of each option; {@code selected} is highlighted.
     */
    public void showChoices(String title, String[] labels, Bitmap[] icons, int selected, OnPick onPick) {
        if (dialog != null) dialog.dismiss();
        Dialog d = new Dialog(activity, R.style.OverlayDialog);
        android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
        root.setOnClickListener(v -> d.dismiss());

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        ThemeDrawables.setBackground(panel, R.drawable.panel);
        panel.setClickable(true);
        panel.setPadding(px(28), px(24), px(28), px(24));
        android.widget.FrameLayout.LayoutParams plp = new android.widget.FrameLayout.LayoutParams(px(640),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        plp.topMargin = px(30);
        plp.bottomMargin = px(30);
        root.addView(panel, plp);

        TextView t = new TextView(activity);
        t.setText(title);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(26);
        t.setTypeface(Ui.semiBold(activity));
        t.setPadding(0, 0, 0, px(14));
        panel.addView(t);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            ThemeDrawables.setBackground(line, R.drawable.list_row);
            line.setPadding(px(14), 0, px(14), 0);
            line.setActivated(i == selected);
            if (icons != null && icons[i] != null) {
                ImageView icon = new ImageView(activity);
                icon.setImageBitmap(icons[i]);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(px(44), px(44));
                ilp.rightMargin = px(16);
                line.addView(icon, ilp);
            }
            TextView label = new TextView(activity);
            label.setText(labels[i]);
            label.setTextSize(22);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTypeface(i == selected ? Ui.semiBold(activity) : Ui.medium(activity));
            label.setTextColor(i == selected ? Ui.TEXT : Ui.TEXT_MUTED);
            line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i == selected) {
                TextView mark = new TextView(activity);
                mark.setText(R.string.settings_check_mark);
                mark.setTextColor(Ui.TEXT);
                mark.setTextSize(22);
                line.addView(mark);
            }
            line.setOnClickListener(v -> {
                d.dismiss();
                onPick.pick(index);
            });
            list.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(68)));
        }
        // Long list: cap the height to fit the 720 screen.
        scroll.post(() -> {
            int maxH = px(520);
            if (scroll.getHeight() > maxH) {
                scroll.getLayoutParams().height = maxH;
                scroll.requestLayout();
            }
        });

        d.setContentView(root);
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        Ui.hideSystemBars(d.getWindow());
        d.show();
        Touch.install(root);
        dialog = d;
    }
}
