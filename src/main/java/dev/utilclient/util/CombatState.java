package dev.utilclient.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Remembers who the combat modules are fighting, for the Target HUD and Criticals. */
public final class CombatState {
    private static Entity target;
    private static long time;

    private CombatState() {}

    public static void target(Entity e) {
        if (e == null) return;
        target = e;
        time = System.currentTimeMillis();
    }

    /** The current target if it was set in the last 3 seconds and is still alive. */
    public static LivingEntity current() {
        if (target == null || System.currentTimeMillis() - time > 3000L) return null;
        if (!(target instanceof LivingEntity le) || !le.isAlive() || target.isRemoved()) return null;
        return le;
    }

    public static boolean isTarget(Entity e) { return e != null && e == current(); }
}
