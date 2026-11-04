package dev.utilclient.module;

import dev.utilclient.util.CombatState;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

/** Attacks only what your crosshair is on. Works alongside AutoHit. */
public final class TriggerbotModule extends Module {
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", false);
    private final BoolSetting invisibles = bool("Invisibles", true);
    private final BoolSetting spare = bool("Spare pets", true);
    private final NumberSetting charge = num("Charge", 1.0, 0.5, 1.0, 0.05).desc("How charged the swing must be");
    private final NumberSetting minDelay = num("Min delay", 60, 0, 500, 10).unit("ms").desc("Random reaction delay, lower bound");
    private final NumberSetting maxDelay = num("Max delay", 160, 0, 500, 10).unit("ms");
    private final BoolSetting onlyCrits = bool("Only crits", false).desc("Wait until you're falling to hit");
    private final BoolSetting onlyWeapon = bool("Only with weapon", false);
    private final BoolSetting pauseOnUse = bool("Pause while using", true);

    private Entity seen;
    private long seenAt, delay, chargedAt;

    public TriggerbotModule() { super("Triggerbot", "Attack what your crosshair is on", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null || p.isSpectator()) return;
        if (pauseOnUse.get() && p.isUsingItem()) return;
        if (onlyWeapon.get() && !AutoHitModule.isWeapon(p.getMainHandItem())) return;
        if (!(mc.hitResult instanceof EntityHitResult hit)) { seen = null; return; }
        Entity e = hit.getEntity();
        if (!TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), spare.get(), invisibles.get())) {
            seen = null;
            return;
        }
        if (e != seen) {
            seen = e;
            seenAt = System.currentTimeMillis();
            int lo = Math.min(minDelay.getInt(), maxDelay.getInt()), hi = Math.max(minDelay.getInt(), maxDelay.getInt());
            // reaction times bunch around the middle, they aren't evenly spread
            double mid = (lo + hi) / 2.0;
            delay = lo == hi ? lo : Math.max(lo, Math.min(hi, (long) (mid + ThreadLocalRandom.current().nextGaussian() * (hi - lo) / 4.0)));
        }
        CombatState.target(e);
        if (System.currentTimeMillis() - seenAt < delay) return;
        if (p.getAttackStrengthScale(0.5f) < charge.getFloat()) { chargedAt = 0; return; }
        long now = System.currentTimeMillis();
        if (chargedAt == 0) chargedAt = now + (dev.utilclient.util.Human.on() ? dev.utilclient.util.Human.delay(45, 30, 0) : 0);
        if (now < chargedAt) return;
        if (!dev.utilclient.util.Human.canClick()) return;
        if (onlyCrits.get() && canCrit(p) && !(p.fallDistance > 0 && !p.onGround())) return;
        if (ModuleManager.CRITICALS.waiting()) return;   // a held-back crit hit is about to land
        mc.gameMode.attack(p, e);
        p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
        if (dev.utilclient.util.Human.on()) dev.utilclient.util.Human.clicked();
        chargedAt = 0;
    }

    private static boolean canCrit(LocalPlayer p) {
        return !p.isInWater() && !p.isInLava() && !p.onClimbable() && !p.isPassenger();
    }
}
