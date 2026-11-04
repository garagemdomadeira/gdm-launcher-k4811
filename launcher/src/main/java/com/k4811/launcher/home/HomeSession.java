package com.k4811.launcher.home;

import com.k4811.common.audio.PlayerHandoff;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.acloud.newinterface.MusicBean;
import com.k4811.common.bt.BtClient;
import com.k4811.common.config.LauncherConfig;
import com.k4811.common.media.AppMediaClient;
import com.k4811.common.music.MusicRemote;
import com.k4811.common.system.NwdConstants;
import com.k4811.launcher.media.MediaNotificationListener;

/**
 * Current source, USB/SD track, Bluetooth music and call. Only publishes state:
 * audio is changed by the transport methods, called on touch.
 * O MP3 vem do app de música por broadcast ({@link MusicRemote}); os comandos também vão por
 * broadcast, e quem toca é sempre o app da fonte.
 */
public final class HomeSession {

    public enum Kind { NONE, RADIO, USB, BT, APP }

    public enum CallPhase { NONE, INCOMING, OUTGOING, ACTIVE }

    public static final class Snapshot {
        public final int source;
        public final Kind kind;
        public final CallPhase call;
        public final String callName;
        public final String callNumber;
        public final String title;
        public final String artist;
        public final String album;
        public final boolean playing;
        public final int positionMs;
        public final int durationMs;
        public final String mediaId;
        /** Track file (only in our player); null when the track comes from the NWD player. */
        public final String path;
        public final String btTitle;
        public final String btArtist;
        public final boolean btPlaying;
        public final boolean a2dpKnown;
        public final boolean a2dpConnected;
        /** App de mídia de terceiro (Spotify, Deezer…) na fonte Android; ver AppMediaClient. */
        public final String appPackage;
        public final String appLabel;
        public final String appTitle;
        public final String appArtist;
        public final boolean appPlaying;
        public final boolean appHasNext;
        public final boolean appHasPrev;
        public final Bitmap appArt;

        Snapshot(int source, Kind kind, CallPhase call, String callName, String callNumber,
                 String title, String artist, String album, boolean playing,
                 int positionMs, int durationMs, String mediaId, String path,
                 String btTitle, String btArtist, boolean btPlaying,
                 boolean a2dpKnown, boolean a2dpConnected,
                 String appPackage, String appLabel, String appTitle, String appArtist,
                 boolean appPlaying, boolean appHasNext, boolean appHasPrev, Bitmap appArt) {
            this.source = source;
            this.kind = kind;
            this.call = call;
            this.callName = callName;
            this.callNumber = callNumber;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.playing = playing;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.mediaId = mediaId;
            this.path = path;
            this.btTitle = btTitle;
            this.btArtist = btArtist;
            this.btPlaying = btPlaying;
            this.a2dpKnown = a2dpKnown;
            this.a2dpConnected = a2dpConnected;
            this.appPackage = appPackage;
            this.appLabel = appLabel;
            this.appTitle = appTitle;
            this.appArtist = appArtist;
            this.appPlaying = appPlaying;
            this.appHasNext = appHasNext;
            this.appHasPrev = appHasPrev;
            this.appArt = appArt;
        }
    }

    public interface Listener {
        void onChanged(Snapshot snapshot);
    }

    /** Buttons of the NWD music widget. */
    public static final int USB_NEXT = 0;
    public static final int USB_PREV = 1;
    public static final int USB_TOGGLE = 2;

    /** Bluetooth music playControl. 0 play, 1 pause, 2 next, 3 previous. */
    public static final int BT_PLAY = 0;
    public static final int BT_PAUSE = 1;
    public static final int BT_NEXT = 2;
    public static final int BT_PREV = 3;

    private static final String TAG = "K4811Home";
    private static final int SOURCE_ANDROID = 0;
    private static final int SOURCE_RADIO = 4;
    private static final int SOURCE_AUX = 7;
    private static final int SOURCE_DVR = 8;

