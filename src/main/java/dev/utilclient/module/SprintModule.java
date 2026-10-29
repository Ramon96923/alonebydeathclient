package dev.utilclient.module;

import net.minecraft.client.Minecraft;

public final class SprintModule extends Module {
    public SprintModule() { super("Sprint", "Always sprint while moving forward", Category.PLAYER); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.gui.screen() != null) return;
        mc.options.keySprint.setDown(mc.options.keyUp.isDown());
    }

    @Override protected void onDisable() { Minecraft.getInstance().options.keySprint.setDown(false); }
}
