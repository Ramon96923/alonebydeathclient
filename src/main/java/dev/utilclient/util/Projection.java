package dev.utilclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Projects world positions to GUI-scaled screen coordinates using the player's view.
 * Assumes first person; the FOV slider value is used, so FOV effects (sprinting) cause slight drift.
 */
public final class Projection {
    private final double cx, cy, cz;
    private final double rx, rz;
    private final double fx, fy, fz;
    private final double ux, uy, uz;
    private final double tanHalf, aspect;
    public final int width, height;

    public Projection(Minecraft mc, float pt) {
        LocalPlayer p = mc.player;
        Vec3 eye = p.getEyePosition(pt);
        cx = eye.x; cy = eye.y; cz = eye.z;
        double yaw = Math.toRadians(p.getViewYRot(pt));
        double pitch = Math.toRadians(p.getViewXRot(pt));
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        width = mc.getWindow().getGuiScaledWidth();
        height = mc.getWindow().getGuiScaledHeight();
        aspect = (double) width / (double) height;
        tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
    }

    /** Returns false if the point is behind the camera. out = {x, y}. */
    public boolean project(double x, double y, double z, double[] out) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double zc = dx * fx + dy * fy + dz * fz;
        if (zc < 0.05) return false;
        double xc = dx * rx + dz * rz;
        double yc = dx * ux + dy * uy + dz * uz;
        out[0] = width / 2.0 + (xc / (zc * tanHalf * aspect)) * (width / 2.0);
        out[1] = height / 2.0 - (yc / (zc * tanHalf)) * (height / 2.0);
        return true;
    }

    /** Like project, but points behind you are pushed to the screen edge in the right direction (for tracers). */
    public void projectClamped(double x, double y, double z, double[] out) {
        if (project(x, y, z, out)) {
            out[0] = Draw.clamp((int) out[0], -100, width + 100);
            out[1] = Draw.clamp((int) out[1], -100, height + 100);
            return;
        }
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double xc = dx * rx + dz * rz;
        double yc = dx * ux + dy * uy + dz * uz;
        double len = Math.hypot(xc, yc);
        if (len < 1e-6) { out[0] = width / 2.0; out[1] = height; return; }
        out[0] = Draw.clamp((int) (width / 2.0 + xc / len * width), 0, width);
        out[1] = Draw.clamp((int) (height / 2.0 - yc / len * height), 0, height);
    }

    /** 2D bounding rectangle of a world-space box. rect = {x0, y0, x1, y1}. False if any corner is behind you. */
    public boolean boxRect(double x0, double y0, double z0, double x1, double y1, double z1, int[] rect) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        double[] o = new double[2];
        for (int i = 0; i < 8; i++) {
            double px = (i & 1) == 0 ? x0 : x1;
            double py = (i & 2) == 0 ? y0 : y1;
            double pz = (i & 4) == 0 ? z0 : z1;
            if (!project(px, py, pz, o)) return false;
            minX = Math.min(minX, o[0]); maxX = Math.max(maxX, o[0]);
            minY = Math.min(minY, o[1]); maxY = Math.max(maxY, o[1]);
        }
        rect[0] = (int) minX; rect[1] = (int) minY; rect[2] = (int) maxX; rect[3] = (int) maxY;
        return maxX - minX < width * 2 && maxY - minY < height * 2;
    }
}
