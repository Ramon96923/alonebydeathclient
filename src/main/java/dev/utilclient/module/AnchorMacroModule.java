package dev.utilclient.module;

import dev.utilclient.util.Human;
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
 * Humanized: each swap gets its own tick before the click, with random gaps between steps.
 */
public final class AnchorMacroModule extends Module {
    private final NumberSetting delay = num("Step interval", 90, 0, 400, 10).unit("ms").desc("Average time between charge and explode");
    private final BoolSetting explodeWithTotem = bool("Explode holding totem", true).desc("Swap to a totem slot for the blast");

    private long next;
    private int prevSlot = -1;

    public AnchorMacroModule() { super("AnchorMacro", "Charge + explode anchors on right-click", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean holding = mc.options.keyUse.isDown() && mc.gui.screen() == null;
        if (!holding || mc.gameMode == null || mc.level.dimension() == Level.NETHER
                || !(mc.hitResult instanceof BlockHitResult bh)) {
            if (prevSlot >= 0 && !holding && Human.swapSettled()) { Inv.select(p, prevSlot); prevSlot = -1; }
            return;
        }
        if (System.currentTimeMillis() < next) return;
        var st = mc.level.getBlockState(bh.getBlockPos());
        if (!st.is(Blocks.RESPAWN_ANCHOR)) return;
        int charges = st.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
        if (prevSlot < 0) prevSlot = Inv.selected(p);

        int slot;
        if (charges == 0) {
            slot = Inv.hotbar(p, Items.GLOWSTONE);
        } else {
            // anything but glowstone detonates it
            slot = explodeWithTotem.get() ? Inv.hotbar(p, Items.TOTEM_OF_UNDYING) : -1;
            if (slot < 0) slot = Inv.hotbar(p, s -> !s.is(Items.GLOWSTONE) && !s.is(Items.RESPAWN_ANCHOR));
        }
        if (slot < 0) return;
        if (Inv.selected(p) != slot) {
            Inv.select(p, slot);
            if (Human.on()) return;   // click on a later tick
        }
        if (!Human.swapSettled() || !Human.canClick()) return;
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bh);
        p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getInteractAnimation(), false);
        if (Human.on()) Human.clicked();
        double m = delay.get();
        next = System.currentTimeMillis() + (Human.on() ? Human.delay(m, m * 0.28, m * 0.4) : (long) m);
    }

    @Override
    protected void onDisable() {
        var p = Minecraft.getInstance().player;
        if (p != null && prevSlot >= 0) Inv.select(p, prevSlot);
        prevSlot = -1;
    }
}
