package dev.utilclient.module;

import dev.utilclient.core.Friends;
import dev.utilclient.render.WorldRender;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Entity ESP: vanilla glow outlines (through walls) and/or 2D boxes with health bars. */
public final class EspModule extends Module {
    private final ModeSetting mode = mode("Mode", "Glow", "Glow", "Box", "Both");
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", true);
    private final BoolSetting items = bool("Dropped items", false);
    private final BoolSetting crystals = bool("Crystals", true);
    private final NumberSetting range = num("Box range", 96, 16, 256, 8).unit("m").visible(() -> !mode.is("Glow"));
    private final BoolSetting healthBar = bool("Health bar", true).visible(() -> !mode.is("Glow"));
    private final ColorSetting playerColor = color("Player colour", 0xFF4DA3FF);
    private final ColorSetting friendColor = color("Friend colour", 0xFF2DD4BF);
    private final ColorSetting hostileColor = color("Hostile colour", 0xFFFF4D4D);
    private final ColorSetting passiveColor = color("Passive colour", 0xFF57F287);
    private final ColorSetting itemColor = color("Item colour", 0xFFFFD84D);
    private final ColorSetting crystalColor = color("Crystal colour", 0xFFF472B6);

    private List<Entity> boxed = List.of();

    public EspModule() { super("ESP", "See entities through walls", Category.RENDER); }

    @Override public String info() { return mode.get(); }

    private static boolean isSelf(Entity e) { return e == Minecraft.getInstance().player; }

    public boolean matches(Entity e) {
        if (isSelf(e)) return false;
        if (e instanceof EndCrystal) return crystals.get();
        if (e instanceof ItemEntity) return items.get();
        return switch (EntityClass.of(e)) {
            case PLAYER -> players.get();
            case HOSTILE -> hostile.get();
            case PASSIVE -> passive.get();
            case OTHER -> false;
        };
    }

    public boolean shouldGlow(Entity e) {
        return isEnabled() && !mode.is("Box") && matches(e);
    }

    /** Glow outline RGB, or -1 if this entity isn't ours. */
    public int colorFor(Entity e) {
        if (!shouldGlow(e)) return -1;
        return colorOf(e);
    }

    public int colorOf(Entity e) {
        if (Friends.isFriend(e)) return friendColor.rgb();
        if (e instanceof EndCrystal) return crystalColor.rgb();
        if (e instanceof ItemEntity) return itemColor.rgb();
        return colorOf(EntityClass.of(e));
    }

    public int colorOf(EntityClass c) {
        return switch (c) {
            case PLAYER -> playerColor.rgb();
            case HOSTILE -> hostileColor.rgb();
            case PASSIVE -> passiveColor.rgb();
            case OTHER -> itemColor.rgb();
        };
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mode.is("Glow")) { boxed = List.of(); return; }
        LocalPlayer p = mc.player;
        double r2 = range.get() * range.get();
        List<Entity> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (matches(e) && e.distanceToSqr(p) <= r2) out.add(e);
        }
        boxed = out;
    }

    private static AABB box(Entity e, float pt) {
        double x = Mth.lerp(pt, e.xOld, e.getX()), y = Mth.lerp(pt, e.yOld, e.getY()), z = Mth.lerp(pt, e.zOld, e.getZ());
        double hw = e.getBbWidth() / 2.0;
        return new AABB(x - hw, y, z - hw, x + hw, y + e.getBbHeight(), z + hw);
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        if (mode.is("Glow")) return;
        for (Entity e : boxed) {
            if (e.isRemoved()) continue;
            int rgb = colorOf(e);
            ctx.box(box(e, ctx.partialTick), Draw.alpha(rgb, 40), Draw.alpha(rgb, 230));
        }
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        if (mode.is("Glow") || boxed.isEmpty()) return;
        boolean drawBoxes = !WorldRender.active();
        Minecraft mc = Minecraft.getInstance();
        Projection pr = new Projection(mc, g.guiWidth(), g.guiHeight());
        int[] r = new int[4];
        for (Entity e : boxed) {
            if (e.isRemoved()) continue;
            double x = Mth.lerp(pt, e.xOld, e.getX()), y = Mth.lerp(pt, e.yOld, e.getY()), z = Mth.lerp(pt, e.zOld, e.getZ());
            double hw = e.getBbWidth() / 2.0, h = e.getBbHeight();
            if (!pr.boxRect(x - hw, y, z - hw, x + hw, y + h, z + hw, r)) continue;
            int c = 0xFF000000 | colorOf(e);
            if (drawBoxes) {
                Draw.outline(g, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 0x90000000);
                Draw.outline(g, r[0], r[1], r[2], r[3], c);
                Draw.outline(g, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, 0x90000000);
            }
            if (healthBar.get() && e instanceof LivingEntity le) {
                float frac = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
                int bx = r[0] - 4, top = r[1], bot = r[3];
                g.fill(bx, top - 1, bx + 2, bot + 1, 0xA0000000);
                int fill = (int) ((bot - top) * frac);
                g.fill(bx, bot - fill, bx + 2, bot, Draw.healthColor(frac));
            }
        }
    }
}
