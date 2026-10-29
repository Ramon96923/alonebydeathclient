package dev.utilclient.module;

import dev.utilclient.util.Compat;
import dev.utilclient.util.Ids;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Places end crystals on obsidian/bedrock beside a target and breaks them.
 * Heuristic only (no damage simulation): crystals must be closer to the target than to you.
 */
public final class AutoCrystalModule extends Module {
    private final NumberSetting targetRange = num("Target range", 8, 4, 12, 1);
    private final NumberSetting placeRange  = num("Place range", 4.0, 2.0, 4.5, 0.5);
    private final NumberSetting breakRange  = num("Break range", 3.0, 2.0, 4.0, 0.5);
    private final NumberSetting placeDelay  = num("Place delay", 3, 1, 10, 1);
    private final NumberSetting breakDelay  = num("Break delay", 2, 1, 10, 1);
    private final NumberSetting minHealth   = num("Min health", 10, 1, 20, 1);
    private final BoolSetting players       = add("Players", true);
    private final BoolSetting hostile       = add("Hostile mobs", true);
    private final BoolSetting passive       = add("Passive mobs", false);
    private final BoolSetting switchSlot    = add("Auto switch", true);
    private int tick;

    public AutoCrystalModule() { super("AutoCrystal", "Place & break end crystals near targets", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.gameMode == null || mc.gui.screen() != null || p.isSpectator()) return;
        if (p.getHealth() < minHealth.get()) return;

        Entity target = TargetFilter.nearest(mc, targetRange.get(),
                e -> TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), true));
        if (target == null) return;
        tick++;

        if (tick % breakDelay.getInt() == 0) breakCrystal(mc, p, target);
        if (tick % placeDelay.getInt() == 0) placeCrystal(mc, p, level, target);
    }

    private void breakCrystal(Minecraft mc, LocalPlayer p, Entity target) {
        double br = breakRange.get();
        List<Entity> crystals = mc.level.getEntities(p, p.getBoundingBox().inflate(br + 1.0),
                e -> Ids.entity(e).equals("end_crystal"));
        Entity best = null;
        double bestD = 1e9;
        for (Entity c : crystals) {
            if (c.getBoundingBox().distanceToSqr(p.getEyePosition()) > br * br) continue;
            double dt = c.distanceTo(target);
            if (dt > 7.0 || dt + 1.0 > c.distanceTo(p)) continue;   // keep blasts on the target's side
            if (dt < bestD) { bestD = dt; best = c; }
        }
        if (best != null) mc.gameMode.attack(p, best);
    }

    private void placeCrystal(Minecraft mc, LocalPlayer p, ClientLevel level, Entity target) {
        InteractionHand hand = null;
        if (Ids.isItem(p.getMainHandItem(), "end_crystal")) hand = InteractionHand.MAIN_HAND;
        else if (Ids.isItem(p.getOffhandItem(), "end_crystal")) hand = InteractionHand.OFF_HAND;
        else if (switchSlot.get()) {
            int slot = Compat.findHotbar(p, "end_crystal");
            if (slot >= 0 && Compat.selectHotbar(p, slot)) hand = InteractionHand.MAIN_HAND;
        }
        if (hand == null) return;

        Vec3 eye = p.getEyePosition();
        Vec3 tpos = target.position();
        BlockPos tp = target.blockPosition();
        BlockPos bestBase = null;
        double bestScore = 1e9;

        for (int dx = -4; dx <= 4; dx++) for (int dy = -3; dy <= 2; dy++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos b = tp.offset(dx, dy, dz);
            BlockState st = level.getBlockState(b);
            if (!(st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK))) continue;
            BlockPos a1 = b.above();
            if (!level.getBlockState(a1).isAir() || !level.getBlockState(b.above(2)).isAir()) continue;
            Vec3 top = new Vec3(b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5);
            if (top.distanceTo(eye) > placeRange.get()) continue;
            double dt = top.distanceTo(tpos);
            if (dt > 6.0 || dt + 1.0 > top.distanceTo(p.position())) continue;
            if (!level.getEntities((Entity) null, new AABB(a1).expandTowards(0, 1, 0), e -> true).isEmpty()) continue;
            if (dt < bestScore) { bestScore = dt; bestBase = b; }
        }
        if (bestBase == null) return;

        Vec3 hit = new Vec3(bestBase.getX() + 0.5, bestBase.getY() + 1.0, bestBase.getZ() + 0.5);
        mc.gameMode.useItemOn(p, hand, new BlockHitResult(hit, Direction.UP, bestBase, false));
    }
}
