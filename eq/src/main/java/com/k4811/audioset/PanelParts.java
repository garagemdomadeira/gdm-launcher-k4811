package com.k4811.audioset;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.ui.ThemeDrawables;
import com.k4811.common.ui.Ui;

/** Peças montadas em código das abas do EQ (Graves, Meus EQs), com o visual das outras abas. */
final class PanelParts {

    private PanelParts() {
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, 8, 0, 0);
        return l;
    }

    static TextView title(Context c, int res) {
        TextView tv = label(c, c.getString(res), Ui.TEXT, 22, 0);
        tv.setTypeface(Ui.semiBold(c));
        return tv;
    }

    static TextView label(Context c, String text, int color, float size, int top) {
        TextView tv = new TextView(c);
        tv.setTypeface(Ui.medium(c));
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        tv.setPadding(0, top, 0, 0);
        return tv;
    }

    static TextView chip(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setTypeface(Ui.medium(c));
        tv.setText(text);
        tv.setTextColor(Ui.TEXT);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        ThemeDrawables.setBackground(tv, R.drawable.pill);
        tv.setPadding(16, 10, 16, 10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = 8;
        tv.setLayoutParams(lp);
        return tv;
    }

    static View spaced(View v, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        v.setLayoutParams(lp);
        return v;
    }

    static LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        return lp;
    }

    static LinearLayout.LayoutParams column(float weight, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.leftMargin = left;
        return lp;
    }
}
