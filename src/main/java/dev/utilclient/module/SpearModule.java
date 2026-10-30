package dev.utilclient.module;

import dev.utilclient.util.Inv;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spear support for 26.3's new weapon. Swaps to a spear for the extra reach when a target is further than a
 * sword can hit, and to a Lunge spear to dash when you swing at the air while sprinting. Swaps back after.
 * Approach follows Meteor's AttributeSwap and LiquidBounce's SpearKill (both GPL-3.0); the code is original.
 */
public final class SpearModule extends Module {
    private final NumberSetting swordReach = num("Sword reach", 3.5, 2.5, 5.0, 0.1).unit("m")
            .desc("Swap to a spear for targets further than this");
    private final NumberSetting spearReach = num("Spear reach", 6.5, 4.0, 9.0, 0.5).unit("m");
    private final BoolSetting lunge = bool("Lunge dash", true).desc("Swap to a Lunge spear when you swing at air while sprinting");
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", false);
    private final NumberSetting swapBack = num("Swap back", 3, 0, 20, 1).unit("t").desc("0 = stay on the spear");

    private int prevSlot = -1;
    private int backTimer;

    public SpearModule() { super("Spear", "Reach and lunge with a spear", Category.COMBAT); }

    @Override protected void onDisable() { restore(Minecraft.getInstance().player); }

    static boolean isSpear(ItemStack s) { return !s.isEmpty() && s.is(ItemTags.SPEARS); }

    static boolean hasLunge(ItemStack s) {
        if (!isSpear(s)) return false;
        for (var e : s.getEnchantments().entrySet()) if (e.getKey().is(Enchantments.LUNGE)) return true;
        return false;
    }

    private int findSpear(LocalPlayer p, boolean wantLunge) {
        return Inv.hotbar(p, s -> wantLunge ? hasLunge(s) : isSpear(s));
    }

    /** A living target roughly along the look ray between sword reach and spear reach, else null. */
    private Entity farTarget(Minecraft mc) {
        LocalPlayer p = mc.player;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        double max = spearReach.get();
        Vec3 end = eye.add(look.scale(max));
        AABB box = p.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0);
        Entity best = null;
        double bestD = max * max;
        for (Entity e : mc.level.getEntities(p, box, x -> TargetFilter.accept(x, p, players.get(), hostile.get(), passive.get(), true))) {
            var clip = e.getBoundingBox().inflate(0.15).clip(eye, end);
            if (clip.isEmpty()) continue;
            double d = clip.get().distanceToSqr(eye);
            if (d < bestD) { bestD = d; best = e; }
        }
        if (best == null) return null;
        return Math.sqrt(bestD) > swordReach.get() ? best : null;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gui.screen() != null || p.isSpectator()) {
            restore(p);
            return;
        }
        // reach swap: hold a spear while a target sits past sword range
        Entity far = farTarget(mc);
        if (far != null && !isSpear(p.getMainHandItem())) {
            int slot = findSpear(p, false);
            if (slot >= 0) swapTo(p, slot);
        } else if (far == null && prevSlot >= 0 && swapBack.getInt() > 0 && backTimer-- <= 0) {
            restore(p);
        }
    }

    /** From the attack mixin HEAD: make sure a far target is hit with the spear. */
    public void beforeAttack(Entity target) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || isSpear(p.getMainHandItem())) return;
        if (p.distanceTo(target) > swordReach.get()) {
            int slot = findSpear(p, false);
            if (slot >= 0) { swapTo(p, slot); backTimer = swapBack.getInt(); }
        }
    }

    /** From the startAttack mixin: a swing that hit nothing. Lunge if sprinting and a Lunge spear is around. */
    public void onSwingAir() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !lunge.get() || !p.isSprinting()) return;
        int slot = findSpear(p, true);
        if (slot >= 0) { swapTo(p, slot); backTimer = Math.max(backTimer, swapBack.getInt()); }
    }

    private void swapTo(LocalPlayer p, int slot) {
        if (slot == Inv.selected(p)) return;
        if (prevSlot < 0) prevSlot = Inv.selected(p);
        Inv.select(p, slot);
        backTimer = swapBack.getInt();
    }

    private void restore(LocalPlayer p) {
        if (p != null && prevSlot >= 0 && prevSlot < 9) Inv.select(p, prevSlot);
        prevSlot = -1;
        backTimer = 0;
    }
}
