package dev.utilclient.module;

import dev.utilclient.util.Inv;
import dev.utilclient.util.Moves;
import dev.utilclient.util.Placer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Places blocks under your feet as you walk, so you can bridge over gaps. Hold Space while still to tower up. */
public final class ScaffoldModule extends Module {
    private final ModeSetting rotate = mode("Rotate", "Packet", "None", "Packet");
    private final BoolSetting tower = bool("Tower", true).desc("Hold jump while standing still to build straight up");
    private final BoolSetting extend = bool("Extend", true).desc("Also place a support block when there's nothing to build off");

    public ScaffoldModule() { super("Scaffold", "Place blocks under you", Category.MOVEMENT); }

    private static boolean usable(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi && !(bi.getBlock() instanceof FallingBlock);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null || p.isFallFlying()) return;
        int slot = usable(p.getMainHandItem()) ? Inv.selected(p) : Inv.hotbar(p, ScaffoldModule::usable);
        if (slot < 0) return;

        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ());
        if (!mc.level.getBlockState(below).canBeReplaced()) {
            towerJump(mc, p);
            return;
        }
        List<BlockPos> plan = new ArrayList<>();
        plan.add(below);
        if (extend.get()) {   // nothing to click under us: build a neighbour first, the next tick fills below
            Vec3 dir = Moves.inputDir(mc, p.getYRot());
            if (dir.lengthSqr() > 0) plan.add(below.relative(behind(dir)));   // the block we came from
            for (Direction d : HORIZONTAL) plan.add(below.relative(d));
        }
        Placer.placeBatch(mc, plan, slot, 1, rotate.get());
        towerJump(mc, p);
    }

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /** Horizontal direction opposite to where you're moving. */
    private static Direction behind(Vec3 dir) {
        if (Math.abs(dir.x) > Math.abs(dir.z)) return dir.x > 0 ? Direction.WEST : Direction.EAST;
        return dir.z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private void towerJump(Minecraft mc, LocalPlayer p) {
        if (tower.get() && mc.options.keyJump.isDown() && !Moves.moving(mc) && p.onGround()) p.jumpFromGround();
    }
}
