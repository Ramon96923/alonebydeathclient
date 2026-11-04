package dev.utilclient.module;

import dev.utilclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** Replaces the vanilla crosshair with a clean, customisable one (CS-style), with a dynamic gap and cooldown bar. */
public final class CrosshairModule extends Module {
    private final ColorSetting color = color("Colour", 0xFFFFFFFF);
    private final NumberSetting length = num("Length", 4, 1, 12, 1).unit("px");
    private final NumberSetting gap = num("Gap", 2, 0, 10, 1).unit("px");
    private final NumberSetting thickness = num("Thickness", 1, 1, 4, 1).unit("px");
    private final BoolSetting dot = bool("Dot", false);
    private final BoolSetting outline = bool("Outline", true).desc("Thin dark border so it shows on bright blocks");
    private final BoolSetting dynamic = bool("Dynamic gap", true).desc("Opens up while you move or fall");
    private final BoolSetting cooldown = bool("Cooldown bar", true).desc("Small bar showing your attack charge");
    private final BoolSetting targetColor = bool("Target colour", true).desc("Turns red when you're aiming at an entity");

    private float spread;

    public CrosshairModule() { super("Crosshair", "Custom crosshair", Category.RENDER); }

    private void bar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int col) {
        if (outline.get()) g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xA0000000);
        g.fill(x0, y0, x1, y1, col);
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!mc.options.getCameraType().isFirstPerson() || mc.gui.screen() != null) return;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        double moving = Math.sqrt(p.getDeltaMovement().x * p.getDeltaMovement().x + p.getDeltaMovement().z * p.getDeltaMovement().z);
        float want = dynamic.get() ? (float) Math.min(6, moving * 18 + (p.onGround() ? 0 : 2)) : 0;
        spread += (want - spread) * 0.25f;
        int gp = gap.getInt() + Math.round(spread), len = length.getInt(), t = thickness.getInt();
        int col = color.get() | 0xFF000000;
        if (targetColor.get() && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult) col = 0xFFFF4D4D;
        int h0 = t / 2, h1 = t - h0;
        bar(g, cx - gp - len, cy - h0, cx - gp, cy + h1, col);   // left
        bar(g, cx + gp + 1, cy - h0, cx + gp + 1 + len, cy + h1, col);   // right
        bar(g, cx - h0, cy - gp - len, cx + h1, cy - gp, col);   // top
        bar(g, cx - h0, cy + gp + 1, cx + h1, cy + gp + 1 + len, col);   // bottom
        if (dot.get()) bar(g, cx - h0, cy - h0, cx + h1, cy + h1, col);
        if (cooldown.get()) {
            float charge = p.getAttackStrengthScale(0f);
            if (charge < 1f) {
                int w = 16, y = cy + gp + len + 5;
                g.fill(cx - w / 2, y, cx + w / 2, y + 2, 0x80000000);
                g.fill(cx - w / 2, y, cx - w / 2 + (int) (w * charge), y + 2, Draw.withAlpha(col, 0.9f));
            }
        }
    }
}
