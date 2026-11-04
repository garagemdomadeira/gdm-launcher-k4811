package com.k4811.android.phone;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.k4811.common.bt.BtClient;
import com.k4811.common.ui.IconView;
import com.k4811.common.ui.Ui;

/**
 * Janela flutuante da chamada (papel do FloatBar do app original): um cartão no alto da tela, por
 * cima de qualquer app, com nome ou número, o estado (recebendo, efetuando, em conversa),
 * Atender e Desligar. Tocar no cartão abre o telefone. Some quando a chamada termina e enquanto a
 * tela do telefone está na frente (ela já mostra a chamada).
 *
 * A janela usa TYPE_SYSTEM_ALERT com SYSTEM_ALERT_WINDOW, concedida na instalação (targetSdk < 23).
 */
public class CallFloatService extends Service {
    private static final String TAG = "K4811CallFloat";
    private static final String EXTRA_SUPPRESS = "suppress";
    private static final String EXTRA_HOME_CARD = "home_card";

    private static final int NONE = 0;
    private static final int IN = 1;
    private static final int OUT = 2;
    private static final int ONLINE = 3;

    /** Desenho em 1280×720: 700×220 a 48 do topo, centralizado (como o original). */
    private static final int WIDTH_DP = 700;
    private static final int HEIGHT_DP = 220;
    private static final int TOP_DP = 48;

    private WindowManager wm;
    private BtClient bt;
    private View card;
    private TextView nameView;
    private TextView stateView;
    private TextView answerView;
    private float scale = 1f;
    private int state = NONE;
    private String name = "";
    private String number = "";
    /** A tela do telefone está na frente: o cartão fica escondido. */
    private boolean suppressed;
    /** A tela inicial está mostrando o próprio cartão da ligação: este fica escondido. */
    private boolean homeCard;
    private boolean receiverRegistered;

    /** Chamada começou (ou número/nome chegou): mostra ou atualiza o cartão. */
    public static void onCall(Context context, Intent broadcast) {
        Intent i = new Intent(context, CallFloatService.class);
        i.setAction(broadcast.getAction());
        if (broadcast.getExtras() != null) i.putExtras(broadcast.getExtras());
        start(context, i);
    }

    /** A tela do telefone entrou ({@code true}) ou saiu da frente. */
    public static void setPhoneInFront(Context context, boolean inFront) {
        Intent i = new Intent(context, CallFloatService.class);
        i.putExtra(EXTRA_SUPPRESS, inFront);
        start(context, i);
    }

    /** A tela inicial mostra ({@code true}) ou deixou de mostrar o cartão dela da ligação. */
    public static void setHomeCard(Context context, boolean shown) {
        Intent i = new Intent(context, CallFloatService.class);
        i.putExtra(EXTRA_HOME_CARD, shown);
        start(context, i);
    }

