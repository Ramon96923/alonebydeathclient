package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The "legit" totem refill: while your inventory is open, puts a totem in your offhand after a short, random
 * human-like delay. Unlike AutoTotem it never moves items with the inventory closed, which anticheats watch.
 */
public final class InvTotemModule extends Module {
    private final NumberSetting minDelay = num("Min delay", 80, 0, 500, 10).unit("ms");
    private final NumberSetting maxDelay = num("Max delay", 180, 0, 800, 10).unit("ms");

    private long due;

    public InvTotemModule() { super("InvTotem", "Refill offhand totem while inventory is open", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        if (!(mc.gui.screen() instanceof InventoryScreen) || mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            due = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (due == 0) {
            int lo = Math.min(minDelay.getInt(), maxDelay.getInt()), hi = Math.max(minDelay.getInt(), maxDelay.getInt());
            due = now + (lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1));
            return;
        }
        if (now < due) return;
        int slot = Inv.menuSlot(mc.player, s -> s.is(Items.TOTEM_OF_UNDYING), true);
        if (slot >= 0) Inv.swapWithOffhand(mc, slot);
        due = 0;
    }
}
