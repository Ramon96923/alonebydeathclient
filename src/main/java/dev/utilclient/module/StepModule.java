package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Walk straight up blocks instead of jumping. */
public final class StepModule extends Module {
    private final NumberSetting height = num("Height", 1.0, 0.6, 2.5, 0.1).unit("b");
    private final BoolSetting sneakOff = bool("Off while sneaking", true);

    public StepModule() { super("Step", "Step up full blocks", Category.MOVEMENT); }

    @Override public String info() { return height.valueText(); }

    @Override
    public void onTick(Minecraft mc) {
        AttributeInstance a = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (a == null) return;
        double want = sneakOff.get() && mc.player.isShiftKeyDown() ? 0.6 : height.get();
        if (a.getBaseValue() != want) a.setBaseValue(want);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        AttributeInstance a = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (a != null) a.setBaseValue(0.6);
    }
}
