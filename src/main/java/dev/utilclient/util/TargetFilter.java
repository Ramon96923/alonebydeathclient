package dev.utilclient.util;

import dev.utilclient.core.Friends;
import dev.utilclient.module.EntityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Shared target selection for every combat module. Friends are never valid targets. */
public final class TargetFilter {
    private TargetFilter() {}

    public static boolean accept(Entity e, LocalPlayer p, boolean players, boolean hostile, boolean passive, boolean sparePets) {
        return accept(e, p, players, hostile, passive, sparePets, true);
    }

    public static boolean accept(Entity e, LocalPlayer p, boolean players, boolean hostile, boolean passive,
                                 boolean sparePets, boolean invisibles) {
        if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || le.isDeadOrDying() || e.isSpectator()) return false;
        if (!invisibles && e.isInvisible()) return false;
        if (Friends.isFriend(e)) return false;
        if (dev.utilclient.module.ModuleManager.ANTI_BOT.isBot(e)) return false;
        if (sparePets && EntityClass.isProtected(e)) return false;
        return switch (EntityClass.of(e)) {
            case PLAYER -> players;
            case HOSTILE -> hostile;
            case PASSIVE -> passive;
            case OTHER -> false;
        };
    }

    public static Entity nearest(Minecraft mc, double range, Predicate<Entity> test) {
        return best(mc, range, test, "Distance");
    }

    /** @param priority "Distance", "Health" or "Angle" */
    public static Entity best(Minecraft mc, double range, Predicate<Entity> test, String priority) {
        LocalPlayer p = mc.player;
        Vec3 eye = p.getEyePosition();
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(range), test)) {
            double distSq = e.getBoundingBox().distanceToSqr(eye);
            if (distSq > range * range) continue;
            double score = switch (priority) {
                case "Health" -> e instanceof LivingEntity le ? le.getHealth() + le.getAbsorptionAmount() : 1000;
                case "Angle" -> angleTo(p, e.getBoundingBox().getCenter());
                default -> distSq;
            };
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    /** Degrees between where the player looks and the point. */
    public static double angleTo(LocalPlayer p, Vec3 point) {
        float[] r = Rotations.angles(p.getEyePosition(), point);
        double dy = Mth.wrapDegrees(r[0] - p.getYRot());
        double dp = r[1] - p.getXRot();
        return Math.sqrt(dy * dy + dp * dp);
    }
}
