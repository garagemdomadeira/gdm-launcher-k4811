package com.k4811.launcher.media;

import android.annotation.TargetApi;
import android.service.notification.NotificationListenerService;

/**
 * Leitor de notificações: só libera ao launcher as sessões de mídia (nome, artista e capa do que toca, e os botões)
 * quando ele não é app privilegiado. Nada é lido nem guardado das notificações. Na ROM fica ligado de fábrica; instalado
 * à parte, liga em Configurações → Acesso a notificações.
 */
@TargetApi(21)
public class MediaListener extends NotificationListenerService {
}
