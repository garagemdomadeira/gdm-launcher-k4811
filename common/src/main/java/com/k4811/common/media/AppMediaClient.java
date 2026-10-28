package com.k4811.common.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.k4811.common.Apps;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and controls what any Android audio app (Spotify, Deezer, YouTube Music, podcasts…) is
 * playing, through {@code MediaSession}. Works on API 21+ once the launcher's
 * {@code NotificationListenerService} is turned on in "Notification access"; otherwise it is inert.
 *
 * <p>The suite's own apps (radio, MP3, Bluetooth music…) are ignored here, because they are handled
 * by their own clients. The active session is the one that is {@code PLAYING} (a paused one is used
 * only when nothing is playing).
 */
public final class AppMediaClient {

    private static final String TAG = "K4811AppMedia";
    private static final String ENABLED_LISTENERS = "enabled_notification_listeners";

    /** Snapshot of the active app session (immutable). */
    public static final class Info {
        public final String pkg;
        public final String appLabel;
        public final String title;
        public final String artist;
        public final boolean playing;
        public final boolean hasNext;
        public final boolean hasPrev;
        public final Bitmap art;

        Info(String pkg, String appLabel, String title, String artist, boolean playing,
             boolean hasNext, boolean hasPrev, Bitmap art) {
            this.pkg = pkg;
            this.appLabel = appLabel;
            this.title = title;
            this.artist = artist;
            this.playing = playing;
            this.hasNext = hasNext;
            this.hasPrev = hasPrev;
            this.art = art;
        }
    }

    public interface Listener {
        /** {@code info} null = no app session (or feature off). */
        void onChanged(Info info);
    }

    /** Suite packages to skip (they have their own handling). */
    public static Set<String> suitePackages() {
        return new HashSet<>(Arrays.asList(
            Apps.LAUNCHER, Apps.RADIO, Apps.MUSIC, Apps.BT_MUSIC, Apps.PHONE, Apps.EQ, Apps.FAN, Apps.SETTINGS));
    }

    /** True when the recurso can work: API 21+ and our notification listener is enabled. */
    public static boolean enabled(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        try {
            String flat = Settings.Secure.getString(c.getContentResolver(), ENABLED_LISTENERS);
            return flat != null && flat.contains(c.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ one-shot (media keys)

    /**
     * Sends next/previous to the app session that is currently playing. Returns true if it handled
     * the key (so the caller doesn't fall back to MP3/Bluetooth). Does nothing if nothing is playing.
     */
    public static boolean sendKey(Context c, ComponentName listener, boolean next) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !enabled(c)) return false;
        MediaController mc = activePlaying(c, listener);
        if (mc == null) return false;
        try {
            if (next) mc.getTransportControls().skipToNext();
            else mc.getTransportControls().skipToPrevious();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "sendKey: " + e.getMessage());
            return false;
        }
    }

