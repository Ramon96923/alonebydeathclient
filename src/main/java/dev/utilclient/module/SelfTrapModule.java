package dev.utilclient.module;

import dev.utilclient.util.Placer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** Walls in your own head (and optionally feet) with obsidian, so crystals can't hit your upper body. */
public final class SelfTrapModule extends Module {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final BoolSetting feet = bool("Feet too", true).desc("Also surround your feet");
    private final BoolSetting cap = bool("Cap", true).desc("Put a block above your head");
    private final NumberSetting perTick = num("Blocks per tick", 3, 1, 8, 1);
    private final ModeSetting rotate = mode("Rotate", "Packet", "None", "Packet");
    private final BoolSetting disableOnMove = bool("Disable on move", true).desc("Turn off when you leave the block");

    private BlockPos start;

    public SelfTrapModule() { super("SelfTrap", "Obsidian around your own head", Category.COMBAT); }

    @Override
    protected void onEnable() {
        LocalPlayer p = Minecraft.getInstance().player;
        start = p == null ? null : p.blockPosition();
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null) return;
        if (start == null) start = p.blockPosition();
        if (disableOnMove.get() && !p.blockPosition().equals(start)) { setEnabled(false); return; }
        int slot = Placer.blastProofSlot(p);
        if (slot < 0) return;

        BlockPos at = p.blockPosition();
        List<BlockPos> plan = new ArrayList<>();
        if (feet.get()) {
            plan.add(at.below());
            for (Direction d : HORIZONTAL) plan.add(at.relative(d));
        }
        for (Direction d : HORIZONTAL) plan.add(at.above().relative(d));
        if (cap.get()) plan.add(at.above(2));
        Placer.placeBatch(mc, plan, slot, perTick.getInt(), rotate.get());
    }
}