    private static void start(Context context, Intent i) {
        try {
            context.startService(i);
        } catch (Exception e) {
            Log.w(TAG, "não iniciou: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        scale = Math.min(Math.max(size.x, size.y) / (float) Ui.DESIGN_WIDTH,
            Math.min(size.x, size.y) / (float) Ui.DESIGN_HEIGHT);
        Ui.applyThemeColors(this);
        bt = new BtClient(this);
        bt.connect();
        IntentFilter f = new IntentFilter();
        for (String a : CALL_ACTIONS) f.addAction(a);
        registerReceiver(receiver, f);
        receiverRegistered = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.hasExtra(EXTRA_SUPPRESS)) suppressed = intent.getBooleanExtra(EXTRA_SUPPRESS, false);
            if (intent.hasExtra(EXTRA_HOME_CARD)) homeCard = intent.getBooleanExtra(EXTRA_HOME_CARD, false);
            if (intent.getAction() != null) apply(intent);
        }
        render();
        return START_NOT_STICKY;
    }

    private static final String[] CALL_ACTIONS = {
        "com.bt.ACTION_BT_INCOMING_CALL", "com.bt.ACTION_BT_OUTGOING_CALL",
        "com.bt.ACTION_BT_BEGIN_CALL_ONLINE", "com.bt.ACTION_BT_END_CALL",
        "com.bt.ACTION_BT_INCOMING_NUMBER", "com.bt.ACTION_BT_OUTGOING_NUMBER",
        "com.bt.ACTION_BT_INCOMING_NAME", "com.bt.ACTION_BT_OUTGOING_NAME",
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            apply(intent);
            render();
        }
    };

    private void apply(Intent intent) {
        String a = intent.getAction();
        if (a == null) return;
        switch (a) {
            case "com.bt.ACTION_BT_INCOMING_CALL":
                if (state == NONE) { name = ""; number = ""; }
                state = IN;
                break;
            case "com.bt.ACTION_BT_OUTGOING_CALL":
                if (state == NONE) { name = ""; number = ""; }
                state = OUT;
                break;
            case "com.bt.ACTION_BT_BEGIN_CALL_ONLINE":
                state = ONLINE;
                break;
            case "com.bt.ACTION_BT_END_CALL":
                state = NONE;
                break;
            case "com.bt.ACTION_BT_INCOMING_NUMBER":
            case "com.bt.ACTION_BT_OUTGOING_NUMBER": {
                String n = intent.getStringExtra("extra_phone_number");
                if (n != null) number = n.trim();
                break;
            }
            case "com.bt.ACTION_BT_INCOMING_NAME":
            case "com.bt.ACTION_BT_OUTGOING_NAME": {
                String n = intent.getStringExtra("extra_phone_name");
                if (n != null) name = n.trim();
                break;
            }
            default:
                break;
        }
    }

    private void render() {
        if (state == NONE) {
            removeCard();
            stopSelf();
            return;
        }
        if (suppressed || homeCard) {
            removeCard();
            return;
        }
        if (card == null) addCard();
        if (card == null) return;
        nameView.setText(!name.isEmpty() ? name : !number.isEmpty() ? number
            : getString(R.string.media_phone_call_caption));
        int st = state == IN ? R.string.media_phone_call_incoming
            : state == OUT ? R.string.media_phone_call_outgoing : R.string.media_phone_call_active;
        String sub = getString(st);
        if (!name.isEmpty() && !number.isEmpty()) sub = sub + "  ·  " + number;
        stateView.setText(sub);
        answerView.setVisibility(state == IN ? View.VISIBLE : View.GONE);
    }

    private int px(float dp) {
        return Math.round(dp * scale);
    }

    @SuppressWarnings("deprecation")
    private void addCard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(px(28), px(20), px(24), px(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        bg.setCornerRadius(px(26));
        bg.setStroke(Math.max(1, px(1.5f)), Ui.OUTLINE);
        root.setBackgroundDrawable(bg);
        root.setOnClickListener(v -> openPhone());

        IconView icon = new IconView(this).set(IconView.Icon.PHONE, IconView.Style.NONE).color(Ui.ACCENT);
        icon.setClickable(false);
        root.addView(icon, new LinearLayout.LayoutParams(px(72), px(72)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = px(22);
        root.addView(text, textLp);

        nameView = label(34, Ui.TEXT);
        nameView.setTypeface(Ui.semiBold(this));
        text.addView(nameView);
        stateView = label(22, Ui.TEXT_MUTED);
        stateView.setTypeface(Ui.regular(this));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = px(8);
        text.addView(stateView, stLp);

        answerView = button(R.string.media_phone_answer, Ui.SUCCESS);
        answerView.setOnClickListener(v -> bt.answer());
        root.addView(answerView, buttonLp());
        TextView hangup = button(R.string.media_phone_hang_up, Ui.DANGER);
        hangup.setOnClickListener(v -> bt.hangup());
        root.addView(hangup, buttonLp());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            px(WIDTH_DP), px(HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = px(TOP_DP);
        try {
            wm.addView(root, lp);
            card = root;
        } catch (Exception e) {
            Log.w(TAG, "janela flutuante recusada: " + e.getMessage());
        }
    }

    private TextView label(float sizeDp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizeDp));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    @SuppressWarnings("deprecation")
    private TextView button(int text, int color) {
        TextView b = label(24, color);
        b.setText(text);
        b.setTypeface(Ui.semiBold(this));
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(22), 0, px(22), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE_HIGH);
        bg.setCornerRadius(px(36));
        bg.setStroke(Math.max(1, px(1.5f)), color);
        b.setBackgroundDrawable(bg);
        return b;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, px(72));
        lp.leftMargin = px(14);
        return lp;
    }

    private void openPhone() {
        Intent i = new Intent(this, PhoneActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void removeCard() {
        if (card == null) return;
        try {
            wm.removeViewImmediate(card);
        } catch (Exception ignored) {
        }
        card = null;
    }

    @Override
    public void onDestroy() {
        removeCard();
        if (receiverRegistered) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
        }
        if (bt != null) bt.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
