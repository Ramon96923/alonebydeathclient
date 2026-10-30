package dev.utilclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Movement-key helpers for the movement modules. */
public final class Moves {
    private Moves() {}

    /** True while any of W/A/S/D is held. */
    public static boolean moving(Minecraft mc) {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    /**
     * Unit horizontal vector for the held movement keys relative to {@code yawDeg}, or zero when none are held.
     * Same convention as vanilla's input vector: forward is (-sin yaw, cos yaw), strafe-left is (cos yaw, sin yaw).
     */
    public static Vec3 inputDir(Minecraft mc, float yawDeg) {
        double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        if (f == 0 && s == 0) return Vec3.ZERO;
        double yaw = Math.toRadians(yawDeg);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double x = s * cos - f * sin;
        double z = f * cos + s * sin;
        double len = Math.sqrt(x * x + z * z);
        return new Vec3(x / len, 0, z / len);
    }
}
