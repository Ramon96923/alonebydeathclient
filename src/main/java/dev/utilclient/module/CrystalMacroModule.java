package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Hold right-click on obsidian: places a crystal where you look and hits it the moment it appears, as fast
 * as your delays allow. Everything goes through your real crosshair (no rotations), which is how DonutSMP
 * "legit" crystal macros work. Use AutoCrystal instead for full automation.
 */
public final class CrystalMacroModule extends Module {
    private final NumberSetting placeDelay = num("Place delay", 1, 0, 10, 1).unit("t");
    private final NumberSetting breakDelay = num("Break delay", 1, 0, 10, 1).unit("t");
    private final BoolSetting autoSwitch = bool("Switch to crystals", true).desc("Hold crystals while looking at obsidian");

    private int placeTimer, breakTimer;

    public CrystalMacroModule() { super("CrystalMacro", "Fast crystal place + break on right-click", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;
        if (mc.gameMode == null || mc.gui.screen() != null || !mc.options.keyUse.isDown()) return;

        if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal crystal) {
            if (breakTimer == 0) {
                mc.gameMode.attack(p, crystal);
                p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
                breakTimer = breakDelay.getInt();
            }
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult bh) || placeTimer > 0) return;
        BlockPos pos = bh.getBlockPos();
        var st = mc.level.getBlockState(pos);
        if (!(st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK)) || !mc.level.getBlockState(pos.above()).isAir()) return;
        InteractionHand hand = p.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (hand == InteractionHand.MAIN_HAND && !p.getMainHandItem().is(Items.END_CRYSTAL)) {
            int slot = autoSwitch.get() ? Inv.hotbar(p, Items.END_CRYSTAL) : -1;
            if (slot < 0) return;
            Inv.select(p, slot);
        }
        mc.gameMode.useItemOn(p, hand, bh);
        p.swing(hand, p.getItemInHand(hand).getInteractAnimation(), false);
        placeTimer = placeDelay.getInt();
    }
}