    private static final String ACTION_WIDGET = "com.nwd.action.music.on_widget_button_click";
    private static final String EXTRA_BUTTON = "extra_button";
    private static final String ACTION_REFRESH_WIDGET = "action_refresh_music_widget_data";
    private static final String ACTION_META = "com.android.music.metachanged";
    private static final String ACTION_PLAYSTATE = "com.android.music.playstatechanged";
    private static final String ACTION_PROGRESS = "com.nwd.ACTION_REFRESH_WIDGETPLAYTIME";
    private static final String EXTRA_CUR = "extra_widgetcurtime";
    private static final String EXTRA_TOTAL = "extra_widgettotaltime";

    private static final String ACTION_BT_PLAY = "com.bt.ACTION_BT_MUSIC_PLAY";
    private static final String ACTION_BT_PAUSE = "com.bt.ACTION_BT_MUSIC_PAUSE";
    private static final String ACTION_ID3 = "com.bt.ACTION_AVRCP_MUSIC_ID3";
    private static final String EXTRA_BT_TITLE = "extra_avrcp_id3_title";
    private static final String EXTRA_BT_ARTIST = "extra_avrcp_id3_artist";

    private static final String ACTION_INCOMING = "com.bt.ACTION_BT_INCOMING_CALL";
    private static final String ACTION_OUTGOING = "com.bt.ACTION_BT_OUTGOING_CALL";
    private static final String ACTION_ONLINE = "com.bt.ACTION_BT_BEGIN_CALL_ONLINE";
    private static final String ACTION_END = "com.bt.ACTION_BT_END_CALL";
    private static final String ACTION_IN_NUMBER = "com.bt.ACTION_BT_INCOMING_NUMBER";
    private static final String ACTION_OUT_NUMBER = "com.bt.ACTION_BT_OUTGOING_NUMBER";
    private static final String ACTION_IN_NAME = "com.bt.ACTION_BT_INCOMING_NAME";
    private static final String ACTION_OUT_NAME = "com.bt.ACTION_BT_OUTGOING_NAME";
    private static final String EXTRA_NUMBER = "extra_phone_number";
    private static final String EXTRA_NAME = "extra_phone_name";

    private final Context context;
    private final BtClient bt;
    private final AppMediaClient appMedia;
    private AppMediaClient.Info appInfo;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean ourMusic;

    private int source = -1;
    private Kind lastKind = Kind.NONE;
    private boolean sawUsb;
    private boolean sawBt;
    private boolean usbOverrides;
    private boolean btPlaying;
    private int musicEpoch;
    private int callEpoch;

    private String title = "";
    private String artist = "";
    private String album = "";
    private boolean playing;
    private int positionMs;
    private int durationMs;
    private String mediaId;
    private String path;
    private String btTitle = "";
    private String btArtist = "";
    private boolean a2dpKnown;
    private boolean a2dpConnected;

    private CallPhase call = CallPhase.NONE;
    private String callName = "";
    private String callNumber = "";

    private boolean started;
    private boolean receiverRegistered;

