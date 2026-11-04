package dev.utilclient.module;

import dev.utilclient.core.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Bind it to a key: throws a wind charge (straight down for a jump, or where you look) and swaps back. */
public final class KeyWindChargeModule extends Module {
    private final BoolSetting down = bool("Throw down", true).desc("Aim at your feet for a wind-charge jump (mace combos)");

    public KeyWindChargeModule() { super("KeyWindCharge", "Throw a wind charge on a key", Category.COMBAT); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !ThrowAction.throwItem(mc, Items.WIND_CHARGE, down.get())) Chat.error("No wind charges in your hotbar.");
        setEnabled(false, false);
    }
}
