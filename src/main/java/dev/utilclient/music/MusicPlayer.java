package dev.utilclient.music;

import dev.utilclient.core.Config;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The AloneByDeath music player: plays MP3 and WAV files from {@code config/donutwaffen/music/} on a
 * background thread, with its own volume, shuffle and repeat. MP3 decoding by JLayer (LGPL).
 */
public final class MusicPlayer {
    private static final Logger LOG = Logger.getLogger("donutwaffen");
    private static final Random RNG = new Random();

    public enum Repeat { ALL, ONE, OFF }

    private static final List<Path> tracks = new ArrayList<>();
    private static final List<Integer> order = new ArrayList<>();
    private static int pos = -1;

    private static volatile float volume = 0.6f;
    private static volatile boolean shuffle;
    private static volatile Repeat repeat = Repeat.ALL;

    private static volatile boolean paused = true;
    private static volatile boolean skip;           // ask the decode loop to stop the current track
    private static volatile int skipTo = -1;        // queue position to jump to after skipping
    private static volatile long posMs, lenMs;
    private static volatile String nowPlaying = "";
    private static volatile String error = "";
    private static Thread thread;

    private MusicPlayer() {}

    public static Path folder() { return Config.dir().resolve("music"); }

    // ---- state for the HUD / menu --------------------------------------------------------------

    public static boolean playing() { return !paused && !nowPlaying.isEmpty(); }
    public static boolean paused() { return paused; }
    public static String nowPlaying() { return nowPlaying; }
    public static long positionMs() { return posMs; }
    public static long lengthMs() { return lenMs; }
    public static String error() { return error; }
    public static synchronized int trackCount() { return tracks.size(); }
    public static synchronized List<String> trackNames() {
        List<String> out = new ArrayList<>();
        for (Path p : tracks) out.add(name(p));
        return out;
    }

