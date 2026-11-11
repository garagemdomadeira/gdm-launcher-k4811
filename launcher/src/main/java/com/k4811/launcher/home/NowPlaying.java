package com.k4811.launcher.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.k4811.common.Apps;
import com.k4811.common.audio.PlayerHandoff;
import com.k4811.common.music.AlbumArt;
import com.k4811.common.radio.Band;
import com.k4811.common.radio.RadioClient;
import com.k4811.common.radio.RadioState;
import com.k4811.common.radio.StationArt;
import com.k4811.common.ui.DockNavigator;
import com.k4811.common.ui.DockView;
import com.k4811.common.ui.IconView;
import com.k4811.common.ui.Ui;
import com.k4811.launcher.R;
import com.k4811.launcher.media.ActivePlayer;
import com.k4811.launcher.media.SourcePicker;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Módulo "Tocando agora": a fonte com a setinha (Automático, Rádio, MP3, Bluetooth ou um app de música como Spotify,
 * Deezer, YouTube Music) e o que está tocando, com capa, faixa, álbum ou RDS, etiquetas, progresso, tempos e
 * anterior / tocar / próxima.
 *
 * <p>O que toca de verdade manda: o rádio quando ele é a fonte da central, senão a sessão de mídia que está tocando
 * (qualquer app), senão o MP3 ou a música BT da suíte. Parado, mostra a fonte escolhida (no Automático, a última que
 * tocou). Os apps de música recebem os comandos pela sessão de mídia; sem sessão (app fechado), a tecla de mídia vai
 * direto para o app e, se ele não começar, o app abre.
 */
public final class NowPlaying {

    public static final String SRC_AUTO = "AUTO";
    public static final String SRC_RADIO = "RADIO";
    public static final String SRC_MP3 = "MP3";
    public static final String SRC_BT = "BT";

    private static final String PREFS = "gdm_home";
    private static final String KEY_SOURCE = "np_source";
    private static final String KEY_LAST = "np_source_last";
    private static final int SOURCE_ANDROID = 0;
    private static final int SOURCE_RADIO = 4;
    private static final long POLL_MS = 1000;
    private static final long START_WAIT_MS = 2500;

    private enum Shown { RADIO, SESSION, USB, BT, IDLE }

    /** Quanto cabe no espaço do módulo (HomeModuleViews). */
    public interface Room {
        boolean line2();

        boolean progress();

        boolean extras();
    }

    private final Activity activity;
    private final HomeSession session;
    private final RadioClient radio;
    private final Room room;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService artLoader = Executors.newSingleThreadExecutor();

    private final ImageView sourceIcon;
    private final TextView label;
    private final TextView line1;
    private final TextView line2;
    private final TextView extra;
    private final TextView tags;
    private final View times;
    private final TextView timePos;
    private final TextView timeDur;
    private final View progressTrack;
    private final View progress;
    private final View controls;
    private final IconView play;
    private final ImageView art;
    private final IconView artIcon;

    private HomeSession.Snapshot snap;
    private RadioState radioState = new RadioState();
    private boolean radioReady;
    private Shown shown = Shown.IDLE;
    private MediaController controller;
    private String labelKey;
    private String artKey;
    private int artGen;
    private boolean running;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public NowPlaying(Activity activity, HomeSession session, RadioClient radio, Room room) {
        this.activity = activity;
        this.session = session;
        this.radio = radio;
        this.room = room;
        prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sourceIcon = (ImageView) activity.findViewById(R.id.np_source_icon);
        label = (TextView) activity.findViewById(R.id.np_label);
        line1 = (TextView) activity.findViewById(R.id.np_line1);
        line2 = (TextView) activity.findViewById(R.id.np_line2);
        extra = (TextView) activity.findViewById(R.id.np_extra);
        tags = (TextView) activity.findViewById(R.id.np_tags);
        times = activity.findViewById(R.id.np_times);
        timePos = (TextView) activity.findViewById(R.id.np_pos);
        timeDur = (TextView) activity.findViewById(R.id.np_dur);
        progressTrack = activity.findViewById(R.id.np_progress_track);
        progress = activity.findViewById(R.id.np_progress);
        controls = activity.findViewById(R.id.np_controls);
        play = (IconView) activity.findViewById(R.id.np_play);
        art = (ImageView) activity.findViewById(R.id.np_art);
        artIcon = (IconView) activity.findViewById(R.id.np_art_icon);
        extra.setTypeface(Ui.regular(activity));
        tags.setTypeface(Ui.medium(activity));
        timePos.setTypeface(Ui.regular(activity));
        timeDur.setTypeface(Ui.regular(activity));
        ((ImageView) activity.findViewById(R.id.np_source_arrow)).setColorFilter(Ui.TEXT_MUTED);
        activity.findViewById(R.id.np_source).setOnClickListener(v ->
            SourcePicker.show(activity, this, mode(), this::pick));
    }

