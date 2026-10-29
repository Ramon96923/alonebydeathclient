package dev.utilclient.module;

import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Smoothly turns your view toward the best target inside your FOV cone. */
public final class AimbotModule extends Module {
    private final NumberSetting range   = num("Range", 5, 2, 10, 1);
    private final NumberSetting fov     = num("FOV", 90, 30, 360, 30);
    private final NumberSetting smooth  = num("Smooth", 5, 1, 20, 1);
    private final BoolSetting players   = add("Players", true);
    private final BoolSetting hostile   = add("Hostile mobs", true);
    private final BoolSetting passive   = add("Passive mobs", false);
    private final BoolSetting head      = add("Aim at head", false);
    private final BoolSetting visible   = add("Visible only", true);
    private final BoolSetting attacking = add("Only attacking", true);

    public AimbotModule() { super("Aimbot", "Aim at players and mobs", Category.COMBAT); }

    private Vec3 aimPoint(Entity e) { return head.get() ? e.getEyePosition() : e.getBoundingBox().getCenter(); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gui.screen() != null || p.isSpectator()) return;
        if (attacking.get() && !mc.options.keyAttack.isDown()) return;

        Vec3 eye = p.getEyePosition();
        double r = range.get();
        List<Entity> list = mc.level.getEntities(p, p.getBoundingBox().inflate(r),
                e -> TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), true));

        Entity best = null;
        double bestAng = 1e9;
        float bestYaw = 0, bestPitch = 0;
        for (Entity e : list) {
            Vec3 aim = aimPoint(e);
            if (eye.distanceTo(aim) > r) continue;
            if (visible.get() && !p.hasLineOfSight(e)) continue;
            double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            float dyaw = Mth.wrapDegrees(yaw - p.getYRot());
            float dpitch = pitch - p.getXRot();
            double off = Math.max(Math.abs(dyaw), Math.abs(dpitch));
            if (off > fov.get() / 2.0) continue;
            double score = Math.hypot(dyaw, dpitch);
            if (score < bestAng) { bestAng = score; best = e; bestYaw = yaw; bestPitch = pitch; }
        }
        if (best == null) return;

        float s = (float) smooth.get();
        p.setYRot(p.getYRot() + Mth.wrapDegrees(bestYaw - p.getYRot()) / s);
        p.setXRot(Mth.clamp(p.getXRot() + (bestPitch - p.getXRot()) / s, -90.0f, 90.0f));
    }
}
