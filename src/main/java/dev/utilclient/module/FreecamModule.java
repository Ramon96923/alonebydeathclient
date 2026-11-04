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
        px = x; py = y; pz = z;
        lastTick = System.nanoTime();

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
        // forward along the view (pitch included when lookFly), strafe stays level
        double tx = -sy * flat * fwd + cy * side;
        double tz = cy * flat * fwd + sy * side;
        double ty = up + (lookFly.get() ? -Math.sin(rp) * fwd : 0);
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double v = speed.get() * (mc.options.keySprint.isDown() ? boost.get() : 1);
        if (len > 1e-6) { tx = tx / len * v; ty = ty / len * v; tz = tz / len * v; }

        double k = smooth.get() ? 0.35 : 1.0;
        vx += (tx - vx) * k;
        vy += (ty - vy) * k;
        vz += (tz - vz) * k;
        if (Math.abs(vx) < 1e-4 && tx == 0) vx = 0;
        if (Math.abs(vy) < 1e-4 && ty == 0) vy = 0;
        if (Math.abs(vz) < 1e-4 && tz == 0) vz = 0;
        x += vx;
        y += vy;
        z += vz;
    }
}
