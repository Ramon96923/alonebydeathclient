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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places a respawn anchor near a target, charges it with glowstone and detonates it.
 * Anchors only explode outside the Nether, so the module does nothing there.
 */
public final class AutoAnchorModule extends Module {
    private final NumberSetting targetRange = num("Target range", 8, 4, 12, 1);
    private final NumberSetting placeRange  = num("Place range", 4.0, 2.0, 4.5, 0.5);
    private final NumberSetting delay       = num("Delay (ticks)", 3, 1, 10, 1);
    private final NumberSetting minHealth   = num("Min health", 14, 1, 20, 1);
    private final NumberSetting safeDist    = num("Safe distance", 4.0, 2.0, 6.0, 0.5);
    private final BoolSetting players       = add("Players", true);
    private final BoolSetting hostile       = add("Hostile mobs", true);
    private final BoolSetting passive       = add("Passive mobs", false);
    private int cooldown;

    public AutoAnchorModule() { super("AutoAnchor", "Place, charge & detonate respawn anchors", Category.COMBAT); }

    private static int charges(BlockState st) {
        for (Property<?> pr : st.getProperties()) {
            if (pr.getName().equals("charges") && st.getValue(pr) instanceof Integer i) return i;
        }
        return 0;
    }

    private static Vec3 faceHit(BlockPos pos, Direction face) {
        Vec3 c = Vec3.atCenterOf(pos);
        return c.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.gameMode == null || mc.gui.screen() != null || p.isSpectator()) return;
        if (level.dimension() == Level.NETHER) return;
        if (cooldown > 0) { cooldown--; return; }
        if (p.getHealth() < minHealth.get()) return;

        Entity target = TargetFilter.nearest(mc, targetRange.get(),
                e -> TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), true));
        if (target == null) return;

        Vec3 eye = p.getEyePosition();
        Vec3 tpos = target.position();
        BlockPos tp = target.blockPosition();

        // 1) an existing anchor near the target?
        BlockPos anchor = null;
        double bestD = 1e9;
        for (int dx = -4; dx <= 4; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos b = tp.offset(dx, dy, dz);
            if (!level.getBlockState(b).is(Blocks.RESPAWN_ANCHOR)) continue;
            Vec3 c = Vec3.atCenterOf(b);
            if (c.distanceTo(eye) > placeRange.get()) continue;
            double d = c.distanceTo(tpos);
            if (d < bestD) { bestD = d; anchor = b; }
        }

        if (anchor != null) {
            BlockHitResult hit = new BlockHitResult(faceHit(anchor, Direction.UP), Direction.UP, anchor, false);
            if (charges(level.getBlockState(anchor)) == 0) {
                int slot = Compat.findHotbar(p, "glowstone");
                if (slot < 0 || !Compat.selectHotbar(p, slot)) return;
                mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
            } else {
                if (Vec3.atCenterOf(anchor).distanceTo(p.position()) < safeDist.get()) return;
                InteractionHand hand;
                if (!Ids.isItem(p.getOffhandItem(), "glowstone")) hand = InteractionHand.OFF_HAND;
                else if (!Ids.isItem(p.getMainHandItem(), "glowstone")) hand = InteractionHand.MAIN_HAND;
                else return;
                mc.gameMode.useItemOn(p, hand, hit);
            }
            cooldown = delay.getInt();
            return;
        }

        // 2) place a new anchor against a solid neighbour
        int anchorSlot = Compat.findHotbar(p, "respawn_anchor");
        if (anchorSlot < 0) return;

        BlockPos bestAir = null, bestSupport = null;
        Direction bestFace = null;
        double bestScore = 1e9;
        for (int dx = -3; dx <= 3; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos a = tp.offset(dx, dy, dz);
            if (!level.getBlockState(a).isAir()) continue;
            Vec3 c = Vec3.atCenterOf(a);
            if (c.distanceTo(eye) > placeRange.get()) continue;
            double dt = c.distanceTo(tpos);
            if (dt + 1.0 > c.distanceTo(p.position())) continue;
            if (!level.getEntities((Entity) null, new AABB(a), e -> true).isEmpty()) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = a.relative(d);
                BlockState ns = level.getBlockState(n);
                if (ns.isAir() || !ns.getFluidState().isEmpty()) continue;
                if (dt < bestScore) { bestScore = dt; bestAir = a; bestSupport = n; bestFace = d.getOpposite(); }
                break;
            }
        }
        if (bestAir == null || !Compat.selectHotbar(p, anchorSlot)) return;
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(faceHit(bestSupport, bestFace), bestFace, bestSupport, false));
        cooldown = delay.getInt();
    }
}
