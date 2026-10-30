package dev.utilclient.module;

import dev.utilclient.util.Moves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera so you can fly it around freely — look over walls, scout bases — while your real
 * player stands still. WASD moves, Space/Shift go up and down, the mouse looks. Your body stays where it
 * was (and visible to you). Hooks follow Meteor's Freecam (GPL-3.0): Camera.alignWithEntity, Entity.turn and
 * KeyboardInput.tick.
 */
public final class FreecamModule extends Module {
    private final NumberSetting speed = num("Speed", 1.0, 0.1, 5.0, 0.1).unit("b/t");
    private final BoolSetting disableOnDamage = bool("Off when hurt", true).desc("Snap back if you take damage");

    private double x, y, z, px, py, pz;
    private float yaw, pitch;
    private float lastHealth;
    private long lastTick;

    /** Fraction of the way between the last two ticks, for smooth camera motion. */
    public float partial() { return Mth.clamp((System.nanoTime() - lastTick) / 50_000_000f, 0f, 1f); }

    public FreecamModule() { super("Freecam", "Fly the camera away from your body", Category.RENDER); }

    @Override
    protected void onEnable() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) { setEnabled(false, false); return; }
        Vec3 eye = p.getEyePosition();
        x = px = eye.x;
        y = py = eye.y;
        z = pz = eye.z;
        yaw = p.getYRot();
        pitch = p.getXRot();
        lastHealth = p.getHealth();
    }

    /** True while the camera should be detached (read by the mixins). */
    public boolean active() { return isEnabled() && Minecraft.getInstance().player != null; }

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

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (disableOnDamage.get() && p.getHealth() < lastHealth) { setEnabled(false); return; }
        lastHealth = p.getHealth();
        px = x; py = y; pz = z;
        lastTick = System.nanoTime();
        if (mc.gui.screen() != null) return;
        Vec3 dir = Moves.inputDir(mc, yaw);
        double v = speed.get();
        x += dir.x * v;
        z += dir.z * v;
        if (mc.options.keyJump.isDown()) y += v;
        if (mc.options.keyShift.isDown()) y -= v;
    }
}