    private static MediaController activePlaying(Context c, ComponentName listener) {
        Set<String> skip = suitePackages();
        for (MediaController mc : sessions(c, listener)) {
            if (mc == null || skip.contains(mc.getPackageName())) continue;
            PlaybackState st = mc.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) return mc;
        }
        return null;
    }

    private static List<MediaController> sessions(Context c, ComponentName listener) {
        try {
            MediaSessionManager m =
                (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            return m.getActiveSessions(listener);
        } catch (Exception e) {
            // SecurityException when the listener isn't enabled, or any framework hiccup.
            return java.util.Collections.emptyList();
        }
    }

    // ------------------------------------------------------------------ live client (home screen)

    private final Context context;
    private final ComponentName listener;
    private final Set<String> skip = suitePackages();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaSessionManager manager;
    private MediaController controller;
    private Listener out;
    private boolean started;

    public AppMediaClient(Context context, ComponentName listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void setListener(Listener l) {
        this.out = l;
    }

    /** Starts watching sessions. Inert (and safe to call again) when the permission isn't granted. */
    public void start() {
        if (started) return;
        if (!enabled(context)) {
            publish(null);
            return;
        }
        try {
            manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, main);
            started = true;
            refresh();
        } catch (Exception e) {
            Log.w(TAG, "start: " + e.getMessage());
        }
    }

    /** Re-reads the sessions. Call it when coming back to the screen (the permission may have changed). */
    public void refresh() {
        if (!started) {
            start();
            return;
        }
        pick();
    }

    public void stop() {
        if (manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (Exception ignored) {
            }
        }
        detach();
        manager = null;
        started = false;
    }

    public void playPause() {
        if (controller == null) return;
        try {
            PlaybackState st = controller.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        } catch (Exception e) {
            Log.w(TAG, "playPause: " + e.getMessage());
        }
    }

    public void next() {
        if (controller == null) return;
        try {
            controller.getTransportControls().skipToNext();
        } catch (Exception ignored) {
        }
    }

    public void prev() {
        if (controller == null) return;
        try {
            controller.getTransportControls().skipToPrevious();
        } catch (Exception ignored) {
        }
    }

    /** Package of the current app session, or null. */
    public String currentPackage() {
        return controller == null ? null : controller.getPackageName();
    }

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = controllers -> pick();

    /** Chooses the best session (a playing one wins; otherwise the first non-suite one). */
    private void pick() {
        MediaController best = null;
        for (MediaController mc : sessions(context, listener)) {
            if (mc == null || skip.contains(mc.getPackageName())) continue;
            if (best == null) best = mc;
            PlaybackState st = mc.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                best = mc;
                break;
            }
        }
        attach(best);
    }

    private void attach(MediaController next) {
        if (controller != null && (next == null || !sameSession(controller, next))) {
            try {
                controller.unregisterCallback(callback);
            } catch (Exception ignored) {
            }
        }
        boolean changedController = controller == null || next == null || !sameSession(controller, next);
        controller = next;
        if (controller != null && changedController) {
            try {
                controller.registerCallback(callback, main);
            } catch (Exception ignored) {
            }
        }
        publishCurrent();
    }

    private void detach() {
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Exception ignored) {
            }
            controller = null;
        }
    }

    private static boolean sameSession(MediaController a, MediaController b) {
        return a.getSessionToken().equals(b.getSessionToken());
    }

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            // A pause/stop here may mean another app took over: re-pick the active session.
            pick();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publishCurrent();
        }

        @Override
        public void onSessionDestroyed() {
            pick();
        }
    };

    private void publishCurrent() {
        publish(infoOf(controller));
    }

    private Info infoOf(MediaController mc) {
        if (mc == null) return null;
        MediaMetadata meta = safeMetadata(mc);
        PlaybackState st = mc.getPlaybackState();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        long actions = st == null ? 0 : st.getActions();
        boolean hasNext = (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0;
        boolean hasPrev = (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0;
        String title = metaText(meta, MediaMetadata.METADATA_KEY_TITLE);
        String artist = metaText(meta, MediaMetadata.METADATA_KEY_ARTIST);
        if (artist.isEmpty()) artist = metaText(meta, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        return new Info(mc.getPackageName(), appLabel(mc.getPackageName()), title, artist,
            playing, hasNext, hasPrev, art(meta));
    }

    private static MediaMetadata safeMetadata(MediaController mc) {
        try {
            return mc.getMetadata();
        } catch (Exception e) {
            return null;
        }
    }

    private static String metaText(MediaMetadata meta, String key) {
        if (meta == null) return "";
        CharSequence v = meta.getText(key);
        return v == null ? "" : v.toString().trim();
    }

    private static Bitmap art(MediaMetadata meta) {
        if (meta == null) return null;
        Bitmap b = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }

    private String appLabel(String pkg) {
        if (pkg == null) return "";
        try {
            PackageManager pm = context.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return label == null ? pkg : label.toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void publish(Info info) {
        Listener l = out;
        if (l != null) main.post(() -> {
            if (out != null) out.onChanged(info);
        });
    }
}
