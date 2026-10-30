package dev.utilclient.module;

import dev.utilclient.util.Holes;
import dev.utilclient.util.Placer;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fills the safe holes next to enemies with obsidian, so they can't hide from your crystals. */
public final class HoleFillerModule extends Module {
    private final NumberSetting enemyRange = num("Enemy range", 8, 3, 16, 1).unit("m").desc("Only fill near enemies this close");
    private final NumberSetting holeRadius = num("Hole radius", 2, 1, 4, 1).unit("b").desc("How far around the enemy to look");
    private final NumberSetting reach = num("Place reach", 4.5, 3.0, 6.0, 0.5).unit("m");
    private final NumberSetting perTick = num("Blocks per tick", 1, 1, 4, 1);
    private final ModeSetting rotate = mode("Rotate", "Packet", "None", "Packet");

    private int timer;

    public HoleFillerModule() { super("HoleFiller", "Fill holes near enemies with obsidian", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        if (timer-- > 0) return;
        timer = 1;
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null) return;
        int slot = Placer.blastProofSlot(p);
        if (slot < 0) return;
        Entity enemy = TargetFilter.nearest(mc, enemyRange.get(), e -> TargetFilter.accept(e, p, true, false, false, true));
        if (enemy == null) return;

        Vec3 eye = p.getEyePosition();
        BlockPos me = p.blockPosition();
        BlockPos center = enemy.blockPosition();
        int r = holeRadius.getInt();
        double reachSq = reach.get() * reach.get();
        List<BlockPos> holes = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
            if (pos.equals(me) || pos.equals(center)) continue;   // never fill your own hole or theirs mid-step
            if (Vec3.atCenterOf(pos).distanceToSqr(eye) > reachSq) continue;
            if (Holes.isHole(mc.level, pos)) holes.add(pos.immutable());
        }
        if (holes.isEmpty()) return;
        holes.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        Placer.placeBatch(mc, holes, slot, perTick.getInt(), rotate.get());
    }
}
