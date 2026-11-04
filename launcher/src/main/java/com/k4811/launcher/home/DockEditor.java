package com.k4811.launcher.home;

import android.content.Context;

import com.k4811.common.config.DockConfig;
import com.k4811.common.ui.DockView;

/**
 * Edição do dock a partir do launcher (o único que grava o {@code dock.json}): soltar um app da
 * gaveta insere, arrastar um ícone do dock reordena, e soltar fora do dock remove. Depois de gravar,
 * atualiza o dock (adiando para depois do evento de arraste).
 */
public final class DockEditor implements DockView.Editor {

    private final Context context;
    private final DockView dock;

    public DockEditor(Context context, DockView dock) {
        this.context = context;
        this.dock = dock;
    }

    @Override
    public void onAdd(int atStoredIndex, String pkg) {
        if (DockConfig.insert(context, atStoredIndex, pkg)) refresh();
    }

    @Override
    public void onReorder(int fromStoredIndex, int toStoredIndex) {
        if (DockConfig.move(context, fromStoredIndex, toStoredIndex)) refresh();
    }

    @Override
    public void onRemove(int fromStoredIndex) {
        if (DockConfig.remove(context, fromStoredIndex)) refresh();
    }

    private void refresh() {
        dock.post(dock::refresh); // depois do evento de arraste, não durante
    }
}
