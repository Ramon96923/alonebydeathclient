package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** After you pop a totem, holds a hotbar totem in your main hand too, so a second pop can't kill you. */
public final class AutoDoubleHandModule extends Module {
    public AutoDoubleHandModule() { super("AutoDoubleHand", "Hold a second totem after a pop", Category.COMBAT); }

    /** From the packet mixin when your own totem pops. */
    public void onOwnPop() {
        var p = Minecraft.getInstance().player;
        if (!isEnabled() || p == null || p.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) return;
        int slot = Inv.hotbar(p, Items.TOTEM_OF_UNDYING);
        if (slot >= 0) Inv.select(p, slot);
    }
}
