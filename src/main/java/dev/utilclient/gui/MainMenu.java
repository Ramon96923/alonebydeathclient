package dev.utilclient.gui;

import com.mojang.blaze3d.Blaze3D;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.music.MusicPlayer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DonutWaffen buttons on the title screen, in the same column (and the same round style) as the vanilla
 * ones: Click GUI and AloneByDeath on Spotify, plus a music row (previous, play/pause with the song name,
 * next). The DonutWaffen logo replaces the Minecraft one through the resource pack textures. If the menu
 * is too short, the vanilla buttons are nudged up to make room. Switch off in ClickGUI > Main menu buttons.
 */
public final class MainMenu {
    public static final String SPOTIFY = "https://open.spotify.com/artist/0MVEjD74djJKq2ZJcNniE7";
    private static final Logger LOG = Logger.getLogger("donutwaffen");

    private MainMenu() {}

    /** Called from ScreenEvents.AFTER_INIT for every TitleScreen (re-inits on resize, too). */
    public static void attach(Screen screen) {
        if (!ModuleManager.CLICK_GUI.mainMenu.get()) return;
        try {
            addButtons(screen);
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Couldn't add the DonutWaffen menu buttons", t);
        }
    }

    private static void addButtons(Screen screen) {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        int cx = screen.width / 2;
        // the vanilla main column: wide buttons centred on the screen
        List<AbstractWidget> column = new ArrayList<>();
        int top = Integer.MAX_VALUE, bottom = 0;
        for (AbstractWidget w : widgets) {
            if (w.getX() > cx + 104 || w.getX() + w.getWidth() < cx - 104) continue;
            if (w.getY() < screen.height / 4) continue;
            column.add(w);
            top = Math.min(top, w.getY());
            bottom = Math.max(bottom, w.getY() + w.getHeight());
        }
        if (column.isEmpty()) { top = screen.height / 4 + 48; bottom = top + 92; }

        boolean player = ModuleManager.MUSIC.menuPlayer();
        int need = 4 + 24 + (player ? 24 : 0);
        int room = screen.height - 14 - bottom;
        if (room < need) {
            int shift = Math.min(need - room, Math.max(0, top - 84));   // never under the logo
            for (AbstractWidget w : column) w.setY(w.getY() - shift);
            bottom -= shift;
            room += shift;
            if (room < need && player) { player = false; need -= 24; }
        }
        int y = bottom + 4;

        // the Spotify label is long, so it gets the wider share of the row
        Button gui = Button.builder(Component.literal("Click GUI"),
                b -> Minecraft.getInstance().gui.setScreen(new ClickGui(screen))).bounds(cx - 100, y, 64, 20).build();
        Button spotify = Button.builder(Component.literal("AloneByDeath on Spotify"), b -> openSpotify())
                .bounds(cx - 32, y, 132, 20).build();
        widgets.add(gui);
        widgets.add(spotify);
        if (!player) return;

        y += 24;
        Button prev = Button.builder(Component.literal("<"), b -> run(MusicPlayer::previous)).bounds(cx - 100, y, 20, 20).build();
        Button play = Button.builder(Component.literal(playLabel()), b -> run(MusicPlayer::toggle)).bounds(cx - 78, y, 156, 20).build();
        Button next = Button.builder(Component.literal(">"), b -> run(MusicPlayer::next)).bounds(cx + 80, y, 20, 20).build();
        widgets.add(prev);
        widgets.add(play);
        widgets.add(next);
        ScreenEvents.afterTick(screen).register(s -> play.setMessage(Component.literal(playLabel())));
    }

    private static void run(Runnable r) {
        ModuleManager.MUSIC.apply();
        r.run();
        ModuleManager.MUSIC.sync();
    }

    private static String playLabel() {
        String song = MusicPlayer.nowPlaying();
        if (!MusicPlayer.error().isEmpty() && song.isEmpty()) return "Add songs: .music folder";
        if (song.isEmpty()) {
            int n = MusicPlayer.trackCount();
            return n > 0 ? "Play music (" + n + ")" : "Music player";
        }
        return (MusicPlayer.playing() ? "|| " : "> ") + song;
    }

    public static void openSpotify() {
        try {
            Blaze3D.openUri(URI.create(SPOTIFY));
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Couldn't open " + SPOTIFY, t);
        }
    }

    /** Kept for MouseHandlerMixin; the buttons are real widgets now, so nothing to intercept. */
    public static boolean onMousePress() { return false; }
}
