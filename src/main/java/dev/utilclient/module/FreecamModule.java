package dev.utilclient.module;

import dev.utilclient.core.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera so you can fly it anywhere — through walls, over bases — while your body stays put.
 * <ul>
 *   <li>WASD fly where you're looking (up/down included), Space/Shift go straight up and down.</li>
 *   <li>Hold your sprint key to go faster, scroll the mouse wheel to change speed.</li>
 *   <li>Movement eases in and out instead of starting and stopping dead, and is interpolated per frame.</li>
 * </ul>
 * Camera/input hooks follow Meteor's Freecam (GPL-3.0); the movement code is original.
 */
public final class FreecamModule extends Module {
    private final NumberSetting speed = num("Speed", 1.0, 0.05, 10.0, 0.05).unit("b/t").desc("Scroll the mouse wheel to change it in-game");
    private final BoolSetting lookFly = bool("Fly where you look", true).desc("W follows your pitch too, like a spectator");
    private final BoolSetting smooth = bool("Smooth", true).desc("Ease in and out of movement");
    private final NumberSetting boost = num("Sprint boost", 3, 1, 10, 0.5).unit("x").desc("Speed multiplier while holding sprint");
    private final BoolSetting scroll = bool("Scroll speed", true).desc("Mouse wheel changes speed while in freecam");
    private final BoolSetting hideHand = bool("Hide hand", true);
    private final BoolSetting disableOnDamage = bool("Off when hurt", true).desc("Snap back if you take damage");

    private double x, y, z, px, py, pz;
    private double vx, vy, vz;
    private float yaw, pitch;
    private float lastHealth;
    private long lastTick;

    public FreecamModule() { super("Freecam", "Fly the camera away from your body", Category.RENDER); }

    @Override
    protected void onEnable() {
        lastFrame = 0;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { setEnabled(false, false); return; }
        Vec3 eye = p.getEyePosition();
        x = px = eye.x;
        y = py = eye.y;
        z = pz = eye.z;
        vx = vy = vz = 0;
        yaw = p.getYRot();
        pitch = p.getXRot();
        lastHealth = p.getHealth();
        lastTick = System.nanoTime();
    }

    /** True while the camera should be detached (read by the mixins). */
    public boolean active() { return isEnabled() && Minecraft.getInstance().player != null; }

    public boolean hidesHand() { return active() && hideHand.get(); }

    /** Fallback interpolation when the real partial tick isn't available. */
    public float partial() { return Mth.clamp((System.nanoTime() - lastTick) / 50_000_000f, 0f, 1f); }

    public double camX(float pt) { return Mth.lerp(pt, px, x); }
    public double camY(float pt) { return Mth.lerp(pt, py, y); }
    public double camZ(float pt) { return Mth.lerp(pt, pz, z); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    /** Mouse look, from EntityMixin (same scale vanilla uses for the player). */
    public void turn(double dx, double dy) {
        yaw += (float) (dx * 0.15);
        pitch = Mth.clamp(pitch + (float) (dy * 0.15), -90f, 90f);
    }

    /** Mouse wheel, from MouseHandlerMixin. @return true if the scroll was used */
    public boolean scroll(double amount) {
        if (!active() || !scroll.get() || Minecraft.getInstance().gui.screen() != null || amount == 0) return false;
        double v = speed.get() * (amount > 0 ? 1.25 : 0.8);
        speed.set(Math.round(Mth.clamp(v, 0.05, 10.0) * 100) / 100.0);
        Chat.info("Freecam speed: " + speed.get());
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (disableOnDamage.get() && p.getHealth() < lastHealth) { setEnabled(false); return; }
        lastHealth = p.getHealth();
    }

    private long lastFrame;

    /**
     * Moves the camera, once per rendered frame, by real elapsed time. Moving per frame (not per game tick)
     * keeps it smooth even when ticks stutter from lag or low TPS.
     */
    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        if (dt <= 0) return;

        double fwd = 0, side = 0, up = 0;
        if (mc.gui.screen() == null) {
            if (mc.options.keyUp.isDown()) fwd++;
            if (mc.options.keyDown.isDown()) fwd--;
            if (mc.options.keyLeft.isDown()) side++;
            if (mc.options.keyRight.isDown()) side--;
            if (mc.options.keyJump.isDown()) up++;
            if (mc.options.keyShift.isDown()) up--;
        }
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        double cy = Math.cos(ry), sy = Math.sin(ry);
        double flat = lookFly.get() ? Math.cos(rp) : 1;
        double tx = -sy * flat * fwd + cy * side;
        double tz = cy * flat * fwd + sy * side;
        double ty = up + (lookFly.get() ? -Math.sin(rp) * fwd : 0);
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double v = speed.get() * 20 * (mc.options.keySprint.isDown() ? boost.get() : 1);   // blocks per second
        if (len > 1e-6) { tx = tx / len * v; ty = ty / len * v; tz = tz / len * v; }

        // frame-rate independent easing (same feel as before, at any FPS)
        double k = smooth.get() ? 1 - Math.exp(-dt * 9) : 1;
        vx += (tx - vx) * k;
        vy += (ty - vy) * k;
        vz += (tz - vz) * k;
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;
    }

    public double camX() { return x; }
    public double camY() { return y; }
    public double camZ() { return z; }
}
