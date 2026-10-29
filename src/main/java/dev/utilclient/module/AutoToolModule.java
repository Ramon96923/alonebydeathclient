package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Switches to the fastest tool for the block you're mining. */
public final class AutoToolModule extends Module {
    private final BoolSetting switchBack = bool("Switch back", true).desc("Return to your old slot when you stop mining");
    private final BoolSetting antiBreak = bool("Anti break", true).desc("Skip tools that are about to break");

    private int previous = -1;

    public AutoToolModule() { super("AutoTool", "Use the best tool when mining", Category.PLAYER); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean mining = mc.gui.screen() == null && mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult;
        if (!mining) {
            if (previous >= 0 && switchBack.get()) Inv.select(p, previous);
            previous = -1;
            return;
        }
        BlockState state = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
        if (state.isAir()) return;
        int best = -1;
        float bestScore = score(p.getMainHandItem(), state);
        for (int i = 0; i < 9; i++) {
            float s = score(p.getInventory().getItem(i), state);
            if (s > bestScore) { bestScore = s; best = i; }
        }
        if (best >= 0 && best != Inv.selected(p)) {
            if (previous < 0) previous = Inv.selected(p);
            Inv.select(p, best);
        }
    }

    private float score(ItemStack s, BlockState state) {
        if (antiBreak.get() && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 5) return -1f;
        float speed = s.getDestroySpeed(state);
        if (s.isCorrectToolForDrops(state)) speed += 1000f;
        return speed;
    }
}
