package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.util.Inv;
import dev.utilclient.util.Placer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Surrounds your feet with obsidian (or other blast-proof blocks) so crystals can't reach you. */
public final class SurroundModule extends Module {
    private static final Item[] BLOCKS = {Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.ENDER_CHEST, Items.NETHERITE_BLOCK};

    private final NumberSetting perTick = num("Blocks per tick", 4, 1, 8, 1);
    private final BoolSetting center = bool("Center", true).desc("Snap to the middle of the block when turned on");
    private final BoolSetting floor = bool("Floor", true).desc("Also fill the block under you");
    private final BoolSetting onlyGround = bool("Only on ground", true);
    private final BoolSetting disableOnJump = bool("Disable on jump", true);
    private final BoolSetting disableOnMove = bool("Disable on move", true).desc("Turn off when you leave the block");
    private final ModeSetting rotate = mode("Rotate", "Packet", "None", "Packet");

    private BlockPos start;

    public SurroundModule() { super("Surround", "Box your feet in with obsidian", Category.COMBAT); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        start = p.blockPosition();
        if (center.get()) {
            p.setPos(Math.floor(p.getX()) + 0.5, p.getY(), Math.floor(p.getZ()) + 0.5);
            p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null) return;
        if (disableOnJump.get() && mc.options.keyJump.isDown()) { setEnabled(false); return; }
        if (start == null) start = p.blockPosition();
        if (disableOnMove.get() && !p.blockPosition().equals(start)) { setEnabled(false); return; }
        if (onlyGround.get() && !p.onGround()) return;

        int slot = -1;
        for (Item item : BLOCKS) {
            slot = Inv.hotbar(p, item);
            if (slot >= 0) break;
        }
        if (slot < 0) {
            Chat.error("Surround: no obsidian in your hotbar.");
            setEnabled(false);
            return;
        }

        List<BlockPos> targets = new ArrayList<>();
        BlockPos feet = p.blockPosition();
        if (floor.get()) targets.add(feet.below());
        for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) targets.add(feet.relative(d));

        int prev = Inv.selected(p);
        int placed = 0;
        boolean switched = false;
        for (BlockPos pos : targets) {
            if (placed >= perTick.getInt()) break;
            if (!Placer.canPlaceAt(mc, pos)) continue;
            if (!switched) { Inv.select(p, slot); switched = true; }
            if (Placer.place(mc, pos, InteractionHand.MAIN_HAND, rotate.get(), true)) placed++;
        }
        if (switched) Inv.select(p, prev);
    }
}
