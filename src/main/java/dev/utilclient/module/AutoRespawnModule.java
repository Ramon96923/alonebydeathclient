package dev.utilclient.module;

import dev.utilclient.core.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

import java.util.Locale;

public final class AutoRespawnModule extends Module {
    private final BoolSetting deathCoords = bool("Death coords", true).desc("Note where you died in chat (only you see it)");
    private boolean handled;

    public AutoRespawnModule() { super("AutoRespawn", "Skip the death screen", Category.PLAYER); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.gui.screen() instanceof DeathScreen) {
            if (!handled && deathCoords.get()) {
                Chat.info(String.format(Locale.ROOT, "You died at %.0f %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            }
            handled = true;
            mc.player.respawn();
        } else {
            handled = false;
        }
    }
}
