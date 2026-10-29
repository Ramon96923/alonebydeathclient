package dev.utilclient.module;

import dev.utilclient.util.Compat;
import dev.utilclient.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Keeps a totem of undying in your offhand. */
public final class AutoTotemModule extends Module {
    private final NumberSetting health = num("Below HP", 20, 2, 20, 1);
    private final NumberSetting delay  = num("Delay (ticks)", 2, 0, 10, 1);
    private int cooldown;

    public AutoTotemModule() { super("AutoTotem", "Keep a totem in your offhand", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || p.isSpectator()) return;
        if (cooldown > 0) { cooldown--; return; }
        if (p.getHealth() > health.get()) return;
        if (Ids.isItem(p.getOffhandItem(), "totem_of_undying")) return;

        // inventory menu slots: 9-35 main inventory, 36-44 hotbar
        for (int i = 9; i <= 44; i++) {
            ItemStack s = p.inventoryMenu.getSlot(i).getItem();
            if (Ids.isItem(s, "totem_of_undying")) {
                if (Compat.swapWithOffhand(mc, i)) cooldown = delay.getInt();
                return;
            }
        }
    }
}
