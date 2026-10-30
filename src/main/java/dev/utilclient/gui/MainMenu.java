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
 * The AloneByDeath card on the title screen: client name and version, a button that opens the click GUI
 * right on the menu, and a link to AloneByDeath's Spotify. Drawn on top of the vanilla title screen, so the
 * normal buttons (and other mods' buttons) keep working. Switch it off in ClickGUI > Main menu card.
 */
public final class MainMenu {
    public static final String SPOTIFY = "https://open.spotify.com/artist/0MVEjD74djJKq2ZJcNniE7";
    private static final Logger LOG = Logger.getLogger("alonebydeathclient");
    private static final int SPOTIFY_GREEN = 0xFF1DB954;
    private static final int CARD = 0x0E0E14;

    private static Screen shownOn;
    private static long shownAt;
    private static boolean broken;

    // button rectangles from the last frame, in screen coordinates
    private static int guiX0, guiY0, guiX1, guiY1;
    private static int spX0, spY0, spX1, spY1;

    private MainMenu() {}

    private static boolean enabled() { return !broken && ModuleManager.CLICK_GUI.mainMenu.get(); }

    /** Called from ScreenEvents.AFTER_INIT for every TitleScreen (re-inits on resize, too). */
    public static void attach(Screen screen) {
        if (screen != shownOn) {
            shownOn = screen;
            shownAt = System.currentTimeMillis();
        }
        guiX1 = spX1 = -1;   // nothing clickable until the first frame lays the card out

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
            if (!enabled() || event.button() != 0) return true;
            return !click(s, event.x(), event.y());
        });
    }

    private static long lastClick;

    /** Runs the button under (x, y), if any. @return true if a button was clicked */
    private static boolean click(Screen s, double x, double y) {
        long now = System.currentTimeMillis();
        if (now - lastClick < 200) return inside(x, y, guiX0, guiY0, guiX1, guiY1) || inside(x, y, spX0, spY0, spX1, spY1);
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
        String title = "AloneByDeath";
        String sub = "CLIENT  ·  v" + UtilClient.version() + "  ·  " + ModuleManager.ALL.size() + " modules";
        String guiLabel = "Open Click GUI";
        String guiKey = Keys.name(UtilClient.guiKey());
        String spLabel = "AloneByDeath on Spotify";

        float titleScale = 1.6f;
        int pad = 9;
        int titleW = (int) Math.ceil(Fonts.width(title, true) * titleScale);
        int w = Math.max(160, Math.max(titleW, Math.max(Fonts.width(sub), Fonts.width(spLabel, true) + 26)) + pad * 2);
        int x = 8 - (int) ((1f - e) * 14f), y = 8;
        int h = 96;

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

        if (hoverSp) {
            String hint = "open.spotify.com";
            Fonts.text(g, hint, x + w / 2 - Fonts.width(hint) / 2, y + h + 3, Draw.withAlpha(0xFF9A9AB0, a));
        } else if (hoverGui) {
            String hint = "Change modules and settings right here";
            Fonts.text(g, hint, x + w / 2 - Fonts.width(hint) / 2, y + h + 3, Draw.withAlpha(0xFF9A9AB0, a));
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
