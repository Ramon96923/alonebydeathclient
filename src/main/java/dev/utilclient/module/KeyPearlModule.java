package dev.utilclient.module;

import dev.utilclient.core.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Bind it to a key: throws an ender pearl from your hotbar and goes straight back to what you held. */
public final class KeyPearlModule extends Module {
    public KeyPearlModule() { super("KeyPearl", "Throw a pearl on a key, then swap back", Category.COMBAT); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !ThrowAction.throwItem(mc, Items.ENDER_PEARL, false)) Chat.error("No ender pearls in your hotbar.");
        setEnabled(false, false);
    }
}
