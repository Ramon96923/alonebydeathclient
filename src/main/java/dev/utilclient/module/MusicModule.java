package dev.utilclient.module;

import dev.utilclient.music.MusicPlayer;

/**
 * The AloneByDeath music player. Turning the module on plays, off pauses (so its key bind is a play/pause
 * key). Songs come from {@code config/donutwaffen/music/}; see {@code .music} for the rest.
 */
public final class MusicModule extends Module {
    private final NumberSetting volume = num("Volume", 60, 0, 100, 1).unit("%")
            .desc("Player volume (separate from Minecraft's)");
    private final BoolSetting shuffle = bool("Shuffle", false, () -> MusicPlayer.setShuffle(this.shuffle.get()));
    private final ModeSetting repeat = mode("Repeat", "All", "All", "One", "Off");
    private final BoolSetting resume = bool("Play on launch", false).desc("Keep playing next time you start the game");
    private final BoolSetting menu = bool("Main menu player", true).desc("Play/pause/next pill on the title screen");

    /** Set once the config has been loaded, so a saved "on" doesn't start music at launch by surprise. */
    public static boolean booted;

    public MusicModule() { super("MusicPlayer", "AloneByDeath music player for your own MP3s", Category.CLIENT); }

    public boolean menuPlayer() { return menu.get(); }

    @Override public String info() { return MusicPlayer.playing() ? "♪" : null; }

    /** Pushes the settings into the player (called every frame from the HUD and on toggle). */
    public void apply() {
        MusicPlayer.setVolume((float) (volume.get() / 100.0));
        MusicPlayer.setShuffle(shuffle.get());
        MusicPlayer.setRepeat(switch (repeat.get()) {
            case "One" -> MusicPlayer.Repeat.ONE;
            case "Off" -> MusicPlayer.Repeat.OFF;
            default -> MusicPlayer.Repeat.ALL;
        });
    }

    public void changeVolume(int percent) { volume.set(Math.max(0, Math.min(100, percent))); apply(); }

    @Override
    protected void onEnable() {
        if (!booted && !resume.get()) { setEnabled(false, false); return; }
        apply();
        MusicPlayer.play();
        if (!MusicPlayer.error().isEmpty()) {
            dev.utilclient.core.Chat.info(MusicPlayer.error());
            setEnabled(false, false);
        }
    }

    @Override protected void onDisable() { MusicPlayer.pause(); }

    /** Keeps the module toggle in sync when the player is controlled from the menu or a command. */
    public void sync() {
        apply();
        boolean p = MusicPlayer.playing() || (!MusicPlayer.paused() && !MusicPlayer.nowPlaying().isEmpty());
        if (p != isEnabled()) setEnabled(p, false);
    }
}
