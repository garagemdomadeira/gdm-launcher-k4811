package com.k4811.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Chamada recebida ou efetuada (broadcasts do serviço de BT da NWD). Com a tela do telefone na
 * frente, ela recebe o aviso (onNewIntent). Fora dela, aparece a janela flutuante da chamada
 * ({@link CallFloatService}), por cima de qualquer app; tocar nela abre o telefone. O número e o
 * nome chegam em broadcasts separados, logo depois, e seguem o mesmo caminho.
 */
public class CallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (com.k4811.common.Apps.ACTION_HOME_CALL_CARD.equals(intent.getAction())) {
            CallFloatService.setHomeCard(context, intent.getBooleanExtra(com.k4811.common.Apps.EXTRA_SHOWN, false));
            return;
        }
        if (PhoneActivity.isInFront()) {
            Intent open = new Intent(context, PhoneActivity.class);
            open.setAction(intent.getAction());
            if (intent.getExtras() != null) open.putExtras(intent.getExtras());
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                context.startActivity(open);
            } catch (Exception ignored) {
            }
            return;
        }
        CallFloatService.onCall(context, intent);
    }
}
