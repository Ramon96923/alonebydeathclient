package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

public final class AutoRespawnModule extends Module {
    public AutoRespawnModule() { super("AutoRespawn", "Skip the death screen", Category.PLAYER); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.gui.screen() instanceof DeathScreen) mc.player.respawn();
    }
}
