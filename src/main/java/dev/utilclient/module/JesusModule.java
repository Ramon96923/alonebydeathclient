package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Walk on water (and optionally lava). Hooks LivingEntity.canStandOnFluid the way striders walk on lava, so
 * still liquid is solid underfoot; if you do end up in it you float back to the surface. Hold Shift to dive.
 * Approach after Meteor's Jesus (GPL-3.0).
 */
public final class JesusModule extends Module {
    private final BoolSetting water = bool("Water", true);
    private final BoolSetting lava = bool("Lava", true).desc("Walk on lava too (you'll still burn if you sink)");

    public JesusModule() { super("Jesus", "Walk on water", Category.MOVEMENT); }

    /** From LivingEntityMixin: should the local player stand on this fluid? */
    public boolean standOn(FluidState fluid) {
        if (!isEnabled()) return false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isShiftKeyDown() || p.isSwimming() || p.isFallFlying()) return false;
        if (water.get() && (fluid.getType() == Fluids.WATER || fluid.getType() == Fluids.FLOWING_WATER)) return true;
        return lava.get() && (fluid.getType() == Fluids.LAVA || fluid.getType() == Fluids.FLOWING_LAVA);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p.isShiftKeyDown() || p.isSwimming()) return;
        boolean inWater = water.get() && p.isInWater(), inLava = lava.get() && p.isInLava();
        if (inWater || inLava) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, 0.11, v.z);   // bob up to the surface
        }
    }
}
