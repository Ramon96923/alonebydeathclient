package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Attacks the nearest valid target in range whenever the weapon is charged. Handy for mob farms. */
public final class AutoHitModule extends Module {
    private final NumberSetting range   = num("Range", 3.0, 2.0, 6.0, 0.5);
    private final BoolSetting hostile   = add("Hostile mobs", true);
    private final BoolSetting passive   = add("Passive mobs", false);
    private final BoolSetting players   = add("Players", false);
    private final BoolSetting sparePets = add("Spare pets/villagers", true);
    private final BoolSetting cooldown  = add("Wait for cooldown", true);

    public AutoHitModule() { super("AutoHit", "Auto-attack nearby targets (farms)"); }

    private boolean valid(Entity e, LocalPlayer p) {
        if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || e.isSpectator()) return false;
        if (sparePets.get() && EntityClass.isProtected(e)) return false;
        return switch (EntityClass.of(e)) {
            case PLAYER  -> players.get();
            case HOSTILE -> hostile.get();
            case PASSIVE -> passive.get();
            case OTHER   -> false;
        };
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.gui.screen() != null || p.isSpectator()) return;
        if (cooldown.get() && p.getAttackStrengthScale(0.0f) < 1.0f) return;

        double r = range.get();
        List<Entity> near = mc.level.getEntities(p, p.getBoundingBox().inflate(r + 1.0), e -> valid(e, p));

        Entity best = null;
        double bestD = r * r;
        for (Entity e : near) {
            double d = e.getBoundingBox().distanceToSqr(p.getEyePosition());
            if (d <= bestD) { bestD = d; best = e; }
        }
        if (best != null) {
            mc.gameMode.attack(p, best);
        }
    }
}
