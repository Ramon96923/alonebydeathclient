package dev.utilclient.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side ("silent") rotations done the way anticheats expect. Instead of sending extra look packets
 * (which trips timer/packet-count checks), the wanted rotation is swapped into your normal movement packet
 * and your real view is restored straight after, so the camera never moves. Turning is rate-limited so the
 * server never sees an instant snap. Same approach as Meteor's rotation system (GPL-3.0); code is original.
 */
public final class RotationManager {
    private static float yaw, pitch;
    private static int holdTicks;
    private static float realYaw, realPitch;
    private static boolean swapped;
    /** What the server saw in the last movement packet. */
    private static float sentYaw, sentPitch;
    private static boolean haveSent;

    private RotationManager() {}

    public static boolean active() { return holdTicks > 0; }

    public static float sentYaw() { return sentYaw; }

    public static float sentPitch() { return sentPitch; }

    /**
     * Turn the server-side rotation toward {@code target} by at most {@code maxStep} degrees this tick
     * (180+ = instant) and hold it for a couple of ticks.
     */
    public static void towards(LocalPlayer p, Vec3 target, float maxStep) {
        float[] want = Rotations.angles(p.getEyePosition(), target);
        float fromYaw = active() ? yaw : (haveSent ? sentYaw : p.getYRot());
        float fromPitch = active() ? pitch : (haveSent ? sentPitch : p.getXRot());
        float dy = Mth.wrapDegrees(want[0] - fromYaw);
        float dp = want[1] - fromPitch;
        yaw = fromYaw + Mth.clamp(dy, -maxStep, maxStep);
        pitch = Mth.clamp(fromPitch + Mth.clamp(dp, -maxStep, maxStep), -90f, 90f);
        holdTicks = 3;
    }

    /** Degrees between the last rotation the server saw and the direction to {@code target}. */
    public static double sentAngleTo(LocalPlayer p, Vec3 target) {
        float[] want = Rotations.angles(p.getEyePosition(), target);
        float sy = haveSent ? sentYaw : p.getYRot(), sp = haveSent ? sentPitch : p.getXRot();
        double dy = Mth.wrapDegrees(want[0] - sy), dp = want[1] - sp;
        return Math.sqrt(dy * dy + dp * dp);
    }

    /** Look direction of the last rotation the server saw. */
    public static Vec3 sentLook(LocalPlayer p) {
        float y = haveSent ? sentYaw : p.getYRot(), x = haveSent ? sentPitch : p.getXRot();
        double ry = Math.toRadians(y), rx = Math.toRadians(x);
        return new Vec3(-Math.sin(ry) * Math.cos(rx), -Math.sin(rx), Math.cos(ry) * Math.cos(rx));
    }

    /** LocalPlayer.sendPosition HEAD. */
    public static void preSend(LocalPlayer p) {
        if (holdTicks > 0) {
            realYaw = p.getYRot();
            realPitch = p.getXRot();
            p.setYRot(yaw);
            p.setXRot(pitch);
            swapped = true;
        }
        sentYaw = p.getYRot();
        sentPitch = p.getXRot();
        haveSent = true;
    }

    /** LocalPlayer.sendPosition TAIL. */
    public static void postSend(LocalPlayer p) {
        if (swapped) {
            p.setYRot(realYaw);
            p.setXRot(realPitch);
            swapped = false;
        }
        if (holdTicks > 0) holdTicks--;
    }
}
