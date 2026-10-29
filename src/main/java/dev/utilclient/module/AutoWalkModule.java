package dev.utilclient.module;

import net.minecraft.client.Minecraft;

public final class AutoWalkModule extends Module {
    public AutoWalkModule() { super("AutoWalk", "Hold forward for you", Category.PLAYER); }

    @Override
    public void onTick(Minecraft mc) {
        mc.options.keyUp.setDown(mc.gui.screen() == null);
    }

    @Override protected void onDisable() { Minecraft.getInstance().options.keyUp.setDown(false); }
}
