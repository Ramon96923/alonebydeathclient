package dev.utilclient.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 1x1 "safe holes" for crystal PvP: two air blocks to stand in, blast-proof on all four sides and below. */
public final class Holes {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};

    private Holes() {}

    /** True if a player could stand at {@code pos} fully enclosed by bedrock/obsidian-class blocks. */
    public static boolean isHole(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return false;
        for (Direction d : SIDES) {
            BlockState st = level.getBlockState(pos.relative(d));
            if (st.is(Blocks.BEDROCK)) continue;
            if (st.getBlock().getExplosionResistance() < 600) return false;
        }
        return true;
    }
}
