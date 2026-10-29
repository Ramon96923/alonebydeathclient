package dev.utilclient.module;

import net.minecraft.client.Minecraft;

public final class AutoWalkModule extends Module {
    private final BoolSetting jumpInWater = bool("Swim up", true).desc("Hold jump in water so you don't sink");

    public AutoWalkModule() { super("AutoWalk", "Hold forward for you", Category.MOVEMENT); }

    @Override
    public void onTick(Minecraft mc) {
        boolean free = mc.gui.screen() == null;
        mc.options.keyUp.setDown(free);
        if (jumpInWater.get() && free) mc.options.keyJump.setDown(mc.player.isInWater());
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
    }
}