    public static void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }
    public static void setShuffle(boolean s) {
        if (s == shuffle) return;
        shuffle = s;
        synchronized (MusicPlayer.class) { rebuildOrder(true); }
    }
    public static void setRepeat(Repeat r) { repeat = r; }

    private static String name(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    // ---- library -------------------------------------------------------------------------------

    /** Re-reads the music folder. @return number of tracks */
    public static synchronized int rescan() {
        Path dir = folder();
        try {
            Files.createDirectories(dir);
            Path readme = dir.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        AloneByDeath music player (DonutWaffen)
                        Put .mp3 or .wav files in this folder, then use .music play or the main-menu player.
                        Subfolders work too. Files play in name order unless Shuffle is on.
                        """);
            }
        } catch (Exception e) {
            error = "Can't create " + dir;
        }
        Path current = pos >= 0 && pos < order.size() ? tracks.get(order.get(pos)) : null;
        tracks.clear();
        try (Stream<Path> s = Files.walk(dir, 3)) {
            s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".mp3") || n.endsWith(".wav");
            }).sorted().forEach(tracks::add);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Music scan failed", e);
        }
        rebuildOrder(false);
        if (current != null) {
            int idx = tracks.indexOf(current);
            pos = idx < 0 ? -1 : order.indexOf(idx);
        }
        return tracks.size();
    }

    private static void rebuildOrder(boolean keepCurrent) {
        Integer cur = keepCurrent && pos >= 0 && pos < order.size() ? order.get(pos) : null;
        order.clear();
        for (int i = 0; i < tracks.size(); i++) order.add(i);
        if (shuffle) Collections.shuffle(order, RNG);
        if (cur != null) {
            order.remove(cur);
            order.add(0, cur);
            pos = 0;
        }
    }

    // ---- controls ------------------------------------------------------------------------------

    public static void play() {
        if (trackCount() == 0 && rescan() == 0) {
            error = "No songs - put .mp3/.wav files in " + folder();
            return;
        }
        error = "";
        paused = false;
        ensureThread();
    }

    public static void pause() { paused = true; }

    public static void toggle() { if (playing()) pause(); else play(); }

    public static void next() { jump(+1); }

    public static void previous() {
        if (posMs > 3000) { jump(0); return; }   // like every player: first restart the song
        jump(-1);
    }

    /** Play the track whose name contains {@code query}. @return the match, or null */
    public static String playByName(String query) {
        if (trackCount() == 0) rescan();
        String q = query.toLowerCase(Locale.ROOT);
        synchronized (MusicPlayer.class) {
            for (int i = 0; i < tracks.size(); i++) {
                if (name(tracks.get(i)).toLowerCase(Locale.ROOT).contains(q)) {
                    skipTo = order.indexOf(i);
                    skip = true;
                    play();
                    return name(tracks.get(i));
                }
            }
        }
        return null;
    }

    private static synchronized void jump(int delta) {
        if (tracks.isEmpty() && rescan() == 0) { play(); return; }
        int n = order.size();
        skipTo = ((Math.max(pos, 0) + delta) % n + n) % n;
        skip = true;
        play();
    }

    private static synchronized void ensureThread() {
        if (thread != null && thread.isAlive()) return;
        thread = new Thread(MusicPlayer::loop, "DonutWaffen-Music");
        thread.setDaemon(true);
        thread.start();
    }

    // ---- playback ------------------------------------------------------------------------------

    private static void loop() {
        while (true) {
            Path track;
            synchronized (MusicPlayer.class) {
                if (tracks.isEmpty()) { nowPlaying = ""; paused = true; return; }
                if (skipTo >= 0) { pos = skipTo; skipTo = -1; }
                else if (pos < 0 || pos >= order.size()) pos = 0;
                track = tracks.get(order.get(pos));
            }
            skip = false;
            nowPlaying = name(track);
            posMs = 0;
            lenMs = 0;
            boolean finished;
            try {
                finished = track.toString().toLowerCase(Locale.ROOT).endsWith(".wav") ? playWav(track) : playMp3(track);
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Couldn't play " + track, t);
                error = "Couldn't play " + name(track);
                finished = true;
                try { Thread.sleep(300); } catch (InterruptedException e) { return; }
            }
            if (!finished) continue;   // skipped: skipTo already set
            synchronized (MusicPlayer.class) {
                if (repeat == Repeat.ONE) continue;
                if (pos + 1 >= order.size()) {
                    if (repeat == Repeat.OFF) { paused = true; nowPlaying = ""; pos = -1; return; }
                    if (shuffle) rebuildOrder(false);
                    pos = 0;
                } else {
                    pos++;
                }
            }
        }
    }

    /** Blocks while paused. @return false if the track should be abandoned */
    private static boolean waitWhilePaused(SourceDataLine line) throws InterruptedException {
        if (!paused) return !skip;
        line.stop();
        while (paused && !skip) Thread.sleep(50);
        line.start();
        return !skip;
    }

    private static SourceDataLine open(AudioFormat fmt) throws Exception {
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
        line.open(fmt, (int) (fmt.getSampleRate() * fmt.getFrameSize() / 5));   // ~200 ms buffer
        line.start();
        return line;
    }

    /** Volume curve that sounds even (perceived loudness is roughly logarithmic). */
    private static float gain() { float v = volume; return v * v; }

    private static boolean playMp3(Path file) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 64 * 1024)) {
            long size = Files.size(file);
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            SourceDataLine line = null;
            byte[] bytes = new byte[0];
            float msDone = 0;
            try {
                Header h;
                while ((h = bs.readFrame()) != null) {
                    if (line == null) lenMs = (long) h.total_ms((int) Math.min(Integer.MAX_VALUE, size));
                    SampleBuffer out = (SampleBuffer) dec.decodeFrame(h, bs);
                    if (line == null) {
                        line = open(new AudioFormat(dec.getOutputFrequency(), 16, dec.getOutputChannels(), true, false));
                    }
                    if (!waitWhilePaused(line)) return false;
                    short[] s = out.getBuffer();
                    int n = out.getBufferLength();
                    if (bytes.length < n * 2) bytes = new byte[n * 2];
                    float g = gain();
                    for (int i = 0; i < n; i++) {
                        int v = (int) (s[i] * g);
                        bytes[i * 2] = (byte) v;
                        bytes[i * 2 + 1] = (byte) (v >> 8);
                    }
                    line.write(bytes, 0, n * 2);
                    msDone += h.ms_per_frame();
                    posMs = (long) msDone;
                    bs.closeFrame();
                }
                if (line != null) line.drain();
                return true;
            } finally {
                if (line != null) line.close();
                bs.close();
            }
        }
    }

    private static boolean playWav(Path file) throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            AudioFormat src = raw.getFormat();
            AudioFormat pcm = new AudioFormat(src.getSampleRate(), 16, src.getChannels(), true, false);
            try (AudioInputStream in = AudioSystem.getAudioInputStream(pcm, raw)) {
                long frames = raw.getFrameLength();
                lenMs = frames > 0 ? (long) (frames * 1000 / src.getFrameRate()) : 0;
                SourceDataLine line = open(pcm);
                try {
                    byte[] buf = new byte[4096];
                    long bytesDone = 0;
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        if (!waitWhilePaused(line)) return false;
                        float g = gain();
                        for (int i = 0; i + 1 < r; i += 2) {
                            int v = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
                            v = (int) (v * g);
                            buf[i] = (byte) v;
                            buf[i + 1] = (byte) (v >> 8);
                        }
                        line.write(buf, 0, r);
                        bytesDone += r;
                        posMs = (long) (bytesDone / pcm.getFrameSize() * 1000 / pcm.getFrameRate());
                    }
                    line.drain();
                    return true;
                } finally {
                    line.close();
                }
            }
        }
    }
}
