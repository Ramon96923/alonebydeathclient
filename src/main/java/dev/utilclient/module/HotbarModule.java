package dev.utilclient.module;

import dev.utilclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Replaces the vanilla hotbar with a rounded one that matches the client's HUD and theme. Same size and spot,
 * so hearts, hunger and XP stay exactly where you're used to.
 */
public final class HotbarModule extends Module {
    private final BoolSetting accentSlot = bool("Accent selection", true).desc("Highlight the held slot in the theme colour");
    private final BoolSetting slotBacks = bool("Slot backgrounds", true);

    private float slide = -1;

    public HotbarModule() {
        super("Hotbar", "Rounded hotbar in the client style", Category.RENDER);
        enabledByDefault = true;
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p.isSpectator()) return;
        HudModule hud = ModuleManager.HUD;
        int r = hud.cornerRadius();
        int w = 182, h = 22, x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() - h - 1;
        Draw.round(g, x, y, x + w, y + h, r + 2, hud.bgColor() == 0 ? 0x90000000 : hud.bgColor());

        int sel = p.getInventory().getSelectedSlot();
        if (slide < 0) slide = sel;
        slide += (sel - slide) * 0.35f;   // the highlight glides between slots
        int sx = Math.round(x + 1 + slide * 20);
        for (int i = 0; i < 9; i++) {
            if (slotBacks.get()) Draw.round(g, x + 2 + i * 20, y + 2, x + 20 + i * 20, y + 20, Math.max(2, r - 1), 0x28FFFFFF);
        }
        int acc = hud.accent(0f);
        Draw.round(g, sx, y + 1, sx + 20, y + 21, Math.max(2, r), accentSlot.get() ? Draw.withAlpha(acc, 0.45f) : 0x60FFFFFF);
        Draw.round(g, sx, y + 20, sx + 20, y + 21, 0, accentSlot.get() ? acc : 0xFFFFFFFF);

        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            int ix = x + 3 + i * 20, iy = y + 3;
            g.item(s, ix, iy);
            g.itemDecorations(mc.font, s, ix, iy);
        }
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) {
            int ox = x - 26;
            Draw.round(g, ox, y, ox + 22, y + h, r + 2, hud.bgColor() == 0 ? 0x90000000 : hud.bgColor());
            g.item(off, ox + 3, y + 3);
            g.itemDecorations(mc.font, off, ox + 3, y + 3);
        }
    }
}
