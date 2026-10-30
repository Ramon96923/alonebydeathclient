package dev.utilclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places the held block at a position by clicking a solid neighbour, like a player would. */
public final class Placer {
    private Placer() {}

    public static boolean canPlaceAt(Minecraft mc, BlockPos pos) {
        BlockState st = mc.level.getBlockState(pos);
        if (!st.canBeReplaced()) return false;
        if (!mc.level.getEntities((Entity) null, new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0), Placer::blocks).isEmpty()) return false;
        return neighbour(mc, pos) != null;
    }

    /** Entities that stop a block being placed (items and XP orbs don't). */
    public static boolean blocks(Entity e) {
        return e.isAlive() && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb);
    }

    /** Solid neighbour to click, or null. */
    public static Direction neighbour(Minecraft mc, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState ns = mc.level.getBlockState(pos.relative(d));
            if (!ns.isAir() && !ns.canBeReplaced() && ns.getFluidState().isEmpty()) return d;
        }
        return null;
    }

    public static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    /**
     * @param rotate "None", "Packet" or "Client" (see {@link Rotations#face})
     * @return true if a placement was sent
     */
    public static boolean place(Minecraft mc, BlockPos pos, InteractionHand hand, String rotate, boolean swing) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || !mc.level.getBlockState(pos).canBeReplaced()) return false;
        Direction d = neighbour(mc, pos);
        if (d == null) return false;
        BlockPos support = pos.relative(d);
        Direction face = d.getOpposite();
        Vec3 hit = faceCenter(support, face);
        Rotations.face(mc, hit, rotate);
        mc.gameMode.useItemOn(p, hand, new BlockHitResult(hit, face, support, false));
        if (swing) p.swing(hand, p.getItemInHand(hand).getInteractAnimation(), false);
        return true;
    }

    /** Blast-proof blocks for traps, surround and hole filling, best first. */
    public static final net.minecraft.world.item.Item[] BLAST_PROOF = {
            net.minecraft.world.item.Items.OBSIDIAN, net.minecraft.world.item.Items.CRYING_OBSIDIAN,
            net.minecraft.world.item.Items.ENDER_CHEST, net.minecraft.world.item.Items.NETHERITE_BLOCK};

    /** Hotbar slot of the first blast-proof block, or -1. */
    public static int blastProofSlot(LocalPlayer p) {
        for (net.minecraft.world.item.Item item : BLAST_PROOF) {
            int s = Inv.hotbar(p, item);
            if (s >= 0) return s;
        }
        return -1;
    }

    /**
     * Places up to {@code max} of the targets (in order) with the block in hotbar {@code slot}, switching to
     * it for the placements and straight back. Earlier placements count as support for later ones.
     * @return how many blocks were placed
     */
    public static int placeBatch(Minecraft mc, java.util.List<BlockPos> targets, int slot, int max, String rotate) {
        LocalPlayer p = mc.player;
        if (p == null || slot < 0) return 0;
        int prev = Inv.selected(p);
        int placed = 0;
        boolean switched = false;
        for (BlockPos pos : targets) {
            if (placed >= max) break;
            if (!canPlaceAt(mc, pos)) continue;
            if (!switched) { Inv.select(p, slot); switched = true; }
            if (place(mc, pos, InteractionHand.MAIN_HAND, rotate, true)) placed++;
        }
        if (switched) Inv.select(p, prev);
        return placed;
    }

    /** Right-clicks a block face (anchors, chests...). */
    public static void interact(Minecraft mc, BlockPos pos, Direction face, InteractionHand hand, String rotate) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) return;
        Vec3 hit = faceCenter(pos, face);
        Rotations.face(mc, hit, rotate);
        mc.gameMode.useItemOn(p, hand, new BlockHitResult(hit, face, pos, false));
        p.swing(hand, p.getItemInHand(hand).getInteractAnimation(), false);
    }
}
