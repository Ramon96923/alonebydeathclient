package dev.utilclient.module;

import dev.utilclient.util.CombatState;
import dev.utilclient.util.Rotations;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Smoothly turns your view onto the best target in your field of view. Runs every frame, not every tick. */
public final class AimbotModule extends Module {
    private final NumberSetting range = num("Range", 5.0, 2.0, 12.0, 0.5).unit("m");
    private final NumberSetting fov = num("FOV", 90, 10, 360, 5).desc("Only targets within this cone");
    private final NumberSetting speed = num("Speed", 180, 30, 1080, 10).unit("°/s").desc("Max turn speed");
    private final ModeSetting smoothing = mode("Smoothing", "Ease", "Ease", "Linear", "Instant");
    private final ModeSetting aimAt = mode("Aim at", "Body", "Head", "Body", "Feet", "Closest");
    private final ModeSetting priority = mode("Priority", "Angle", "Angle", "Distance", "Health");
    private final NumberSetting predict = num("Prediction", 1, 0, 5, 1).unit("t").desc("Lead moving targets by this many ticks");
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", false);
    private final BoolSetting visibleOnly = bool("Visible only", true);
    private final BoolSetting sticky = bool("Sticky", true).desc("Stay on the current target while it's valid");
    private final BoolSetting vertical = bool("Vertical", true).desc("Also adjust pitch");
    private final BoolSetting onlyAttacking = bool("Only while attacking", false).desc("Only aim while attack is held");
    private final BoolSetting humanize = bool("Humanize", false).desc("Tiny random jitter");

    private Entity locked;
    private long lastFrame;

    public AimbotModule() { super("Aimbot", "Aim at players and mobs", Category.COMBAT); }

    @Override public String info() { return aimAt.get(); }

    @Override protected void onDisable() { locked = null; lastFrame = 0; }

    private boolean valid(LocalPlayer p, Entity e) {
        if (!TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), true)) return false;
        if (p.distanceTo(e) > range.get()) return false;
        if (visibleOnly.get() && !p.hasLineOfSight(e)) return false;
        return TargetFilter.angleTo(p, e.getBoundingBox().getCenter()) <= fov.get() / 2.0;
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (mc.gui.screen() != null || p.isSpectator()) return;
        if (onlyAttacking.get() && !mc.options.keyAttack.isDown()) return;

        Entity target = sticky.get() && locked != null && locked.isAlive() && valid(p, locked) ? locked
                : TargetFilter.best(mc, range.get() + 1, e -> valid(p, e), priority.get());
        locked = target;
        if (target == null) return;
        CombatState.target(target);

        Vec3 eye = p.getEyePosition();
        Vec3 aim = Rotations.aimPoint(target, eye, aimAt.get());
        Vec3 vel = target.getDeltaMovement();
        aim = aim.add(vel.x * predict.get(), 0, vel.z * predict.get());
        float[] want = Rotations.angles(eye, aim);
        if (humanize.get()) {
            want[0] += (float) ThreadLocalRandom.current().nextGaussian() * 0.35f;
            want[1] += (float) ThreadLocalRandom.current().nextGaussian() * 0.25f;
        }

        float dYaw = Mth.wrapDegrees(want[0] - p.getYRot());
        float dPitch = want[1] - p.getXRot();
        float maxStep = speed.getFloat() * dt;
        switch (smoothing.get()) {
            case "Instant" -> { }
            case "Linear" -> {
                dYaw = Mth.clamp(dYaw, -maxStep, maxStep);
                dPitch = Mth.clamp(dPitch, -maxStep, maxStep);
            }
            default -> {
                // ease: move a fraction of the remaining angle, capped by speed
                float k = Math.min(1f, dt * speed.getFloat() / 45f);
                dYaw = Mth.clamp(dYaw * k, -maxStep, maxStep);
                dPitch = Mth.clamp(dPitch * k, -maxStep, maxStep);
            }
        }
        p.setYRot(p.getYRot() + dYaw);
        if (vertical.get()) p.setXRot(Mth.clamp(p.getXRot() + dPitch, -90f, 90f));
    }
}
