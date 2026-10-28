package com.k4811.launcher.media;

import android.service.notification.NotificationListenerService;

/**
 * Exists only to unlock {@code MediaSessionManager.getActiveSessions} for the launcher: a normal app
 * can read/control media sessions once it is enabled in "Notification access". It doesn't handle any
 * notification. See {@link com.k4811.common.media.AppMediaClient}.
 */
public class MediaNotificationListener extends NotificationListenerService {
}
