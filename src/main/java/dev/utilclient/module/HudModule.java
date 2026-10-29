package dev.utilclient.module;

import dev.utilclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/** Watermark, enabled-module list and coordinates. */
public final class HudModule extends Module {
    private final BoolSetting watermark = add("Watermark", true);
    private final BoolSetting arraylist = add("Module list", true);
    private final BoolSetting coords = add("Coordinates", true);

    public HudModule() { super("HUD", "Watermark, module list, coordinates", Category.RENDER); }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();

        if (watermark.get()) g.text(font, "AloneByDeath", 6, 6, 0xFF8B5CF6, true);
        if (coords.get()) {
            g.text(font, String.format("XYZ %.1f  %.1f  %.1f", p.getX(), p.getY(), p.getZ()), 6, 18, 0xFFE8E8EE, true);
        }
        if (arraylist.get()) {
            List<Module> on = new ArrayList<>();
            for (Module m : ModuleManager.ALL) if (m.isEnabled() && m != this) on.add(m);
            on.sort((a, b) -> font.width(b.name) - font.width(a.name));
            int y = 4, i = 0;
            for (Module m : on) {
                int w = font.width(m.name);
                int color = Draw.hsv(0.75f + i * 0.03f, 0.55f, 1.0f);
                g.fill(sw - w - 9, y - 1, sw - 2, y + 10, 0x90000000);
                g.fill(sw - 2, y - 1, sw, y + 10, color);
                g.text(font, m.name, sw - w - 6, y, color, true);
                y += 11;
                i++;
            }
        }
    }
}
