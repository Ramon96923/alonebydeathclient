package dev.utilclient.module;

import dev.utilclient.render.WorldRender;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shows the flight path of whatever you're about to throw or shoot — pearls, snowballs, eggs, potions,
 * XP bottles, wind charges, tridents, bows and crossbows — and highlights what it will hit.
 */
public final class TrajectoriesModule extends Module {
    private record Shot(double speed, double gravity, double drag, float pitchOffset) {}

    private final ColorSetting color = color("Colour", 0xFF60A5FA);
    private final ColorSetting hitColor = color("Hit colour", 0xFFEF4444);
    private final BoolSetting entities = bool("Show entity hits", true);
    private final NumberSetting width = num("Line width", 4, 1, 10, 0.5).desc("Thickness of the path");
    private final ModeSetting marker = mode("Landing marker", "Plate", "Plate", "Box", "None")
            .desc("Plate: a flat square on the surface it hits (doesn't block the view). Box: a small cube");
    private final NumberSetting markerSize = num("Marker size", 0.6, 0.2, 1.2, 0.05).visible(() -> !marker.is("None"));

    public TrajectoriesModule() { super("Trajectories", "Preview where throws and arrows land", Category.RENDER); }

    private static Shot shotFor(LocalPlayer p, ItemStack s) {
        if (s.isEmpty()) return null;
        if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL)) return new Shot(1.5, 0.03, 0.99, 0);
        String id = Ids.item(s);
        if (id.equals("egg") || id.equals("blue_egg") || id.equals("brown_egg")) return new Shot(1.5, 0.03, 0.99, 0);
        if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new Shot(0.5, 0.05, 0.99, -20);
        if (s.is(Items.EXPERIENCE_BOTTLE)) return new Shot(0.7, 0.07, 0.99, -20);
        if (s.is(Items.WIND_CHARGE)) return new Shot(1.5, 0.0, 1.0, 0);
        boolean using = p.isUsingItem() && p.getUseItem() == s;
        if (s.is(Items.TRIDENT) && using) return new Shot(2.5, 0.05, 0.99, 0);
        if (s.is(Items.BOW) && using) {
            float power = BowItem.getPowerForTime(p.getTicksUsingItem());
            return power < 0.1f ? null : new Shot(power * 3.0, 0.05, 0.99, 0);
        }
        if (s.is(Items.CROSSBOW) && CrossbowItem.isCharged(s)) return new Shot(3.15, 0.05, 0.99, 0);
        return null;
    }

    /** A thin square lying on the face that was hit, lifted a hair off it so it doesn't flicker. */
    private static AABB plate(Vec3 h, net.minecraft.core.Direction face, double r) {
        double lift = 0.01, t = 0.005;
        double cx = h.x + face.getStepX() * lift, cy = h.y + face.getStepY() * lift, cz = h.z + face.getStepZ() * lift;
        double rx = face.getStepX() != 0 ? t : r, ry = face.getStepY() != 0 ? t : r, rz = face.getStepZ() != 0 ? t : r;
        return new AABB(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz);
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Shot shot = shotFor(p, p.getMainHandItem());
        if (shot == null) shot = shotFor(p, p.getOffhandItem());
        if (shot == null) return;

        double yaw = Math.toRadians(p.getYRot()), pitch = Math.toRadians(p.getXRot() + shot.pitchOffset);
        Vec3 vel = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).scale(shot.speed);
        Vec3 own = p.getDeltaMovement();
        vel = vel.add(own.x, p.onGround() ? 0 : own.y, own.z);   // throws inherit your motion
        Vec3 pos = p.getEyePosition().add(0, -0.1, 0);

        int rgb = color.get() & 0xFFFFFF;
        float w = width.getFloat();
        Vec3 eye = p.getEyePosition();
        for (int i = 0; i < 300; i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult block = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (block.getType() != HitResult.Type.MISS) next = block.getLocation();
            Entity hitEntity = null;
            if (entities.get() && i > 1) {
                AABB sweep = new AABB(pos, next).inflate(0.5);
                double best = Double.MAX_VALUE;
                for (Entity e : mc.level.getEntities(p, sweep, e -> e.isPickable() && !e.isSpectator())) {
                    var clip = e.getBoundingBox().inflate(0.3).clip(pos, next);
                    if (clip.isPresent() && clip.get().distanceToSqr(pos) < best) {
                        best = clip.get().distanceToSqr(pos);
                        hitEntity = e;
                        next = clip.get();
                    }
                }
            }
            boolean last = hitEntity != null || block.getType() != HitResult.Type.MISS;
            Vec3 end = next;
            AABB box = null;
            if (last && hitEntity == null && marker.is("Box")) {
                double r = markerSize.get() / 2;
                Vec3 h = block.getLocation();
                box = new AABB(h.x - r, h.y - r, h.z - r, h.x + r, h.y + r, h.z + r);
                // stop the line where it enters the box instead of running through it
                var entry = box.clip(pos, next);
                if (entry.isPresent()) end = entry.get();
            }
            // fade in after the first block, so the line doesn't cover your crosshair
            if (end.distanceToSqr(eye) > 1.2 * 1.2) {
                int a = Math.max(70, 235 - i * 2);
                ctx.line(pos.distanceToSqr(eye) < 1.2 * 1.2 ? pos.add(end.subtract(pos).normalize().scale(0.6)) : pos, end, Draw.alpha(rgb, a), w);
            }
            if (hitEntity != null) {
                int hr = hitColor.get() & 0xFFFFFF;
                ctx.fill(hitEntity.getBoundingBox(), Draw.alpha(hr, 40));
                ctx.outline(hitEntity.getBoundingBox(), Draw.alpha(hr, 235), Math.max(2f, w * 0.6f));
                return;
            }
            if (block.getType() != HitResult.Type.MISS) {
                int hc = hitColor.get() & 0xFFFFFF;
                if (marker.is("Box") && box != null) {
                    ctx.fill(box, Draw.alpha(hc, 45));
                    ctx.outline(box, Draw.alpha(hc, 235), Math.max(2f, w * 0.6f));
                } else if (marker.is("Plate")) {
                    AABB plate = plate(block.getLocation(), block.getDirection(), markerSize.get() / 2);
                    ctx.fill(plate, Draw.alpha(hc, 70));
                    ctx.outline(plate, Draw.alpha(hc, 240), Math.max(2f, w * 0.6f));
                }
                return;
            }
            pos = next;
            vel = vel.scale(shot.drag).add(0, -shot.gravity, 0);
            if (pos.y < -128) return;
        }
    }
}
