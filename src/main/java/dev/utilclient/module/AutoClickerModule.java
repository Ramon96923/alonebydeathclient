package dev.utilclient.module;

import dev.utilclient.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/** Clicks the attack button for you at a set CPS. Runs real left-clicks, so it respects reach and sweep. */
public final class AutoClickerModule extends Module {
    private final NumberSetting minCps = num("Min CPS", 8, 1, 20, 1);
    private final NumberSetting maxCps = num("Max CPS", 12, 1, 20, 1);
    private final ModeSetting when = mode("When", "Holding attack", "Holding attack", "Always")
            .desc("Holding attack: only while the attack key is down. Always: whenever a target is in front");
    private final BoolSetting onlyEntities = bool("Only on entities", true).desc("Don't swing at air or blocks");
    private final BoolSetting requireWeapon = bool("Only with weapon", false);

    private long nextClick;

    public AutoClickerModule() { super("AutoClicker", "Auto left-click at a set CPS", Category.COMBAT); }

    @Override protected void onEnable() { schedule(); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null || p.isSpectator()) return;
        if (requireWeapon.get() && !AutoHitModule.isWeapon(p.getMainHandItem())) return;
        if (when.is("Holding attack") && !mc.options.keyAttack.isDown()) return;
        if (onlyEntities.get() && !(mc.hitResult instanceof EntityHitResult)) return;
        if (when.is("Always") && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.MISS) return;

        if (System.currentTimeMillis() < nextClick) return;
        if (mc instanceof MinecraftAccessor acc) acc.abd$startAttack();
        schedule();
    }

    private void schedule() {
        int lo = Math.min(minCps.getInt(), maxCps.getInt()), hi = Math.max(minCps.getInt(), maxCps.getInt());
        int cps = lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
        nextClick = System.currentTimeMillis() + 1000L / Math.max(1, cps);
    }
}
