package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

/** Holds the attack key for you so you keep mining whatever your crosshair is on. */
public final class AutoMineModule extends Module {
    private final BoolSetting onlyBlocks = add("Only on blocks", true);

    public AutoMineModule() { super("AutoMine", "Hold left click automatically", Category.PLAYER); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.gui.screen() != null) { mc.options.keyAttack.setDown(false); return; }
        boolean aimingAtBlock = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        mc.options.keyAttack.setDown(!onlyBlocks.get() || aimingAtBlock);
    }

    @Override protected void onDisable() { Minecraft.getInstance().options.keyAttack.setDown(false); }
}
