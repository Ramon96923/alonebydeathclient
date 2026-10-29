package dev.utilclient.util;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Projects world positions to GUI-scaled screen coordinates from the real camera
 * (position, rotation and the FOV it rendered with), so overlays follow third person and zoom.
 * View bobbing is not modelled; overlays sway slightly while walking with it on.
 */
public final class Projection {
    private final double cx, cy, cz;
    private final double rx, rz;
    private final double fx, fy, fz;
    private final double ux, uy, uz;
    private final double tanHalf, aspect;
    public final int width, height;

    public Projection(Minecraft mc, int width, int height) {
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 eye = cam.position();
        cx = eye.x;
        cy = eye.y;
        cz = eye.z;
        double yaw = Math.toRadians(cam.yRot());
        double pitch = Math.toRadians(cam.xRot());
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        this.width = width;
        this.height = height;
        aspect = (double) width / Math.max(1, height);
        tanHalf = Math.tan(Math.toRadians(RenderState.fov()) / 2.0);
    }

    public double camX() { return cx; }
    public double camY() { return cy; }
    public double camZ() { return cz; }

    /** @return false if the point is behind the camera. out = {x, y, depth}. */
    public boolean project(double x, double y, double z, double[] out) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double zc = dx * fx + dy * fy + dz * fz;
        if (zc < 0.05) return false;
        double xc = dx * rx + dz * rz;
        double yc = dx * ux + dy * uy + dz * uz;
        out[0] = width / 2.0 + (xc / (zc * tanHalf * aspect)) * (width / 2.0);
        out[1] = height / 2.0 - (yc / (zc * tanHalf)) * (height / 2.0);
        if (out.length > 2) out[2] = zc;
        return true;
    }

    /** Like project, but points behind you are pushed to the screen edge in their direction (for tracers). */
    public void projectClamped(double x, double y, double z, double[] out) {
        if (project(x, y, z, out)) {
            out[0] = Math.max(-200, Math.min(width + 200, out[0]));
            out[1] = Math.max(-200, Math.min(height + 200, out[1]));
            return;
        }
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double xc = dx * rx + dz * rz;
        double yc = dx * ux + dy * uy + dz * uz;
        double len = Math.hypot(xc, yc);
        if (len < 1e-6) { out[0] = width / 2.0; out[1] = height; return; }
        out[0] = Math.max(0, Math.min(width, width / 2.0 + xc / len * width));
        out[1] = Math.max(0, Math.min(height, height / 2.0 - yc / len * height));
    }

    /** 2D bounding rectangle of a world box. rect = {x0, y0, x1, y1}. False if any corner is behind you. */
    public boolean boxRect(double x0, double y0, double z0, double x1, double y1, double z1, int[] rect) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        double[] o = new double[3];
        for (int i = 0; i < 8; i++) {
            double px = (i & 1) == 0 ? x0 : x1;
            double py = (i & 2) == 0 ? y0 : y1;
            double pz = (i & 4) == 0 ? z0 : z1;
            if (!project(px, py, pz, o)) return false;
            minX = Math.min(minX, o[0]);
            maxX = Math.max(maxX, o[0]);
            minY = Math.min(minY, o[1]);
            maxY = Math.max(maxY, o[1]);
        }
        rect[0] = (int) Math.floor(minX);
        rect[1] = (int) Math.floor(minY);
        rect[2] = (int) Math.ceil(maxX);
        rect[3] = (int) Math.ceil(maxY);
        return rect[2] >= 0 && rect[0] <= width && rect[3] >= 0 && rect[1] <= height
                && maxX - minX < width * 2 && maxY - minY < height * 2;
    }

    public boolean onScreen(double[] p) {
        return p[0] >= -50 && p[0] <= width + 50 && p[1] >= -50 && p[1] <= height + 50;
    }
}
