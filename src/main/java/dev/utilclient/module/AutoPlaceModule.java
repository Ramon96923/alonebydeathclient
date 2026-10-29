package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

/** Right-clicks for you at a set interval: places blocks, uses items. (Vanilla enforces a 4-tick minimum.) */
public final class AutoPlaceModule extends Module {
    private final NumberSetting delay = num("Delay (ticks)", 4, 1, 20, 1);
    private final BoolSetting onlyBlocks = add("Only when aiming at a block", true);
    private int counter;

    public AutoPlaceModule() { super("AutoPlace", "Auto right-click / place blocks"); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.gui.screen() != null) { mc.options.keyUse.setDown(false); return; }
        boolean aimingAtBlock = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        if (onlyBlocks.get() && !aimingAtBlock) { mc.options.keyUse.setDown(false); counter = 0; return; }

        counter++;
        if (counter >= delay.getInt()) {
            mc.options.keyUse.setDown(true);
            counter = 0;
        } else {
            mc.options.keyUse.setDown(false);
        }
    }

    @Override protected void onDisable() {
        counter = 0;
        Minecraft.getInstance().options.keyUse.setDown(false);
    }
}
