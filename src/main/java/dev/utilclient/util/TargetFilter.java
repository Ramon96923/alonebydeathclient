package dev.utilclient.util;

import dev.utilclient.module.EntityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Predicate;

public final class TargetFilter {
    private TargetFilter() {}

    public static boolean accept(Entity e, LocalPlayer p, boolean players, boolean hostile, boolean passive, boolean sparePets) {
        if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || e.isSpectator()) return false;
        if (sparePets && EntityClass.isProtected(e)) return false;
        return switch (EntityClass.of(e)) {
            case PLAYER  -> players;
            case HOSTILE -> hostile;
            case PASSIVE -> passive;
            case OTHER   -> false;
        };
    }

    public static Entity nearest(Minecraft mc, double range, Predicate<Entity> test) {
        LocalPlayer p = mc.player;
        Entity best = null;
        double bestD = range * range;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(range), test)) {
            double d = e.getBoundingBox().distanceToSqr(p.getEyePosition());
            if (d <= bestD) { bestD = d; best = e; }
        }
        return best;
    }
}
