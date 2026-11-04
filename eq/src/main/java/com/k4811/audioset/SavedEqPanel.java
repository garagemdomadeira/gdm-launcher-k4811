package com.k4811.audioset;

import android.app.Activity;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.k4811.audioset.eq.CustomEqFiles;
import com.k4811.audioset.eq.EqController;
import com.k4811.common.eq.EqModel;
import com.k4811.common.ui.ThemeDrawables;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Aba Meus EQs, a parte de EQs personalizados do DspAdjust3 original: salvar a curva atual com um
 * nome, usar, apagar e levar para o pen drive e de volta (pasta {@code EQ_Setup}). Os arquivos são
 * os mesmos do original ({@link CustomEqFiles}). Apagar e substituir pedem um segundo toque.
 */
final class SavedEqPanel {

    private final Activity activity;
    private final EqController controller;
    private final EditText name;
    private final TextView save;
    private final TextView status;
    private final LinearLayout list;
    private final List<String> names = new ArrayList<>();
    /** Nome esperando o segundo toque (substituir ao salvar, ou apagar), ou null. */
    private String confirming;
    /** Botão Apagar esperando o segundo toque, ou null. */
    private TextView confirmChip;
    private EqModel.State state;

    SavedEqPanel(Activity activity, EqController controller, LinearLayout root) {
        this.activity = activity;
        this.controller = controller;

        LinearLayout left = PanelParts.vertical(activity);
        left.addView(PanelParts.title(activity, R.string.eq_save_current));
        name = new EditText(activity);
        name.setTypeface(Ui.medium(activity));
        name.setTextColor(Ui.TEXT);
        name.setHintTextColor(Ui.TEXT_MUTED);
        name.setTextSize(17);
        name.setHint(R.string.eq_name_hint);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(40)});
        ThemeDrawables.setBackground(name, R.drawable.pill);
        name.setPadding(20, 12, 20, 12);
        name.setText(suggestedName());
        name.setOnEditorActionListener((v, action, e) -> {
            onSave();
            return true;
        });
        left.addView(name, PanelParts.lp(14));
        save = PanelParts.chip(activity, activity.getString(R.string.eq_save));
        save.setOnClickListener(v -> onSave());
        left.addView(PanelParts.spaced(save, 12));

        left.addView(PanelParts.spaced(PanelParts.title(activity, R.string.eq_usb), 36));
        left.addView(PanelParts.label(activity, activity.getString(R.string.eq_usb_hint), Ui.TEXT_MUTED, 15, 6));
        LinearLayout usb = PanelParts.row(activity);
        TextView importChip = PanelParts.chip(activity, activity.getString(R.string.eq_import));
        importChip.setOnClickListener(v -> onImport());
        usb.addView(importChip);
        TextView exportAll = PanelParts.chip(activity, activity.getString(R.string.eq_export_all));
        exportAll.setOnClickListener(v -> onExport(new ArrayList<>(names)));
        usb.addView(exportAll);
        left.addView(usb, PanelParts.lp(8));
        status = PanelParts.label(activity, "", Ui.ACCENT, 15, 12);
        left.addView(status);

        LinearLayout right = PanelParts.vertical(activity);
        right.addView(PanelParts.title(activity, R.string.eq_saved_title));
        list = PanelParts.vertical(activity);
        right.addView(list, PanelParts.lp(6));

        root.addView(left, PanelParts.column(1f, 0));
        root.addView(right, PanelParts.column(1.4f, 32));
        Touch.install(root);
        refresh();
    }

    void render(EqModel.State s) {
        state = s;
        // Etiqueta "Em uso" (ativar a linha inteira acenderia também os botões dela).
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            Object tag = row.getTag();
            if (tag == null) continue;
            boolean inUse = s != null && tag.equals(s.customName);
            ((LinearLayout) row).getChildAt(1).setVisibility(inUse ? View.VISIBLE : View.GONE);
        }
    }

    /** Relê a pasta (ao abrir a aba e depois de cada mudança). */
    void refresh() {
        controller.listCustom(found -> {
            names.clear();
            if (found != null) names.addAll(found);
            confirming = null;
            confirmChip = null;
            buildList();
            render(state);
        });
    }

    private void buildList() {
        list.removeAllViews();
        if (names.isEmpty()) {
            list.addView(PanelParts.label(activity, activity.getString(R.string.eq_saved_empty), Ui.TEXT_MUTED, 17, 12));
            return;
        }
        for (final String n : names) {
            LinearLayout row = PanelParts.row(activity);
            row.setTag(n);
            ThemeDrawables.setBackground(row, R.drawable.pill);
            row.setPadding(18, 6, 8, 6);
            TextView label = PanelParts.label(activity, n, Ui.TEXT, 17, 0);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView inUse = PanelParts.label(activity, activity.getString(R.string.eq_in_use), Ui.TEXT_MUTED, 15, 0);
            inUse.setPadding(0, 0, 16, 0);
            inUse.setVisibility(View.GONE);
            row.addView(inUse);
            TextView use = PanelParts.chip(activity, activity.getString(R.string.eq_use));
            use.setOnClickListener(v -> onUse(n));
            row.addView(use);
            TextView export = PanelParts.chip(activity, activity.getString(R.string.eq_export));
            export.setOnClickListener(v -> {
                List<String> one = new ArrayList<>();
                one.add(n);
                onExport(one);
            });
            row.addView(export);
            final TextView delete = PanelParts.chip(activity, activity.getString(R.string.eq_delete));
            delete.setTextColor(Ui.DANGER);
            delete.setOnClickListener(v -> onDelete(n, delete));
            row.addView(delete);
            list.addView(row, PanelParts.lp(8));
        }
    }

    // ---- Ações ----

    private void onSave() {
        final String n = name.getText().toString().trim();
        if (!CustomEqFiles.validName(n)) {
            say(activity.getString(R.string.eq_name_invalid));
            return;
        }
        hideKeyboard();
        if (n.equals(confirming)) {
            doSave(n);
            return;
        }
        controller.customExists(n, exists -> {
            if (Boolean.TRUE.equals(exists)) {
                // Já existe: o segundo toque substitui.
                resetSave();
                confirming = n;
                save.setText(R.string.eq_replace);
                save.setActivated(true);
            } else {
                doSave(n);
            }
        });
    }

    private void doSave(String n) {
        controller.saveCustom(n, ok -> {
            resetSave();
            if (Boolean.TRUE.equals(ok)) {
                say(activity.getString(R.string.eq_saved_ok, n));
                name.setText(suggestedName());
                refresh();
            } else {
                say(activity.getString(R.string.eq_saved_fail));
            }
        });
    }

    private void onUse(String n) {
        controller.useCustom(n, ok -> {
            if (!Boolean.TRUE.equals(ok)) say(activity.getString(R.string.eq_use_fail));
        });
    }

    private void onDelete(String n, TextView chip) {
        if (chip != confirmChip) {
            resetSave();
            confirmChip = chip;
            chip.setText(R.string.eq_confirm_delete);
            chip.setActivated(true);
            return;
        }
        controller.deleteCustom(n, ok -> refresh());
    }

    private void onExport(List<String> which) {
        if (which.isEmpty()) {
            say(activity.getString(R.string.eq_saved_empty));
            return;
        }
        controller.exportCustom(which, n -> {
            if (n == null || n == 0) say(activity.getString(R.string.eq_usb_write_fail));
            else if (n < 0) say(activity.getString(R.string.eq_no_usb));
            else say(activity.getResources().getQuantityString(R.plurals.eq_exported, n, n));
        });
    }

    private void onImport() {
        controller.importCustom(n -> {
            if (n == null) say(activity.getString(R.string.eq_saved_fail));
            else if (n == -1) say(activity.getString(R.string.eq_no_usb));
            else if (n == -2) say(activity.getString(R.string.eq_no_usb_folder));
            else say(activity.getResources().getQuantityString(R.plurals.eq_imported, n, n));
            refresh();
        });
    }

    private void resetSave() {
        confirming = null;
        save.setText(R.string.eq_save);
        save.setActivated(false);
        if (confirmChip != null) {
            confirmChip.setText(R.string.eq_delete);
            confirmChip.setActivated(false);
            confirmChip = null;
        }
    }

    private void say(String text) {
        status.setText(text);
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(name.getWindowToken(), 0);
        name.clearFocus();
    }

    private static String suggestedName() {
        return "EQ_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
    }
}
