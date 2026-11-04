package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Hold right-click on a respawn anchor: charges it with glowstone, swaps off the glowstone and blows it up,
 * then goes back to your weapon. Uses your crosshair only. Doesn't work in the Nether (anchors don't explode).
 */
public final class AnchorMacroModule extends Module {
    private final NumberSetting delay = num("Delay", 1, 0, 10, 1).unit("t").desc("Ticks between charge and explode");
    private final BoolSetting explodeWithTotem = bool("Explode holding totem", true).desc("Swap to a totem slot for the blast");

    private int timer, prevSlot = -1;

    public AnchorMacroModule() { super("AnchorMacro", "Charge + explode anchors on right-click", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (timer > 0) { timer--; return; }
        boolean holding = mc.options.keyUse.isDown() && mc.gui.screen() == null;
        if (!holding || mc.gameMode == null || mc.level.dimension() == Level.NETHER
                || !(mc.hitResult instanceof BlockHitResult bh)) {
            if (prevSlot >= 0 && !holding) { Inv.select(p, prevSlot); prevSlot = -1; }
            return;
        }
        var st = mc.level.getBlockState(bh.getBlockPos());
        if (!st.is(Blocks.RESPAWN_ANCHOR)) return;
        int charges = st.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
        if (prevSlot < 0) prevSlot = Inv.selected(p);

        if (charges == 0) {
            int glow = Inv.hotbar(p, Items.GLOWSTONE);
            if (glow < 0) return;
            Inv.select(p, glow);
        } else {
            // anything but glowstone detonates it
            int slot = explodeWithTotem.get() ? Inv.hotbar(p, Items.TOTEM_OF_UNDYING) : -1;
            if (slot < 0) slot = Inv.hotbar(p, s -> !s.is(Items.GLOWSTONE) && !s.is(Items.RESPAWN_ANCHOR));
            if (slot < 0) return;
            Inv.select(p, slot);
        }
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
        p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getInteractAnimation(), false);
        timer = delay.getInt();
    }

    @Override
    protected void onDisable() {
        var p = Minecraft.getInstance().player;
        if (p != null && prevSlot >= 0) Inv.select(p, prevSlot);
        prevSlot = -1;
    }
}
