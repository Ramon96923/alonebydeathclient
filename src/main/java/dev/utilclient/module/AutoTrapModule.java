package dev.utilclient.module;

import dev.utilclient.util.Placer;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** Boxes the nearest enemy player in with obsidian so they can't escape your crystals. */
public final class AutoTrapModule extends Module {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final ModeSetting shape = mode("Shape", "Full", "Full", "Head", "Top")
            .desc("Full: feet + head + cap. Head: head level + cap. Top: just the cap");
    private final NumberSetting range = num("Range", 4.5, 2.0, 6.0, 0.5).unit("m");
    private final NumberSetting perTick = num("Blocks per tick", 2, 1, 8, 1);
    private final ModeSetting rotate = mode("Rotate", "Packet", "None", "Packet");

    public AutoTrapModule() { super("AutoTrap", "Trap the nearest enemy in obsidian", Category.COMBAT); }

    @Override public String info() { return shape.get(); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null) return;
        int slot = Placer.blastProofSlot(p);
        if (slot < 0) return;
        Entity target = TargetFilter.nearest(mc, range.get(), e -> TargetFilter.accept(e, p, true, false, false, true));
        if (target == null) return;

        BlockPos feet = target.blockPosition();
        List<BlockPos> plan = new ArrayList<>();
        if (shape.is("Full")) for (Direction d : HORIZONTAL) plan.add(feet.relative(d));
        if (!shape.is("Top")) for (Direction d : HORIZONTAL) plan.add(feet.above().relative(d));
        // the cap needs something to sit on: if the head ring is empty, start it from one side
        if (shape.is("Top")) plan.add(feet.above().relative(Direction.NORTH));
        plan.add(feet.above(2));
        Placer.placeBatch(mc, plan, slot, perTick.getInt(), rotate.get());
    }
}