    public void update(HomeSession.Snapshot s, RadioState state, boolean ready) {
        snap = s;
        radioState = state;
        radioReady = ready;
        render();
    }

    public void resume() {
        labelKey = null;
        if (running) return;
        running = true;
        handler.post(poll);
    }

    public void pause() {
        running = false;
        handler.removeCallbacks(poll);
    }

    public void release() {
        pause();
        handler.removeCallbacksAndMessages(null);
        artLoader.shutdownNow();
    }

    // ------------------------------------------------------------------ fontes

    /** Nome da fonte na pílula e na lista. */
    public String sourceLabel(String key) {
        if (SRC_RADIO.equals(key)) return activity.getString(R.string.home_radio);
        if (SRC_MP3.equals(key)) return "MP3";
        if (SRC_BT.equals(key)) return activity.getString(R.string.home_bt_music);
        if (SRC_AUTO.equals(key)) return activity.getString(R.string.home_source_auto);
        return appLabel(key).toString();
    }

    /** App de uma fonte (null para Automático e Rádio). */
    public String packageOf(String src) {
        if (src == null || SRC_RADIO.equals(src) || SRC_AUTO.equals(src)) return null;
        if (SRC_MP3.equals(src)) return Apps.MUSIC;
        if (SRC_BT.equals(src)) return Apps.BT_MUSIC;
        return src;
    }

    private void pick(String key) {
        prefs.edit().putString(KEY_SOURCE, key).apply();
        labelKey = null;
        if (SRC_AUTO.equals(key)) render();
        else select(key);
    }

    private String mode() {
        String v = prefs.getString(KEY_SOURCE, SRC_AUTO);
        return v.isEmpty() ? SRC_AUTO : v;
    }

    /** A fonte do player parado: a escolhida ou, no Automático, a última que tocou. */
    private String chosen() {
        String m = mode();
        if (!SRC_AUTO.equals(m)) return m;
        String last = prefs.getString(KEY_LAST, SRC_RADIO);
        return last.isEmpty() ? SRC_RADIO : last;
    }

    private void remember(String key) {
        if (key == null || !SRC_AUTO.equals(mode())) return;
        if (!key.equals(prefs.getString(KEY_LAST, ""))) prefs.edit().putString(KEY_LAST, key).apply();
    }

    /** Toque numa fonte: ela começa a tocar. */
    private void select(String src) {
        if (SRC_RADIO.equals(src)) {
            PlayerHandoff.switchToRadio(activity, radio::activateSource);
            session.assumeSource(SOURCE_RADIO);
        } else {
            startApp(src);
        }
        handler.postDelayed(this::render, 400);
    }

    private void leaveRadio() {
        if (snap != null && snap.source == SOURCE_RADIO) {
            radio.deactivateSource();
            session.assumeSource(SOURCE_ANDROID);
        }
    }

