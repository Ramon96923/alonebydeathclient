package dev.utilclient.gui;

import com.mojang.blaze3d.Blaze3D;
import dev.utilclient.UtilClient;
import dev.utilclient.core.Keys;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.util.Draw;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The DonutWaffen card on the title screen: client name and version, a button that opens the click GUI
 * right on the menu, and a link to AloneByDeath's Spotify. Drawn on top of the vanilla title screen, so the
 * normal buttons (and other mods' buttons) keep working. Switch it off in ClickGUI > Main menu card.
 */
public final class MainMenu {
    public static final String SPOTIFY = "https://open.spotify.com/artist/0MVEjD74djJKq2ZJcNniE7";
    private static final Logger LOG = Logger.getLogger("donutwaffen");
    private static final int SPOTIFY_GREEN = 0xFF1DB954;
    private static final int CARD = 0x0E0E14;

    private static Screen shownOn;
    private static long shownAt;
    private static boolean broken;

    // button rectangles from the last frame, in screen coordinates
    private static int guiX0, guiY0, guiX1, guiY1;
    private static int spX0, spY0, spX1, spY1;
    // music player row: previous, play/pause, next
    private static int mpY0 = -1, mpY1, prevX0, prevX1, playX0, playX1, nextX0, nextX1;

    private MainMenu() {}

    private static boolean enabled() { return !broken && ModuleManager.CLICK_GUI.mainMenu.get(); }

    /** Called from ScreenEvents.AFTER_INIT for every TitleScreen (re-inits on resize, too). */
    public static void attach(Screen screen) {
        if (screen != shownOn) {
            shownOn = screen;
            shownAt = System.currentTimeMillis();
        }
        guiX1 = spX1 = -1;
        mpY0 = -1;   // nothing clickable until the first frame lays the card out

        ScreenEvents.afterExtract(screen).register((s, g, mx, my, tick) -> {
            if (!enabled()) return;
            try {
                render(g, mx, my);
            } catch (Throwable t) {
                broken = true;   // never break the title screen over a cosmetic card
                LOG.log(Level.WARNING, "Main menu card failed, hiding it", t);
            }
        });

        // backup path; the main one is onMousePress() from MouseHandlerMixin
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            if (!enabled() || event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return true;
            return !click(s, event.x(), event.y());
        });
    }

    private static long lastClick;

    /** Runs the button under (x, y), if any. @return true if a button was clicked */
    private static boolean click(Screen s, double x, double y) {
        long now = System.currentTimeMillis();
        if (now - lastClick < 200) return inside(x, y, guiX0, guiY0, guiX1, guiY1) || inside(x, y, spX0, spY0, spX1, spY1)
                || (mpY0 >= 0 && inside(x, y, prevX0, mpY0, nextX1, mpY1));
        if (mpY0 >= 0 && y >= mpY0 && y < mpY1) {
            Runnable act = null;
            if (x >= prevX0 && x < prevX1) act = dev.utilclient.music.MusicPlayer::previous;
            else if (x >= playX0 && x < playX1) act = dev.utilclient.music.MusicPlayer::toggle;
            else if (x >= nextX0 && x < nextX1) act = dev.utilclient.music.MusicPlayer::next;
            if (act != null) {
                lastClick = now;
                ModuleManager.MUSIC.apply();
                act.run();
                ModuleManager.MUSIC.sync();
                return true;
            }
        }
        if (inside(x, y, guiX0, guiY0, guiX1, guiY1)) {
            lastClick = now;
            Minecraft.getInstance().gui.setScreen(new ClickGui(s));
            return true;
        }
        if (inside(x, y, spX0, spY0, spX1, spY1)) {
            lastClick = now;
            openSpotify();
            return true;
        }
        return false;
    }

    /**
     * Left mouse press, straight from MouseHandler (before the screen sees it).
     * @return true if one of the card's buttons took the click
     */
    public static boolean onMousePress() {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen s)) return false;
        double x = mc.mouseHandler.getScaledXPos(mc.getWindow()), y = mc.mouseHandler.getScaledYPos(mc.getWindow());
        return click(s, x, y);
    }

    public static void openSpotify() {
        try {
            Blaze3D.openUri(URI.create(SPOTIFY));
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Couldn't open " + SPOTIFY, t);
        }
    }

    private static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
        return x1 > x0 && x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    private static float ease(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    private static void render(GuiGraphicsExtractor g, int mx, int my) {
        float a = Math.min(1f, (System.currentTimeMillis() - shownAt) / 450f);
        if (a < 0.06f) return;   // text with almost no alpha would draw fully opaque
        float e = ease(a);
        g.nextStratum();

        int acc = ModuleManager.CLICK_GUI.accent(0f);
        int r = Math.max(3, ModuleManager.CLICK_GUI.radius.getInt());
        String title = "DonutWaffen";
        String sub = "CLIENT  ·  v" + UtilClient.version() + "  ·  " + ModuleManager.ALL.size() + " modules";
        String guiLabel = "Open Click GUI";
        String guiKey = Keys.name(UtilClient.guiKey());
        String spLabel = "AloneByDeath on Spotify";

        float titleScale = 1.6f;
        int pad = 9;
        int titleW = (int) Math.ceil(Fonts.width(title, true) * titleScale);
        int w = Math.max(160, Math.max(titleW, Math.max(Fonts.width(sub), Fonts.width(spLabel, true) + 26)) + pad * 2);
        int x = 8 - (int) ((1f - e) * 14f), y = 8;
        boolean player = ModuleManager.MUSIC.menuPlayer();
        int h = player ? 124 : 96;

        // card
        Draw.round(g, x, y, x + w, y + h, r + 2, Draw.alpha(CARD, (int) (205 * a)));
        Draw.round(g, x + pad, y + 1, x + w - pad, y + 2, 0, Draw.withAlpha(acc, a));

        // title in the accent colour (follows gradient/rainbow), letter by letter
        g.pose().pushMatrix();
        g.pose().translate(x + pad, y + pad);
        g.pose().scale(titleScale, titleScale);
        int cx = 0;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            int col = Draw.withAlpha(ModuleManager.CLICK_GUI.accent(i * 0.04f), a);
            Fonts.text(g, ch, cx, 0, col, true);
            cx += Fonts.width(ch, true);
        }
        g.pose().popMatrix();
        Fonts.text(g, sub, x + pad, y + pad + 17, Draw.withAlpha(0xFF9A9AB0, a));

        // divider
        int dy = y + pad + 30;
        g.fill(x + pad, dy, x + w - pad, dy + 1, Draw.withAlpha(0x22FFFFFF, a));

        // Open Click GUI
        int bh = 17, bx0 = x + pad, bx1 = x + w - pad;
        int by0 = dy + 7;
        boolean hoverGui = inside(mx, my, bx0, by0, bx1, by0 + bh);
        int guiFill = hoverGui ? Draw.mix(acc, 0xFFFFFFFF, 0.18f) : acc;
        Draw.round(g, bx0, by0, bx1, by0 + bh, r, Draw.withAlpha(guiFill, a));
        drawMenuIcon(g, bx0 + 7, by0 + 5, Draw.withAlpha(0xFFFFFFFF, a));
        Fonts.text(g, guiLabel, bx0 + 19, by0 + 5, Draw.withAlpha(0xFFFFFFFF, a), true);
        Fonts.text(g, guiKey, bx1 - 7 - Fonts.width(guiKey), by0 + 5, Draw.withAlpha(0xB4FFFFFF, a));
        guiX0 = bx0; guiY0 = by0; guiX1 = bx1; guiY1 = by0 + bh;

        // Spotify
        int sy0 = by0 + bh + 5;
        boolean hoverSp = inside(mx, my, bx0, sy0, bx1, sy0 + bh);
        int spFill = hoverSp ? Draw.mix(SPOTIFY_GREEN, 0xFFFFFFFF, 0.15f) : SPOTIFY_GREEN;
        Draw.round(g, bx0, sy0, bx1, sy0 + bh, r, Draw.withAlpha(spFill, a));
        drawEqualizer(g, bx0 + 7, sy0 + 4, Draw.withAlpha(0xFF0B0B0B, a), hoverSp);
        Fonts.text(g, spLabel, bx0 + 19, sy0 + 5, Draw.withAlpha(0xFF0B0B0B, a), true);
        spX0 = bx0; spY0 = sy0; spX1 = bx1; spY1 = sy0 + bh;

        if (player) drawPlayer(g, mx, my, bx0, sy0 + bh + 6, bx1, r, acc, a);
        else mpY0 = -1;

        if (hoverSp) {
            String hint = "open.spotify.com";
            Fonts.text(g, hint, x + w / 2 - Fonts.width(hint) / 2, y + h + 3, Draw.withAlpha(0xFF9A9AB0, a));
        } else if (hoverGui) {
            String hint = "Change modules and settings right here";
            Fonts.text(g, hint, x + w / 2 - Fonts.width(hint) / 2, y + h + 3, Draw.withAlpha(0xFF9A9AB0, a));
        }
    }

    /** The AloneByDeath player row: song name with progress underneath, and prev / play-pause / next. */
    private static void drawPlayer(GuiGraphicsExtractor g, int mx, int my, int x0, int y0, int x1, int r, int acc, float a) {
        int h = 22;
        Draw.round(g, x0, y0, x1, y0 + h, r, Draw.withAlpha(0xFF1A1A24, a));
        boolean playing = dev.utilclient.music.MusicPlayer.playing();
        String song = dev.utilclient.music.MusicPlayer.nowPlaying();
        int n = dev.utilclient.music.MusicPlayer.trackCount();
        String label = !song.isEmpty() ? song : n > 0 ? "AloneByDeath Player  \u00b7  " + n + " songs" : "AloneByDeath Player  \u00b7  add songs";
        // three round buttons on the right
        int bs = 14, gap = 3, by = y0 + 4;
        nextX1 = x1 - 4; nextX0 = nextX1 - bs;
        playX1 = nextX0 - gap; playX0 = playX1 - bs;
        prevX1 = playX0 - gap; prevX0 = prevX1 - bs;
        mpY0 = by; mpY1 = by + bs;
        int[][] btn = {{prevX0, prevX1}, {playX0, playX1}, {nextX0, nextX1}};
        for (int i = 0; i < 3; i++) {
            boolean hov = inside(mx, my, btn[i][0], by, btn[i][1], by + bs);
            int fill = i == 1 ? (hov ? Draw.mix(acc, 0xFFFFFFFF, 0.18f) : acc) : (hov ? 0xFF34344A : 0xFF26263A);
            Draw.round(g, btn[i][0], by, btn[i][1], by + bs, bs / 2, Draw.withAlpha(fill, a));
        }
        int white = Draw.withAlpha(0xFFFFFFFF, a);
        // previous: bar + triangle, next: triangle + bar, play: triangle, pause: two bars
        triangle(g, prevX0 + 6, by + 4, -1, white); g.fill(prevX0 + 4, by + 4, prevX0 + 5, by + 11, white);
        triangle(g, nextX0 + 4, by + 4, 1, white); g.fill(nextX0 + 9, by + 4, nextX0 + 10, by + 11, white);
        if (playing) {
            g.fill(playX0 + 4, by + 4, playX0 + 6, by + 11, white);
            g.fill(playX0 + 8, by + 4, playX0 + 10, by + 11, white);
        } else {
            triangle(g, playX0 + 5, by + 4, 1, white);
        }
        int tw = prevX0 - x0 - 10;
        Fonts.text(g, Fonts.fit(label, tw), x0 + 6, y0 + 5, Draw.withAlpha(song.isEmpty() ? 0xFF9A9AB0 : 0xFFFFFFFF, a));
        long len = dev.utilclient.music.MusicPlayer.lengthMs();
        if (!song.isEmpty() && len > 0) {
            float p = Math.min(1f, dev.utilclient.music.MusicPlayer.positionMs() / (float) len);
            int bx0 = x0 + 6, bx1 = prevX0 - 6, py = y0 + h - 5;
            g.fill(bx0, py, bx1, py + 2, Draw.withAlpha(0x30FFFFFF, a));
            g.fill(bx0, py, bx0 + (int) ((bx1 - bx0) * p), py + 2, Draw.withAlpha(acc, a));
        }
        String err = dev.utilclient.music.MusicPlayer.error();
        if (!err.isEmpty() && inside(mx, my, prevX0, by, nextX1, by + bs)) {
            Fonts.text(g, Fonts.fit(err, 260), x0, y0 + h + 4, Draw.withAlpha(0xFFFF8080, a));
        }
    }

    /** A small 7-tall triangle pointing right (dir 1) or left (dir -1), drawn as stacked lines. */
    private static void triangle(GuiGraphicsExtractor g, int x, int y, int dir, int color) {
        for (int i = 0; i < 7; i++) {
            int len = 4 - Math.abs(i - 3);
            if (dir > 0) g.fill(x, y + i, x + len, y + i + 1, color);
            else g.fill(x + 4 - len, y + i, x + 4, y + i + 1, color);
        }
    }

    /** Three short lines, like a menu icon. */
    private static void drawMenuIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int i = 0; i < 3; i++) g.fill(x, y + i * 3, x + 8, y + i * 3 + 1, color);
    }

    /** Four bouncing bars, faster while hovered. */
    private static void drawEqualizer(GuiGraphicsExtractor g, int x, int y, int color, boolean fast) {
        double t = System.currentTimeMillis() / (fast ? 120.0 : 210.0);
        int maxH = 9;
        for (int i = 0; i < 4; i++) {
            int bh = 2 + (int) Math.round((maxH - 2) * Math.abs(Math.sin(t + i * 1.3)));
            g.fill(x + i * 3, y + maxH - bh, x + i * 3 + 2, y + maxH, color);
        }
    }
}