    public HomeSession(Context context) {
        this.context = context.getApplicationContext();
        this.bt = new BtClient(context);
        this.appMedia = new AppMediaClient(this.context,
            new ComponentName(this.context, MediaNotificationListener.class));
        this.appMedia.setListener(info -> {
            appInfo = info;
            publish();
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) publish();
    }

    public void start() {
        if (started) return;
        started = true;
        register();
        bt.connect();
        appMedia.start();
        MusicRemote.query(context);
        readSource();
        publish();
    }

    /** Re-reads the source and re-requests the music state, without touching the audio. */
    public void refresh() {
        if (!started) start();
        readSource();
        try {
            context.sendBroadcast(new Intent(ACTION_REFRESH_WIDGET));
        } catch (Exception e) {
            Log.d(TAG, "music refresh: " + e.getMessage());
        }
        MusicRemote.query(context);
        appMedia.refresh();
        requestQuery();
    }

    public void stop() {
        if (!started) return;
        started = false;
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
        try {
            context.getContentResolver().unregisterContentObserver(sourceObserver);
        } catch (Exception ignored) {
        }
        bt.release();
        appMedia.stop();
    }

    /** The screen set the source early (power, tuning, play) before the head unit's broadcast. */
    public void assumeSource(int next) {
        if (next < 0 || next == source) {
            publish();
            return;
        }
        source = next;
        publish();
    }

    public void usbTransport(int button) {
        // App de música da suíte: ele mesmo avisa a central que virou a fonte.
        source = SOURCE_ANDROID;
        MusicRemote.send(context, button == USB_PREV ? MusicRemote.CMD_PREV
            : button == USB_NEXT ? MusicRemote.CMD_NEXT : MusicRemote.CMD_TOGGLE);
        noteUsb();
        publish();
    }

    /** Estado enviado pelo app de música (MusicRemote.ACTION_STATE). */
    private void onRemoteMusic(Intent i) {
        ourMusic = i.getBooleanExtra(MusicRemote.EXTRA_HAS_TRACK, false);
        if (!ourMusic) {
            publish();
            return;
        }
        title = str(i, MusicRemote.EXTRA_TITLE);
        artist = str(i, MusicRemote.EXTRA_ARTIST);
        album = str(i, MusicRemote.EXTRA_ALBUM);
        playing = i.getBooleanExtra(MusicRemote.EXTRA_PLAYING, false);
        positionMs = i.getIntExtra(MusicRemote.EXTRA_POSITION, 0);
        durationMs = i.getIntExtra(MusicRemote.EXTRA_DURATION, 0);
        long id = i.getLongExtra(MusicRemote.EXTRA_MEDIA_ID, 0);
        mediaId = id > 0 ? String.valueOf(id) : null;
        path = i.getStringExtra(MusicRemote.EXTRA_PATH);
        noteUsb();
        if (playing) {
            usbOverrides = true;
            btPlaying = false;
        }
        publish();
    }

    private static String str(Intent i, String key) {
        String v = i.getStringExtra(key);
        return v == null ? "" : v.trim();
    }

    public void btTransport(int cmd) {
        if (cmd != BT_PAUSE) PlayerHandoff.beforeBt(context);
        bringBtToFront();
        sawBt = true;
        usbOverrides = false;
        source = SOURCE_ANDROID;
        if (cmd == BT_PLAY) btPlaying = true;
        else if (cmd == BT_PAUSE) btPlaying = false;
        musicEpoch++;
        publish();
        bt.playControl(cmd);
    }

    public void answerCall() {
        bt.answer();
    }

    public void hangupCall() {
        bt.hangup();
    }

    private void readSource() {
        int next = readSetting();
        if (next < 0 || next == source) return;
        source = next;
        publish();
    }

    private int readSetting() {
        try {
            return Settings.System.getInt(context.getContentResolver(), NwdConstants.SETTING_CURRENT_SOURCE);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
    }

    private void requestAndroidSource() {
        Intent in = new Intent(NwdConstants.ACTION_REQUEST_CHANGE_SOURCE);
        in.putExtra(NwdConstants.EXTRA_SOURCE_ID, (byte) SOURCE_ANDROID);
        context.sendBroadcast(in);
    }

    /** App id 15: moves the radio and USB player out of the foreground without a generic source-0 play. */
    private void bringBtToFront() {
        Intent in = new Intent(NwdConstants.ACTION_APP_IN_OUT);
        in.putExtra(NwdConstants.EXTRA_APP_ID, NwdConstants.APP_ID_BT_MUSIC);
        in.putExtra(NwdConstants.EXTRA_APP_OPERATION, 1);
        in.putExtra(NwdConstants.EXTRA_APP_EVENT, 0);
        context.sendBroadcast(in);
    }

    /** AUX (7) and DVR (8) repeat the last card. Other sources without a card stay empty. */
    private Kind resolveKind() {
        if (source == SOURCE_AUX || source == SOURCE_DVR) return lastKind;
        Kind kind = Kind.NONE;
        if (source == SOURCE_RADIO) kind = Kind.RADIO;
        else if (source == SOURCE_ANDROID) kind = musicKind();
        if (kind != Kind.NONE) lastKind = kind;
        return kind;
    }

    private Kind musicKind() {
        boolean appOn = appInfo != null && LauncherConfig.mediaAppControls(context);
        if (appOn && appInfo.playing) return Kind.APP;   // um app de terceiro (Spotify…) está tocando
        if (btPlaying && !ourMusic) return Kind.BT;
        if (ourMusic || usbOverrides) return Kind.USB;
        if (btPlaying) return Kind.BT;
        if (appOn) return Kind.APP;                       // sessão de app presente (pausada), nada nosso tocando
        if (sawBt) return Kind.BT;
        if (sawUsb) return Kind.USB;
        return Kind.NONE;
    }

    /** Controla o app de mídia da fonte Android: {@code what} < 0 anterior, 0 tocar/pausar, > 0 próxima. */
    public void appTransport(int what) {
        if (what < 0) appMedia.prev();
        else if (what > 0) appMedia.next();
        else appMedia.playPause();
    }

    private void noteUsb() {
        sawUsb = true;
        if (!btPlaying) usbOverrides = true;
    }

    private void noteBtSession() {
        sawBt = true;
        usbOverrides = false;
    }

    private void requestQuery() {
        final int callAt = callEpoch;
        final int musicAt = musicEpoch;
        bt.query((callState, musicPlaying, a2dp) -> {
            if (callAt == callEpoch) applyCallState(callState);
            if (musicAt == musicEpoch) {
                if (musicPlaying) noteBtSession();
                btPlaying = musicPlaying;
            }
            a2dpKnown = true;
            a2dpConnected = a2dp;
            publish();
        });
    }

    private void applyCallState(int state) {
        CallPhase next;
        if (state == 1) next = CallPhase.INCOMING;
        else if (state == 2) next = CallPhase.OUTGOING;
        else if (state == 3) next = CallPhase.ACTIVE;
        else next = CallPhase.NONE;
        setCall(next, false);
    }

    private void setCall(CallPhase next, boolean fromBroadcast) {
        if (next == call) return;
        if (fromBroadcast) callEpoch++;
        if (next == CallPhase.NONE) {
            callName = "";
            callNumber = "";
        } else if (next == CallPhase.INCOMING || next == CallPhase.OUTGOING) {
            // New call: the previous name must not linger. The number arrives in the next broadcast.
            callName = "";
            callNumber = "";
        }
        call = next;
    }

    private final ContentObserver sourceObserver = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            readSource();
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            try {
                handle(intent);
            } catch (Throwable t) {
                Log.w(TAG, "broadcast", t);
            }
        }
    };

