package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

/** Shared "swap, throw, swap back" used by KeyPearl and KeyWindCharge. */
final class ThrowAction {
    private ThrowAction() {}

    /** @param down throw straight down (wind-charge jump) instead of where you look */
    static boolean throwItem(Minecraft mc, Item item, boolean down) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) return false;
        boolean offhand = p.getOffhandItem().is(item);
        int slot = offhand ? -1 : Inv.hotbar(p, item);
        if (!offhand && slot < 0) return false;
        int prev = Inv.selected(p);
        if (!offhand) Inv.select(p, slot);
        float pitch = p.getXRot();
        if (down) {
            p.setXRot(90f);
        }
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        mc.gameMode.useItem(p, hand);
        p.swing(hand, p.getItemInHand(hand).getInteractAnimation(), false);
        if (down) p.setXRot(pitch);
        if (!offhand) Inv.select(p, prev);
        return true;
    }
}
