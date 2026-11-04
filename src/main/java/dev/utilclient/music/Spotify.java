package dev.utilclient.music;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reads what the Spotify desktop app is playing and sends play/pause/next/previous to it.
 * <ul>
 *   <li>Windows: Spotify puts "Artist - Song" in its window title while playing, read with {@code tasklist /v};
 *       controls are the system media keys (the same as the keys on a keyboard).</li>
 *   <li>macOS: AppleScript. Linux: playerctl (MPRIS).</li>
 * </ul>
 * Runs on its own background thread and never blocks the game. No login or API key needed.
 */
public final class Spotify {
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WIN = OS.contains("win"), MAC = OS.contains("mac");

    private static volatile String artist = "", title = "";
    private static volatile boolean running, playing;
    private static volatile long trackSince;
    private static volatile boolean wanted;
    private static Thread thread;

    private Spotify() {}

    public static String artist() { return artist; }
    public static String title() { return title; }
    /** True if the Spotify app is open at all. */
    public static boolean running() { return running; }
    public static boolean playing() { return playing; }
    /** When the current song started showing (ms), for a rough elapsed timer. */
    public static long trackSince() { return trackSince; }

    /** Starts polling (idempotent). Called when the HUD element is on. */
    public static synchronized void ensureStarted() {
        wanted = true;
        if (thread != null && thread.isAlive()) return;
        thread = new Thread(Spotify::loop, "DonutWaffen-Spotify");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() { wanted = false; }

    private static void loop() {
        while (wanted) {
            try {
                poll();
            } catch (Throwable t) {
                running = false;
                playing = false;
            }
            try { Thread.sleep(1500); } catch (InterruptedException e) { return; }
        }
    }

    private static void poll() throws Exception {
        String song = null;
        boolean open;
        if (WIN) {
            String out = run("tasklist", "/v", "/fo", "csv", "/nh", "/fi", "imagename eq Spotify.exe");
            open = out.toLowerCase(Locale.ROOT).contains("spotify.exe");
            for (String line : out.split("\\R")) {
                if (!line.toLowerCase(Locale.ROOT).startsWith("\"spotify.exe\"")) continue;
                String t = lastCsv(line);
                // paused or idle windows are just titled "Spotify", "Spotify Free", "Spotify Premium"
                if (t.contains(" - ") && !t.startsWith("Spotify")) { song = t; break; }
            }
        } else if (MAC) {
            String out = run("osascript", "-e",
                    "if application \"Spotify\" is running then tell application \"Spotify\" to return (player state as string) & \"|\" & artist of current track & \" - \" & name of current track");
            open = !out.isBlank();
            if (out.startsWith("playing|")) song = out.substring(8).strip();
        } else {
            String status = run("playerctl", "-p", "spotify", "status").strip();
            open = !status.isEmpty() && !status.toLowerCase(Locale.ROOT).contains("no players");
            if (status.equalsIgnoreCase("Playing")) song = run("playerctl", "-p", "spotify", "metadata", "--format", "{{artist}} - {{title}}").strip();
        }
        running = open;
        playing = song != null && !song.isEmpty();
        if (playing) {
            int i = song.indexOf(" - ");
            String a = i > 0 ? song.substring(0, i) : "", t = i > 0 ? song.substring(i + 3) : song;
            if (!t.equals(title) || !a.equals(artist)) trackSince = System.currentTimeMillis();
            artist = a;
            title = t;
        }
    }

    private static String lastCsv(String line) {
        String s = line.strip();
        if (s.endsWith("\"")) s = s.substring(0, s.length() - 1);
        int i = s.lastIndexOf("\",\"");
        return i >= 0 ? s.substring(i + 3) : s;
    }

    private static String run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), WIN ? java.nio.charset.Charset.defaultCharset() : StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
        }
        if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly();
        return sb.toString();
    }

    // ---- controls ------------------------------------------------------------------------------

    public static void playPause() { control(179, "playpause", "play-pause"); }
    public static void next() { control(176, "next track", "next"); }
    public static void previous() { control(177, "previous track", "previous"); }

    private static void control(int winKey, String mac, String linux) {
        Thread t = new Thread(() -> {
            try {
                if (WIN) run("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                        "(New-Object -ComObject WScript.Shell).SendKeys([char]" + winKey + ")");
                else if (MAC) run("osascript", "-e", "tell application \"Spotify\" to " + mac);
                else run("playerctl", "-p", "spotify", linux);
                Thread.sleep(250);
                poll();
            } catch (Throwable ignored) {
                // Spotify not installed / tool missing
            }
        }, "DonutWaffen-Spotify-Control");
        t.setDaemon(true);
        t.start();
    }
}
