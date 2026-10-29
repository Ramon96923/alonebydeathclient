package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SprintModule extends Module {
    private final ModeSetting mode = mode("Mode", "Legit", "Legit", "Omni")
            .desc("Legit: only forwards. Omni: sprint in every direction");
    private final BoolSetting keepSprint = bool("Ignore hunger", false).desc("Try to sprint even with low food");

    public SprintModule() { super("Sprint", "Always sprint while moving", Category.MOVEMENT); }

    @Override public String info() { return mode.get(); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gui.screen() != null || p.isShiftKeyDown() || p.isUsingItem()) return;
        if (!keepSprint.get() && p.getFoodData().getFoodLevel() <= 6 && !p.getAbilities().mayfly) return;
        boolean forward = mc.options.keyUp.isDown();
        boolean any = forward || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        if (mode.is("Legit") ? forward : any) p.setSprinting(true);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.keySprint.isDown()) mc.player.setSprinting(false);
    }
}
