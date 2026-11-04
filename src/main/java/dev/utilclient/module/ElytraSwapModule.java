package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.util.Ids;
import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Bind it to a key: swaps your chestplate for an elytra and back (mace/elytra fights, quick escapes). */
public final class ElytraSwapModule extends Module {
    private static final int CHEST_SLOT = 6;   // inventory-menu slot of the chest armour

    public ElytraSwapModule() { super("ElytraSwap", "Swap chestplate and elytra on a key", Category.PLAYER); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        setEnabled(false, false);
        if (p == null || !Inv.canClick(mc)) return;
        boolean wearingElytra = p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        int from = wearingElytra
                ? Inv.menuSlot(p, s -> Ids.item(s).endsWith("_chestplate"), true)
                : Inv.menuSlot(p, s -> s.is(Items.ELYTRA), true);
        if (from < 0) {
            Chat.error(wearingElytra ? "No chestplate in your inventory." : "No elytra in your inventory.");
            return;
        }
        Inv.pickup(mc, from);
        Inv.pickup(mc, CHEST_SLOT);
        ItemStack carried = p.inventoryMenu.getCarried();
        if (!carried.isEmpty()) Inv.pickup(mc, from);   // put the old piece where the new one was
    }
}
