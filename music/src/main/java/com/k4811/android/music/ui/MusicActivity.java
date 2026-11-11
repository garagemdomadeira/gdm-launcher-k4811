package com.k4811.android.music.ui;

import com.k4811.android.music.ui.music.MusicConfig;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;



import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.k4811.common.config.LauncherConfig;
import com.k4811.common.music.AlbumArt;
import com.k4811.android.music.ui.music.MusicClient;
import com.k4811.android.music.ui.music.MusicLibrary;
import com.k4811.android.music.ui.music.MusicState;
import com.k4811.android.music.ui.music.MusicTrack;
import com.k4811.common.ui.ButtonIcon;
import com.k4811.android.music.ui.ui.FolderPicker;
import com.k4811.common.ui.IconView;
import com.k4811.common.ui.DockView;
import com.k4811.common.ui.DockNavigator;
import com.k4811.common.ui.Touch;
import com.k4811.common.ui.Ui;



import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Our USB/SD music screen: current track, transport controls and list. */
public class MusicActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService artLoader = Executors.newSingleThreadExecutor();
    private final List<MusicTrack> tracks = new ArrayList<>();
    /** Every folder heading and track, indented by depth (see buildRows). */
    private final List<Row> allRows = new ArrayList<>();
    /** What the list shows: allRows without what is inside collapsed folders. */
    private final List<Row> rows = new ArrayList<>();
    /** Folders collapsed by a tap on their heading, by path below the chosen folder ("Rock/80s"). */
    private final Set<String> collapsed = new HashSet<>();
    /** List position of each track; -1 while it is inside a collapsed folder. */
    private int[] rowOfTrack = new int[0];

    private MusicClient music;
    private MusicState state = new MusicState();
    private TrackAdapter adapter;
    private boolean seeking;
    /** Track and play state the list last scrolled to; it only follows on a change of either. */
    private int scrolledIndex = -1;
    private boolean scrolledPlaying;
    private boolean alive = true;
    private int artGen;
    private String artKey;

    private TextView trackView;
    private TextView artistView;
    private TextView albumView;
    private TextView posView;
    private TextView durView;
    private ImageView vinyl;
    private android.animation.ObjectAnimator vinylSpin;
    private TextView statusView;
    private TextView listTitle;
    private TextView shuffleBtn;
    private TextView folderBtn;
    private Dialog dialog;
    private TextView repeatBtn;
    private SeekBar seek;
    private IconView playBtn;
    private ImageView artView;
    private IconView artIcon;
    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.applyDesignDensity(this);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_music);
        Ui.applyFont(findViewById(R.id.music_root));
        DockNavigator.attachDock(this, DockView.Item.MUSIC);
        bindViews();

        music = new MusicClient(this);
        music.setListener(this::render);
        music.connect();
        Touch.install(findViewById(android.R.id.content));
    }

    private void bindViews() {
        ((TextView) findViewById(R.id.music_title_bar)).setTypeface(Ui.semiBold(this));
        IconView back = (IconView) findViewById(R.id.music_back);
        back.set(IconView.Icon.CHEVRON_LEFT, IconView.Style.NONE);
        back.setOnClickListener(v -> finish());

        shuffleBtn = (TextView) findViewById(R.id.music_shuffle);
        repeatBtn = (TextView) findViewById(R.id.music_repeat);
        TextView reload = (TextView) findViewById(R.id.music_reload);
        folderBtn = (TextView) findViewById(R.id.music_folder);
        shuffleBtn.setOnClickListener(v -> music.setShuffle(!state.shuffle));
        repeatBtn.setOnClickListener(v -> music.cycleRepeat());
        reload.setOnClickListener(v -> music.reload());
        folderBtn.setOnClickListener(v -> pickFolder());
        ButtonIcon.attach(shuffleBtn, ButtonIcon.Icon.SHUFFLE);
        ButtonIcon.attach(repeatBtn, ButtonIcon.Icon.REPEAT);
        ButtonIcon.attach(folderBtn, ButtonIcon.Icon.FOLDER);
        ButtonIcon.attach(reload, ButtonIcon.Icon.REFRESH);

        trackView = (TextView) findViewById(R.id.music_track);
        artistView = (TextView) findViewById(R.id.music_artist);
        albumView = (TextView) findViewById(R.id.music_album);
        posView = (TextView) findViewById(R.id.music_pos);
        durView = (TextView) findViewById(R.id.music_dur);
        statusView = (TextView) findViewById(R.id.music_status);
        listTitle = (TextView) findViewById(R.id.music_list_title);
        trackView.setTypeface(Ui.semiBold(this));
        seek = (SeekBar) findViewById(R.id.music_seek);
        artView = (ImageView) findViewById(R.id.music_art);
        artIcon = (IconView) findViewById(R.id.music_art_icon);
        artIcon.set(IconView.Icon.MUSIC, IconView.Style.NONE);
        artIcon.setClickable(false);
        artIcon.color(Ui.OUTLINE_STRONG);

        playBtn = (IconView) findViewById(R.id.music_play);
        playBtn.set(IconView.Icon.PLAY, IconView.Style.OUTLINE_CIRCLE);
        vinyl = (ImageView) findViewById(R.id.music_vinyl);
        vinylSpin = android.animation.ObjectAnimator.ofFloat(vinyl, "rotation", 0f, 360f);
        vinylSpin.setDuration(9000);
        vinylSpin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        vinylSpin.setInterpolator(new android.view.animation.LinearInterpolator());
        ((IconView) findViewById(R.id.music_prev))
            .set(IconView.Icon.SKIP_PREV, IconView.Style.NONE)
            .setOnClickListener(v -> music.previous());
        playBtn.setOnClickListener(v -> music.toggle());
        ((IconView) findViewById(R.id.music_next))
            .set(IconView.Icon.SKIP_NEXT, IconView.Style.NONE)
            .setOnClickListener(v -> music.next());

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || state.durationMs <= 0) return;
                posView.setText(clock((int) (progress / 1000f * state.durationMs)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seeking = false;
                if (state.durationMs > 0) {
                    music.seek((int) (seekBar.getProgress() / 1000f * state.durationMs));
                }
            }
        });

        list = (ListView) findViewById(R.id.music_list);
        adapter = new TrackAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            if (row.track >= 0) {
                music.playIndex(row.track);
            } else {
                if (!collapsed.remove(row.key)) collapsed.add(row.key);
                showRows();
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.hideSystemBars(getWindow());
        if (music != null) music.claimSource();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(getWindow());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Ui.applyDesignDensity(this);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        artGen++;
        if (vinylSpin != null) vinylSpin.cancel();
        artLoader.shutdownNow();
        if (dialog != null) dialog.dismiss();
        if (music != null) {
            music.setListener(null);
            music.release();
        }
        super.onDestroy();
    }

    /** Gira o disco de vinil enquanto toca; pausa (mantendo o ângulo) quando pausa. */
    private void spinVinyl(boolean playing) {
        if (vinylSpin == null) return;
        if (playing) {
            if (!vinylSpin.isStarted()) vinylSpin.start();
            else if (vinylSpin.isPaused()) vinylSpin.resume();
        } else if (vinylSpin.isStarted()) {
            vinylSpin.pause();
        }
    }

    private void render(MusicState s) {
        state = s;
        MusicTrack cur = s.current();
        if (cur == null) {
            trackView.setText(s.loading ? R.string.media_music_searching : R.string.media_music_no_track);
            artistView.setText("");
            albumView.setText("");
            clearArt();
        } else {
            trackView.setText(cur.displayTitle());
            artistView.setText(cur.artist);
            albumView.setText(cur.album);
            bindArt(cur);
        }

        if (!seeking) {
            if (s.durationMs > 0) {
                seek.setProgress(Math.min(1000, (int) (s.positionMs * 1000L / s.durationMs)));
            } else {
                seek.setProgress(0);
            }
            posView.setText(clock(s.positionMs));
            durView.setText(clock(s.durationMs));
        }

        playBtn.set(s.playing ? IconView.Icon.PAUSE : IconView.Icon.PLAY, IconView.Style.OUTLINE_CIRCLE);
        spinVinyl(s.playing);
        shuffleBtn.setActivated(s.shuffle);
        repeatBtn.setActivated(s.repeat != MusicState.Repeat.OFF);
        if (s.repeat == MusicState.Repeat.ONE) repeatBtn.setText(R.string.media_music_repeat_one);
        else if (s.repeat == MusicState.Repeat.ALL) repeatBtn.setText(R.string.media_music_repeat_all);
        else repeatBtn.setText(R.string.media_music_repeat);

        if (s.loading) statusView.setText(R.string.media_music_reading);
        else if (s.error != 0) statusView.setText(s.error);
        else if (s.tracks.isEmpty()) statusView.setText(R.string.media_music_empty_hint);
        else statusView.setText(getResources().getQuantityString(R.plurals.media_music_track_count, s.tracks.size(), s.tracks.size()));

        listTitle.setText(getResources().getQuantityString(R.plurals.media_music_list_title,
            s.tracks.size(), s.tracks.size()));
        boolean sameList = tracks.size() == s.tracks.size()
            && (tracks.isEmpty() || tracks.get(0) == s.tracks.get(0));
        if (!sameList) {
            tracks.clear();
            tracks.addAll(s.tracks);
            buildRows();
            scrolledIndex = -1;
        }
        // When the track changes, only its folder stays open; the driver can open the others.
        boolean changed = s.index != scrolledIndex;
        if (changed && s.index >= 0 && s.index < rowOfTrack.length) openOnly(s.index);
        adapter.notifyDataSetChanged();
        // Brings the current track into view only when it changes or starts playing, so the
        // driver can browse the list while it plays.
        boolean follow = changed || (s.playing && !scrolledPlaying);
        scrolledIndex = s.index;
        scrolledPlaying = s.playing;
        if (follow && s.index >= 0 && s.index < rowOfTrack.length && rowOfTrack[s.index] >= 0) {
            int row = rowOfTrack[s.index];
            int first = list.getFirstVisiblePosition();
            int last = list.getLastVisiblePosition();
            if (row < first || row > last) {
                list.setSelection(Math.max(0, row - 2));
            }
        }
    }

    /** The folder browser; the list then has only the music inside the chosen folder. */
    private void pickFolder() {
        if (dialog != null) dialog.dismiss();
        String folder = MusicConfig.folder(this);
        dialog = FolderPicker.show(this, new File(folder), f -> {
            MusicConfig.setFolder(this, f.getAbsolutePath());
            music.reload();
            render(state);
        });
    }

    private void bindArt(MusicTrack track) {
        String key = track.mediaId > 0 ? "id:" + track.mediaId : "path:" + track.path;
        if (key.equals(artKey)) return;
        artKey = key;
        int gen = ++artGen;
        clearArt();
        artLoader.execute(() -> {
            Bitmap bmp = AlbumArt.decode(this, track.mediaId, track.path);
            handler.post(() -> {
                if (!alive || gen != artGen || bmp == null) return;
                artView.setImageBitmap(bmp);
                artView.setVisibility(View.VISIBLE);
                artIcon.setVisibility(View.GONE);
            });
        });
    }

    private void clearArt() {
        artView.setImageDrawable(null);
        artView.setVisibility(View.GONE);
        artIcon.setVisibility(View.VISIBLE);
        artKey = null;
    }

    private static String clock(int ms) {
        int s = Math.max(0, ms) / 1000;
        if (s >= 3600) {
            return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        }
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    /**
     * A list line: a folder heading ({@code track} −1) or a track, at some depth. {@code key} is the
     * path below the chosen folder: the heading's own, or the track's folder ("" right in it).
     */
    private static final class Row {
        final int track;
        final String folder;
        final String key;
        final int depth;

        Row(int track, String folder, String key, int depth) {
            this.track = track;
            this.folder = folder;
            this.key = key;
            this.depth = depth;
        }
    }

    /**
     * Tracks come sorted by folder (MusicLibrary): a heading for each folder the first time it
     * shows up (parents too, even without tracks of their own), and each track one level inside
     * its folder. Tracks right in the chosen folder go first, without a heading.
     */
    private void buildRows() {
        allRows.clear();
        File root = new File(MusicConfig.folder(this));
        String[] previous = new String[0];
        for (int i = 0; i < tracks.size(); i++) {
            String[] path = root == null ? new String[0] : MusicLibrary.folders(tracks.get(i), root);
            int same = 0;
            while (same < path.length && same < previous.length && path[same].equals(previous[same])) same++;
            for (int level = same; level < path.length; level++) {
                String key = android.text.TextUtils.join("/", java.util.Arrays.copyOf(path, level + 1));
                allRows.add(new Row(-1, path[level], key, level));
            }
            allRows.add(new Row(i, null, android.text.TextUtils.join("/", path), path.length));
            previous = path;
        }
        showRows();
    }

    /** Collapses every folder except the ones the track is in (its folder and the ones above). */
    private void openOnly(int track) {
        String folder = null;
        for (Row row : allRows) {
            if (row.track == track) folder = row.key;
        }
        if (folder == null) return;
        collapsed.clear();
        for (Row row : allRows) {
            if (row.track < 0 && !folder.equals(row.key) && !folder.startsWith(row.key + "/")) {
                collapsed.add(row.key);
            }
        }
        showRows();
    }

    /**
     * Fills {@code rows} from {@code allRows}, leaving out whatever is below a collapsed heading:
     * everything after it that is deeper, since tracks come grouped by folder.
     */
    private void showRows() {
        rows.clear();
        rowOfTrack = new int[tracks.size()];
        java.util.Arrays.fill(rowOfTrack, -1);
        int hideBelow = -1;
        for (Row row : allRows) {
            if (hideBelow >= 0 && row.depth > hideBelow) continue;
            hideBelow = -1;
            if (row.track >= 0) rowOfTrack[row.track] = rows.size();
            else if (collapsed.contains(row.key)) hideBelow = row.depth;
            rows.add(row);
        }
    }

    private final class TrackAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Row getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).track < 0 ? 1 : 0;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row item = rows.get(position);
            if (item.track < 0) return folderView(item, convertView);
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(MusicActivity.this).inflate(R.layout.music_row, parent, false);
                Touch.hapticOnly(row);
                Ui.applyFont(row);
            }
            row.setPadding(indent(item.depth), row.getPaddingTop(), row.getPaddingRight(), row.getPaddingBottom());
            MusicTrack t = tracks.get(item.track);
            TextView title = (TextView) row.findViewById(R.id.row_title);
            TextView sub = (TextView) row.findViewById(R.id.row_sub);
            title.setText(t.displayTitle());
            String who = t.artist;
            if (!t.album.isEmpty()) {
                who = who.isEmpty() ? t.album : who + " · " + t.album;
            }
            sub.setText(who);
            boolean current = item.track == state.index;
            title.setTextColor(current ? Ui.TEXT : Ui.TEXT_SOFT);
            row.setActivated(current);
            return row;
        }

        /** Folder heading: its name, indented by depth. A tap collapses or expands it. */
        private View folderView(Row item, View convertView) {
            TextView v = (TextView) convertView;
            if (v == null) {
                v = new TextView(MusicActivity.this);
                v.setLayoutParams(new ListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(Ui.dp(MusicActivity.this, 52))));
                v.setGravity(android.view.Gravity.CENTER_VERTICAL);
                v.setTextSize(18);
                v.setTextColor(Ui.TEXT_MUTED);
                v.setTypeface(Ui.semiBold(MusicActivity.this));
                v.setSingleLine(true);
                v.setEllipsize(android.text.TextUtils.TruncateAt.END);
                Touch.hapticOnly(v);
            }
            v.setPadding(indent(item.depth), 0, Math.round(Ui.dp(MusicActivity.this, 16)), 0);
            v.setText((collapsed.contains(item.key) ? "▸  " : "▾  ") + item.folder);
            return v;
        }

        /** 18 dp plus 28 dp per folder level. */
        private int indent(int depth) {
            return Math.round(Ui.dp(MusicActivity.this, 18 + 28 * depth));
        }
    }
}
