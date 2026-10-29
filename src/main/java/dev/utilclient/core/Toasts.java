package dev.utilclient.core;

import dev.utilclient.gui.Fonts;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small slide-in notifications in the bottom-right corner. */
public final class Toasts {
    private record Toast(String title, String text, int color, long start) {}

    private static final List<Toast> TOASTS = new ArrayList<>();
    private static final long LIFE = 2600L, SLIDE = 220L;

    private Toasts() {}

    public static void push(String title, String text, int color) {
        synchronized (TOASTS) {
            TOASTS.add(new Toast(title, text, color, System.currentTimeMillis()));
            while (TOASTS.size() > 6) TOASTS.remove(0);
        }
    }

    public static void render(GuiGraphicsExtractor g, int screenW, int screenH) {
        long now = System.currentTimeMillis();
        synchronized (TOASTS) {
            Iterator<Toast> it = TOASTS.iterator();
            while (it.hasNext()) if (now - it.next().start > LIFE) it.remove();
            int y = screenH - 6;
            for (int i = TOASTS.size() - 1; i >= 0; i--) {
                Toast t = TOASTS.get(i);
                long age = now - t.start;
                float in = Math.min(1f, age / (float) SLIDE);
                float out = Math.min(1f, Math.max(0f, (LIFE - age) / (float) SLIDE));
                float slide = ease(Math.min(in, out));
                int w = Math.max(Fonts.width(t.title, true), Fonts.width(t.text)) + 16;
                int h = 24;
                int x = screenW - (int) ((w + 6) * slide);
                y -= h + 3;
                int alpha = (int) (230 * slide);
                Draw.soft(g, x, y, x + w, y + h, Draw.alpha(0x0E0E14, alpha));
                g.fill(x, y + 1, x + 2, y + h - 1, Draw.alpha(t.color, Math.max(8, (int) (255 * slide))));
                // progress bar
                float life = 1f - Math.min(1f, age / (float) LIFE);
                g.fill(x + 2, y + h - 1, x + 2 + (int) ((w - 3) * life), y + h, Draw.alpha(t.color, Math.max(8, (int) (160 * slide))));
                Fonts.text(g, t.title, x + 8, y + 3, Draw.alpha(0xFFFFFF, Math.max(8, (int) (255 * slide))), true);
                Fonts.text(g, t.text, x + 8, y + 13, Draw.alpha(0xB8B8C8, Math.max(8, (int) (255 * slide))));
            }
        }
    }

    private static float ease(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    /** Accent colour from the GUI theme for toasts that don't carry their own. */
    public static int accent() { return ModuleManager.CLICK_GUI.accent(0f) & 0xFFFFFF; }
}
