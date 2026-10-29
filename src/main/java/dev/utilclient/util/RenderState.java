package dev.utilclient.util;

import net.minecraft.client.Minecraft;

/** Per-frame values captured by mixins, used to line up 2D overlays with the 3D world. */
public final class RenderState {
    /** Vertical FOV the camera used this frame (written by CameraMixin), or -1 before the first frame. */
    public static volatile float fov = -1f;

    private static int frames, fps;
    private static long fpsWindow;

    private RenderState() {}

    public static float fov() {
        if (fov > 0) return fov;
        try {
            return Minecraft.getInstance().options.fov().get();
        } catch (Throwable t) {
            return 70f;
        }
    }

    /** Counted once per HUD frame. */
    public static void frame() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - fpsWindow >= 1000L) {
            fps = frames;
            frames = 0;
            fpsWindow = now;
        }
    }

    public static int fps() { return fps; }
}