    private void startApp(String src) {
        String pkg = packageOf(src);
        if (pkg == null) return;
        leaveRadio();
        PlayerHandoff.beforeMp3(activity);
        MediaController other = ActivePlayer.playingController(activity);
        if (other != null && !pkg.equals(other.getPackageName())) other.getTransportControls().pause();
        if (SRC_MP3.equals(src)) {
            if (snap == null || snap.kind != HomeSession.Kind.USB || !snap.playing) {
                session.usbTransport(HomeSession.USB_TOGGLE);
            }
            return;
        }
        if (SRC_BT.equals(src)) {
            session.btTransport(HomeSession.BT_PLAY);
            return;
        }
        MediaController mc = controllerOf(pkg);
        if (mc != null) {
            mc.getTransportControls().play();
            return;
        }
        mediaButton(pkg, KeyEvent.KEYCODE_MEDIA_PLAY);
        handler.postDelayed(() -> {
            MediaController now = ActivePlayer.playingController(activity);
            if (now == null || !pkg.equals(now.getPackageName())) open(pkg);
        }, START_WAIT_MS);
    }

    private void mediaButton(String pkg, int code) {
        long now = SystemClock.uptimeMillis();
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, action, code, 0));
            try {
                activity.sendBroadcast(i);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ controles

    /** −1 anterior, 0 tocar/pausar (no rádio: liga/desliga), +1 próxima. */
    public void transport(int what) {
        switch (shown) {
            case RADIO:
                if (what == 0) {
                    if (snap != null && snap.source == SOURCE_RADIO) leaveRadio();
                    else select(SRC_RADIO);
                } else {
                    if (snap == null || snap.source != SOURCE_RADIO) select(SRC_RADIO);
                    radio.presetStep(what > 0);
                }
                break;
            case SESSION: {
                MediaController.TransportControls t = controller.getTransportControls();
                if (what < 0) t.skipToPrevious();
                else if (what > 0) t.skipToNext();
                else if (isPlaying(controller)) t.pause();
                else t.play();
                break;
            }
            case USB:
                session.usbTransport(what < 0 ? HomeSession.USB_PREV
                    : what > 0 ? HomeSession.USB_NEXT : HomeSession.USB_TOGGLE);
                break;
            case BT:
                session.btTransport(what < 0 ? HomeSession.BT_PREV : what > 0 ? HomeSession.BT_NEXT
                    : snap.btPlaying ? HomeSession.BT_PAUSE : HomeSession.BT_PLAY);
                break;
            default:
                if (what == 0) select(chosen());
                break;
        }
        handler.postDelayed(this::render, 400);
    }

    /** Toque no módulo: abre a tela do que está tocando. */
    public void openCurrent() {
        switch (shown) {
            case RADIO:
                DockNavigator.navigate(activity, null, DockView.Item.RADIO);
                break;
            case SESSION:
                open(controller.getPackageName());
                break;
            case USB:
                DockNavigator.navigate(activity, null, DockView.Item.MUSIC);
                break;
            case BT:
                DockNavigator.navigate(activity, null, DockView.Item.BT_MUSIC);
                break;
            default: {
                String src = chosen();
                if (SRC_RADIO.equals(src)) DockNavigator.navigate(activity, null, DockView.Item.RADIO);
                else if (SRC_MP3.equals(src)) DockNavigator.navigate(activity, null, DockView.Item.MUSIC);
                else if (SRC_BT.equals(src)) DockNavigator.navigate(activity, null, DockView.Item.BT_MUSIC);
                else open(packageOf(src));
                break;
            }
        }
    }

    // ------------------------------------------------------------------ tela

    private void render() {
        if (activity.isFinishing()) return;
        controller = null;
        MediaController playing = ActivePlayer.playingController(activity);
        String pick = chosen();
        if (snap != null && snap.source == SOURCE_RADIO) {
            shown = Shown.RADIO;
        } else if (playing != null && !Apps.MUSIC.equals(playing.getPackageName())) {
            shown = Shown.SESSION;
            controller = playing;
        } else if (snap != null && snap.kind == HomeSession.Kind.USB && snap.playing) {
            shown = Shown.USB;
        } else if (snap != null && snap.kind == HomeSession.Kind.BT && snap.btPlaying) {
            shown = Shown.BT;
        } else if (SRC_RADIO.equals(pick)) {
            shown = Shown.RADIO;
        } else if (SRC_MP3.equals(pick) && snap != null && snap.kind == HomeSession.Kind.USB) {
            shown = Shown.USB;
        } else if (SRC_BT.equals(pick) && snap != null && snap.kind == HomeSession.Kind.BT) {
            shown = Shown.BT;
        } else {
            String pkg = SRC_MP3.equals(pick) || SRC_BT.equals(pick) ? null : packageOf(pick);
            controller = pkg == null ? null : controllerOf(pkg);
            shown = controller != null ? Shown.SESSION : Shown.IDLE;
        }
        String on = highlighted(pick);
        if (shown != Shown.IDLE) remember(on);
        renderSource(on);
        controls.setVisibility(View.VISIBLE);
        switch (shown) {
            case RADIO:
                renderRadio();
                break;
            case SESSION:
                renderSession(controller);
                break;
            case USB:
                renderUsb();
                break;
            case BT:
                renderBt();
                break;
            default:
                renderIdle(pick);
                break;
        }
    }

    private String highlighted(String pick) {
        if (shown == Shown.RADIO) return SRC_RADIO;
        if (shown == Shown.USB) return SRC_MP3;
        if (shown == Shown.BT) return SRC_BT;
        if (shown == Shown.SESSION && controller != null) return controller.getPackageName();
        return pick;
    }

    /** Pílula: ícone e nome da fonte; no Automático, "AUTOMÁTICO · RÁDIO". */
    private void renderSource(String on) {
        boolean auto = SRC_AUTO.equals(mode());
        String key = (auto ? "A:" : "F:") + on;
        if (key.equals(labelKey)) return;
        labelKey = key;
        String name = sourceLabel(on);
        String text = auto ? activity.getString(R.string.home_source_auto) + "  ·  " + name : name;
        label.setText(text.toUpperCase(Locale.getDefault()));
        sourceIcon.setImageDrawable(SourcePicker.icon(activity, on, packageOf(on)));
    }

    private void renderRadio() {
        boolean live = snap != null && snap.source == SOURCE_RADIO;
        String sub = "";
        String rt = "";
        StringBuilder badges = new StringBuilder();
        if (!radioReady) {
            line1.setText(radioState.connection == RadioState.Connection.NOT_FOUND
                ? R.string.home_radio_unavailable : R.string.home_radio);
        } else {
            boolean fm = Band.isFm(radioState.band);
            String freq = Band.formatValue(radioState.frequency, fm) + " " + Band.unit(fm);
            boolean named = radioState.psName != null && !radioState.psName.isEmpty();
            line1.setText(named ? radioState.psName : freq);
            sub = Band.label(radioState.band) + (named ? "  ·  " + freq : "");
            if (radioState.radioText != null) rt = radioState.radioText.trim();
            if (radioState.presetIndex > 0) badge(badges, activity.getString(R.string.home_np_preset, radioState.presetIndex));
            if (fm && radioState.stereoSignal) badge(badges, activity.getString(R.string.radio_stereo));
            if (radioState.tp) badge(badges, "TP");
            if (radioState.taActive) badge(badges, "TA");
        }
        setLine2(sub);
        setExtra(rt);
        setTags(badges.toString());
        setProgress(-1);
        setTimes(-1, -1);
        play.set(IconView.Icon.POWER, IconView.Style.NONE);
        play.color(live ? Ui.TEXT : Ui.TEXT_MUTED);
        play.setContentDescription(activity.getString(live ? R.string.home_radio_turn_off : R.string.home_radio_turn_on));
        boolean station = radioReady && radioState.frequency > 0;
        int band = radioState.band;
        int freq = radioState.frequency;
        loadArt(station ? "radio:" + StationArt.baseName(band, freq) : null, IconView.Icon.RADIO,
            size -> StationArt.load(band, freq, size));
    }

    private void renderSession(MediaController mc) {
        String pkg = mc.getPackageName();
        MediaMetadata md = mc.getMetadata();
        String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        line1.setText(t == null || t.isEmpty() ? appLabel(pkg) : t);
        setLine2(a == null || a.isEmpty() ? (md == null ? "" : nz(md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))) : a);
        setExtra(md == null ? "" : nz(md.getString(MediaMetadata.METADATA_KEY_ALBUM)));
        StringBuilder badges = new StringBuilder();
        if (md != null) {
            long n = md.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
            long of = md.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS);
            if (n > 0) badge(badges, of > 0 ? n + "/" + of : "#" + n);
            long year = md.getLong(MediaMetadata.METADATA_KEY_YEAR);
            if (year > 0) badge(badges, String.valueOf(year));
            String genre = md.getString(MediaMetadata.METADATA_KEY_GENRE);
            if (genre != null && !genre.isEmpty()) badge(badges, genre);
        }
        setTags(badges.toString());
        PlaybackState st = mc.getPlaybackState();
        long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        float p = -1f;
        long pos = -1;
        if (st != null && st.getPosition() >= 0) {
            pos = st.getPosition();
            if (st.getState() == PlaybackState.STATE_PLAYING && st.getLastPositionUpdateTime() > 0) {
                pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
            }
            if (dur > 0) {
                pos = Math.min(pos, dur);
                p = Math.max(0f, Math.min(1f, pos / (float) dur));
            }
        }
        setProgress(p);
        setTimes(dur > 0 ? pos : -1, dur);
        setPlay(isPlaying(mc));
        Bitmap bmp = null;
        if (md != null) {
            bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        showBitmap(bmp == null ? null : "session:" + pkg + ":" + System.identityHashCode(bmp), bmp, IconView.Icon.MUSIC);
    }

    private void renderUsb() {
        line1.setText(snap.title.isEmpty() ? activity.getString(R.string.home_music) : snap.title);
        setLine2(snap.artist.isEmpty() ? "USB / SD" : snap.artist);
        setExtra(snap.album == null ? "" : snap.album);
        setTags(snap.path == null ? "" : fileTag(snap.path));
        setProgress(snap.durationMs > 0 ? Math.min(1f, snap.positionMs / (float) snap.durationMs) : 0f);
        setTimes(snap.durationMs > 0 ? snap.positionMs : -1, snap.durationMs);
        setPlay(snap.playing);
        long id = 0;
        if (snap.mediaId != null) {
            try {
                id = Long.parseLong(snap.mediaId.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        long mediaId = id;
        String path = snap.path;
        Context app = activity.getApplicationContext();
        loadArt(id > 0 ? "id:" + id : path != null ? "path:" + path : null, IconView.Icon.MUSIC,
            size -> AlbumArt.decode(app, mediaId, path));
    }

    private void renderBt() {
        boolean named = !snap.btTitle.isEmpty();
        if (snap.a2dpKnown && !snap.a2dpConnected && !named) {
            line1.setText(R.string.home_phone_disconnected);
            setLine2("");
        } else {
            line1.setText(named ? snap.btTitle : activity.getString(R.string.home_bt_music));
            setLine2(snap.btArtist);
        }
        setExtra("");
        setTags("");
        setProgress(-1);
        setTimes(-1, -1);
        setPlay(snap.btPlaying);
        showBitmap(null, null, IconView.Icon.BLUETOOTH);
    }

    private void renderIdle(String pick) {
        line1.setText(R.string.home_nothing_playing);
        setLine2(activity.getString(R.string.home_np_tap_play));
        setExtra("");
        setTags("");
        setProgress(0f);
        setTimes(-1, -1);
        setPlay(false);
        showBitmap(null, null, SRC_RADIO.equals(pick) ? IconView.Icon.RADIO
            : SRC_BT.equals(pick) ? IconView.Icon.BLUETOOTH : IconView.Icon.MUSIC);
    }

    private void setLine2(String text) {
        line2.setText(text);
        line2.setVisibility(text == null || text.isEmpty() || !room.line2() ? View.GONE : View.VISIBLE);
    }

    private void setExtra(String text) {
        extra.setText(text);
        extra.setVisibility(text == null || text.isEmpty() || !room.extras() ? View.GONE : View.VISIBLE);
    }

    private void setTags(String text) {
        tags.setText(text);
        tags.setVisibility(text == null || text.isEmpty() || !room.extras() ? View.GONE : View.VISIBLE);
    }

    private void setTimes(long pos, long dur) {
        if (dur <= 0 || pos < 0 || !room.extras()) {
            times.setVisibility(View.GONE);
            return;
        }
        timePos.setText(clock(pos));
        timeDur.setText(clock(dur));
        times.setVisibility(View.VISIBLE);
    }

    private void setProgress(float p) {
        progressTrack.setVisibility(p < 0 || !room.progress() ? View.GONE : View.VISIBLE);
        if (p >= 0) {
            progress.setPivotX(0f);
            progress.setScaleX(p);
        }
    }

    private void setPlay(boolean playing) {
        play.set(playing ? IconView.Icon.PAUSE : IconView.Icon.PLAY, IconView.Style.NONE);
        play.color(Ui.TEXT);
        play.setContentDescription(activity.getString(playing ? R.string.home_pause : R.string.home_play));
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private static void badge(StringBuilder b, String s) {
        if (b.length() > 0) b.append("  ·  ");
        b.append(s.toUpperCase(Locale.getDefault()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String fileTag(String path) {
        int dot = path.lastIndexOf('.');
        return dot > 0 && path.length() - dot <= 5 ? path.substring(dot + 1).toUpperCase(Locale.US) : "";
    }

    // ------------------------------------------------------------------ capa

    private interface ArtSource {
        Bitmap load(int sizePx);
    }

    private void loadArt(String key, IconView.Icon icon, ArtSource source) {
        artIcon.set(icon, IconView.Style.NONE);
        artIcon.color(Ui.TEXT_DIM);
        if (key == null) {
            clearArt();
            return;
        }
        if (key.equals(artKey)) return;
        clearArt();
        artKey = key;
        int gen = ++artGen;
        int size = art.getWidth() > 0 ? art.getWidth() : Math.round(Ui.dp(activity, 188));
        try {
            artLoader.execute(() -> {
                Bitmap bmp = source.load(size);
                handler.post(() -> {
                    if (activity.isFinishing() || gen != artGen || bmp == null) return;
                    showArt(bmp);
                });
            });
        } catch (Exception ignored) {
            // executor já desligado (tela saindo)
        }
    }

    private void showBitmap(String key, Bitmap bmp, IconView.Icon icon) {
        artIcon.set(icon, IconView.Style.NONE);
        artIcon.color(Ui.TEXT_DIM);
        if (bmp == null) {
            clearArt();
            return;
        }
        if (key.equals(artKey)) return;
        artKey = key;
        artGen++;
        showArt(bmp);
    }

    private void showArt(Bitmap bmp) {
        art.setImageBitmap(bmp);
        art.setVisibility(View.VISIBLE);
        artIcon.setVisibility(View.GONE);
    }

    private void clearArt() {
        artKey = null;
        artGen++;
        art.setImageDrawable(null);
        art.setVisibility(View.GONE);
        artIcon.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ apoio

    private MediaController controllerOf(String pkg) {
        List<MediaController> list = ActivePlayer.sessions(activity);
        if (list == null) return null;
        for (MediaController mc : list) {
            if (pkg.equals(mc.getPackageName())) return mc;
        }
        return null;
    }

    private static boolean isPlaying(MediaController mc) {
        PlaybackState st = mc.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    public CharSequence appLabel(String pkg) {
        PackageManager pm = activity.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (Exception e) {
            return pkg;
        }
    }

    private void open(String pkg) {
        Intent i = pkg == null ? null : activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(activity, activity.getString(R.string.screen_app_missing, String.valueOf(pkg)),
                Toast.LENGTH_LONG).show();
            return;
        }
        activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
