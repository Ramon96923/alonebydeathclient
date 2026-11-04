package dev.utilclient.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side ("silent") rotations done the way anticheats expect. Instead of sending extra look packets
 * (which trips timer/packet-count checks), the wanted rotation is swapped into your normal movement packet
 * and your real view is restored straight after, so the camera never moves.
 * <ul>
 *   <li>Turning goes through {@link Human.Aim}: it speeds up, slows down and varies, never a fixed step.</li>
 *   <li>Every change is whole mouse steps for your sensitivity (GCD).</li>
 *   <li>When a module lets go, the server rotation glides back to where you're really looking instead of
 *       snapping there in one packet (a classic flag).</li>
 * </ul>
 * Same packet approach as Meteor's rotation system (GPL-3.0); code is original.
 */
public final class RotationManager {
    private static float yaw, pitch;
    private static int holdTicks;
    private static boolean returning;
    private static float realYaw, realPitch;
    private static boolean swapped;
    /** What the server saw in the last movement packet. */
    private static float sentYaw, sentPitch;
    private static boolean haveSent;
    private static final Human.Aim AIM = new Human.Aim();

    private RotationManager() {}

    public static boolean active() { return holdTicks > 0 || returning; }

    public static float sentYaw() { return sentYaw; }

    public static float sentPitch() { return sentPitch; }

    /**
     * Turn the server-side rotation toward {@code target} by roughly {@code maxStep} degrees at most this
     * tick (180+ = instant, used only by blatant modes) and hold it for a couple of ticks.
     */
    public static void towards(LocalPlayer p, Vec3 target, float maxStep) {
        float[] want = Rotations.angles(p.getEyePosition(), target);
        float fromYaw = active() ? yaw : (haveSent ? sentYaw : p.getYRot());
        float fromPitch = active() ? pitch : (haveSent ? sentPitch : p.getXRot());
        float dy, dp;
        if (maxStep >= 180f) {
            dy = Mth.wrapDegrees(want[0] - fromYaw);
            dp = want[1] - fromPitch;
        } else {
            float[] s = AIM.step(fromYaw, fromPitch, want[0], want[1], maxStep);
            dy = s[0];
            dp = s[1];
        }
        yaw = fromYaw + Human.snap(dy);
        pitch = Mth.clamp(fromPitch + Human.snap(dp), -90f, 90f);
        holdTicks = 3;
        returning = false;
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
        if (holdTicks <= 0 && returning) {
            // glide back to the real view, a natural step at a time
            float dy = Mth.wrapDegrees(p.getYRot() - yaw), dp = p.getXRot() - pitch;
            if (Math.sqrt(dy * dy + dp * dp) < Math.max(1.0, Human.pixel() * 3)) {
                returning = false;
                AIM.reset();
            } else {
                float[] s = AIM.step(yaw, pitch, p.getYRot(), p.getXRot(), 30f);
                yaw += Human.snap(s[0]);
                pitch = Mth.clamp(pitch + Human.snap(s[1]), -90f, 90f);
            }
        }
        if (holdTicks > 0 || returning) {
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
        if (holdTicks > 0) {
            holdTicks--;
            if (holdTicks == 0) returning = Human.on();
        }
    }
}
