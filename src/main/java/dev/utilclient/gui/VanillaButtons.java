package dev.utilclient.gui;

import dev.utilclient.module.ModuleManager;
import dev.utilclient.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws vanilla buttons (title screen, pause menu, options, every screen) as fully round pills in the
 * client theme. Hooked into AbstractButton's background sprite, so the vanilla label, clicks, focus and
 * narration are untouched; square icon buttons become circles.
 */
public final class VanillaButtons {
    private VanillaButtons() {}

    public static void attach(Screen screen) { }   // nothing per-screen any more: AbstractButtonMixin does it

    public static boolean enabled() {
        try {
            return ModuleManager.CLICK_GUI.roundedMenus.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Replaces the vanilla button sprite. */
    public static void drawBackground(GuiGraphicsExtractor g, AbstractWidget w) {
        int x0 = w.getX(), y0 = w.getY(), x1 = x0 + w.getWidth(), y1 = y0 + w.getHeight();
        float a = Math.max(0f, Math.min(1f, w.getAlpha()));
        boolean hover = w.isHoveredOrFocused() && w.active;
        int acc = ModuleManager.CLICK_GUI.accent((x0 + y0) / 400f);
        int fill = !w.active ? 0xFF1A1A22 : hover ? Draw.mix(0xFF17171F, acc, 0.38f) : 0xFF17171F;
        Draw.round(g, x0, y0, x1, y1, 99, Draw.withAlpha(fill, 0.92f * a));
        if (hover) {
            int r = (y1 - y0) / 2;
            Draw.round(g, x0 + r, y1 - 2, x1 - r, y1 - 1, 0, Draw.withAlpha(acc, a));
        }
    }
}
