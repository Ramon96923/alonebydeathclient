package dev.utilclient.gui;

import dev.utilclient.module.ModuleManager;
import dev.utilclient.util.Draw;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/**
 * Redraws vanilla menu buttons (title screen, pause menu, options...) as rounded pills in the client theme,
 * so the whole game matches the click GUI. Painted over the vanilla button after the screen draws, so the
 * button itself (clicks, focus, narration) is untouched. Icon buttons keep their vanilla look.
 */
public final class VanillaButtons {
    private VanillaButtons() {}

    public static void attach(Screen screen) {
        if (screen instanceof ClickGui) return;
        ScreenEvents.afterExtract(screen).register((s, g, mx, my, tick) -> {
            if (!ModuleManager.CLICK_GUI.roundedMenus.get()) return;
            try {
                draw(s, g);
            } catch (Throwable ignored) {
                // cosmetic only
            }
        });
    }

    private static void draw(Screen s, GuiGraphicsExtractor g) {
        boolean any = false;
        int r = Math.max(3, ModuleManager.CLICK_GUI.radius.getInt() + 1);
        for (AbstractWidget w : Screens.getWidgets(s)) {
            if (!(w instanceof Button) || !w.visible) continue;
            String label = w.getMessage().getString();
            if (label.isEmpty() || w.getWidth() <= w.getHeight() + 4) continue;   // icon buttons
            if (!any) { g.nextStratum(); any = true; }
            int x0 = w.getX(), y0 = w.getY(), x1 = x0 + w.getWidth(), y1 = y0 + w.getHeight();
            boolean hover = w.isHoveredOrFocused() && w.active;
            int acc = ModuleManager.CLICK_GUI.accent((x0 + y0) / 400f);
            // cover the square vanilla sprite (1px larger), then the pill on top
            Draw.round(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, r + 1, 0xFF0B0B10);
            int fill = !w.active ? 0xFF1A1A22 : hover ? Draw.mix(0xFF16161F, acc, 0.35f) : 0xFF16161F;
            Draw.round(g, x0, y0, x1, y1, r, fill);
            if (hover) Draw.round(g, x0 + r, y1 - 2, x1 - r, y1 - 1, 0, acc);
            int text = !w.active ? 0xFF6B6B78 : 0xFFF2F2F7;
            Fonts.centered(g, Fonts.fit(label, w.getWidth() - 8), (x0 + x1) / 2, y0 + (w.getHeight() - 8) / 2, text, hover);
        }
    }
}
