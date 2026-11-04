package dev.utilclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Rotations {
    private Rotations() {}

    /** {yaw, pitch} looking from {@code from} to {@code to}. */
    public static float[] angles(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new float[] {yaw, Mth.clamp(pitch, -90f, 90f)};
    }

    /**
     * Turns towards a point.
     * "Client" turns your camera, "Packet" only tells the server (your view doesn't move), "None" does nothing.
     */
    public static void face(Minecraft mc, Vec3 target, String mode) {
        LocalPlayer p = mc.player;
        if (p == null || "None".equals(mode)) return;
        float[] r = angles(p.getEyePosition(), target);
        if ("Client".equals(mode)) {
            p.setYRot(r[0]);
            p.setXRot(r[1]);
        } else {
            // no extra look packet (that trips anticheat packet/timer checks): ride on the movement packet
            RotationManager.towards(p, target, 180f);
        }
    }

    private static final Human.Aim AIM = new Human.Aim();

    /**
     * Turns your real view toward a point at up to about {@code maxStep} degrees per call, the human way
     * (see {@link Human.Aim}): accelerates, eases in, wobbles, whole mouse steps only.
     * Returns the angle still left to turn, in degrees.
     */
    public static double turnToward(LocalPlayer p, Vec3 target, float maxStep) {
        return AIM.turnView(p, target, maxStep);
    }

    /** Where to aim on an entity: "Head", "Body", "Feet" or "Closest" (nearest point of its hitbox). */
    public static Vec3 aimPoint(Entity e, Vec3 eye, String where) {
        AABB b = e.getBoundingBox();
        return switch (where) {
            case "Head" -> e.getEyePosition();
            case "Feet" -> new Vec3(e.getX(), b.minY + 0.1, e.getZ());
            case "Closest" -> new Vec3(
                    Mth.clamp(eye.x, b.minX, b.maxX),
                    Mth.clamp(eye.y, b.minY, b.maxY),
                    Mth.clamp(eye.z, b.minZ, b.maxZ));
            default -> b.getCenter();
        };
    }
}