    private void handle(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (MusicRemote.ACTION_STATE.equals(action)) {
            onRemoteMusic(intent);
            return;
        }
        if (NwdConstants.ACTION_CHANGE_SOURCE.equals(action)) {
            int next = readSourceExtra(intent);
            if (next >= 0 && next != source) {
                source = next;
                publish();
            }
            return;
        }
        if (ACTION_META.equals(action) || ACTION_PLAYSTATE.equals(action)) {
            readTrack(intent);
            noteUsb();
            publish();
            return;
        }
        if (ACTION_PROGRESS.equals(action)) {
            positionMs = readMillis(intent, EXTRA_CUR);
            durationMs = readMillis(intent, EXTRA_TOTAL);
            publish();
            return;
        }
        if (ACTION_BT_PLAY.equals(action)) {
            musicEpoch++;
            noteBtSession();
            btPlaying = true;
            publish();
            return;
        }
        if (ACTION_BT_PAUSE.equals(action)) {
            musicEpoch++;
            btPlaying = false;
            publish();
            return;
        }
        if (ACTION_ID3.equals(action)) {
            String t = intent.getStringExtra(EXTRA_BT_TITLE);
            String a = intent.getStringExtra(EXTRA_BT_ARTIST);
            if (t != null) btTitle = t.trim();
            if (a != null) btArtist = a.trim();
            noteBtSession();
            publish();
            return;
        }
        if (ACTION_INCOMING.equals(action)) {
            setCall(CallPhase.INCOMING, true);
            publish();
            return;
        }
        if (ACTION_OUTGOING.equals(action)) {
            setCall(CallPhase.OUTGOING, true);
            publish();
            return;
        }
        if (ACTION_ONLINE.equals(action)) {
            setCall(CallPhase.ACTIVE, true);
            publish();
            return;
        }
        if (ACTION_END.equals(action)) {
            setCall(CallPhase.NONE, true);
            publish();
            return;
        }
        if (ACTION_IN_NUMBER.equals(action) || ACTION_OUT_NUMBER.equals(action)) {
            String n = intent.getStringExtra(EXTRA_NUMBER);
            if (n != null) callNumber = n.trim();
            publish();
            return;
        }
        if (ACTION_IN_NAME.equals(action) || ACTION_OUT_NAME.equals(action)) {
            String n = intent.getStringExtra(EXTRA_NAME);
            if (n != null) callName = n.trim();
            publish();
        }
    }

    private void readTrack(Intent intent) {
        String track = intent.getStringExtra("track");
        String who = intent.getStringExtra("artist");
        String disc = intent.getStringExtra("album");
        if (intent.hasExtra("playing")) playing = intent.getBooleanExtra("playing", false);
        if (track != null) title = track.trim();
        if (who != null) artist = who.trim();
        if (disc != null) album = disc.trim();
        if (!intent.hasExtra("id")) return;
        mediaId = null;
        path = null;
        try {
            Object extra = intent.getParcelableExtra("id");
            if (extra instanceof MusicBean) {
                MusicBean bean = (MusicBean) extra;
                if (title.isEmpty() && bean.TITLE != null) title = bean.TITLE.trim();
                if (artist.isEmpty() && bean.ARTIST != null) artist = bean.ARTIST.trim();
                if (album.isEmpty() && bean.ALBUM != null) album = bean.ALBUM.trim();
                mediaId = bean.ID;
            }
        } catch (Throwable ignored) {
            // The track parcel didn't match; title and artist already came in the text extras.
        }
    }

    private void register() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(NwdConstants.ACTION_CHANGE_SOURCE);
        filter.addAction(MusicRemote.ACTION_STATE);
        filter.addAction(ACTION_META);
        filter.addAction(ACTION_PLAYSTATE);
        filter.addAction(ACTION_PROGRESS);
        filter.addAction(ACTION_BT_PLAY);
        filter.addAction(ACTION_BT_PAUSE);
        filter.addAction(ACTION_ID3);
        filter.addAction(ACTION_INCOMING);
        filter.addAction(ACTION_OUTGOING);
        filter.addAction(ACTION_ONLINE);
        filter.addAction(ACTION_END);
        filter.addAction(ACTION_IN_NUMBER);
        filter.addAction(ACTION_OUT_NUMBER);
        filter.addAction(ACTION_IN_NAME);
        filter.addAction(ACTION_OUT_NAME);
        context.registerReceiver(receiver, filter);
        receiverRegistered = true;
        context.getContentResolver().registerContentObserver(
            Settings.System.getUriFor(NwdConstants.SETTING_CURRENT_SOURCE), false, sourceObserver);
    }

    private void publish() {
        Listener l = listener;
        if (l == null) return;
        boolean appOn = appInfo != null && LauncherConfig.mediaAppControls(context);
        l.onChanged(new Snapshot(
            source, resolveKind(), call, callName, callNumber,
            title, artist, album, playing, positionMs, durationMs, mediaId, path,
            btTitle, btArtist, btPlaying, a2dpKnown, a2dpConnected,
            appOn ? appInfo.pkg : null,
            appOn ? appInfo.appLabel : "",
            appOn ? appInfo.title : "",
            appOn ? appInfo.artist : "",
            appOn && appInfo.playing,
            appOn && appInfo.hasNext,
            appOn && appInfo.hasPrev,
            appOn ? appInfo.art : null));
    }

    private static int readSourceExtra(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(NwdConstants.EXTRA_SOURCE_ID)) return -1;
        Object v = extras.get(NwdConstants.EXTRA_SOURCE_ID);
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Byte) return ((Byte) v) & 0xFF;
        if (v instanceof Short) return ((Short) v) & 0xFFFF;
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static int readMillis(Intent intent, String key) {
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(key)) return 0;
        Object v = extras.get(key);
        if (v instanceof Integer) return Math.max(0, (Integer) v);
        if (v instanceof Long) return (int) Math.max(0, (Long) v);
        if (v instanceof String) {
            try {
                return Math.max(0, Integer.parseInt(((String) v).trim()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return Math.max(0, intent.getIntExtra(key, 0));
    }
}
