package dev.utilclient.module;

import dev.utilclient.render.WorldRender;
import dev.utilclient.util.CombatState;
import dev.utilclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Visual flair, all client-side: effects when something dies, particles on your hits, a ring when you jump
 * and a spinning ring under your current target.
 */
public final class EffectsModule extends Module {
    private record Bolt(List<Vec3> points, long start) {}
    private record Circle(Vec3 pos, long start) {}

    private final ModeSetting death = mode("Death effect", "Lightning", "Lightning", "Totem", "Hearts", "Souls", "Flames", "Fireworks", "None");
    private final ModeSetting deathTargets = mode("Death of", "Players", "Players", "Everything");
    private final ModeSetting deathSound = mode("Death sound", "Bell", "Bell", "Chime", "Bass", "None");
    private final ModeSetting hit = mode("Hit particles", "Crit", "Crit", "Hearts", "Flames", "Sparkles", "None");
    private final BoolSetting jumpCircles = bool("Jump circles", true);
    private final BoolSetting targetRing = bool("Target ring", true).desc("Spinning ring under whoever you're fighting");
    private final ColorSetting color = color("Colour", 0xFFF472B6);

    private final Set<UUID> dying = new HashSet<>();
    private final List<Bolt> bolts = new ArrayList<>();
    private final List<Circle> circles = new ArrayList<>();
    private final Random rnd = new Random();
    private boolean wasOnGround = true;

    public EffectsModule() { super("Effects", "Death effects, hit particles, jump circles", Category.RENDER); }

    @Override protected void onDisable() { dying.clear(); bolts.clear(); circles.clear(); }

    private static void burst(Minecraft mc, ParticleOptions type, Vec3 at, int n, double spread, double speed, Random rnd) {
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(type, at.x + (rnd.nextDouble() - 0.5) * spread, at.y + rnd.nextDouble() * spread,
                    at.z + (rnd.nextDouble() - 0.5) * spread,
                    (rnd.nextDouble() - 0.5) * speed, rnd.nextDouble() * speed, (rnd.nextDouble() - 0.5) * speed);
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer me = mc.player;
        // deaths
        Set<UUID> now = new HashSet<>();
        for (Entity e : mc.level.getEntities(me, me.getBoundingBox().inflate(64), x -> x instanceof LivingEntity)) {
            if (deathTargets.is("Players") && !(e instanceof Player)) continue;
            LivingEntity le = (LivingEntity) e;
            if (!le.isDeadOrDying()) continue;
            now.add(e.getUUID());
            if (dying.contains(e.getUUID())) continue;
            onDeath(mc, le);
        }
        dying.retainAll(now);
        dying.addAll(now);

        // jumps
        if (jumpCircles.get()) {
            if (wasOnGround && !me.onGround() && me.getDeltaMovement().y > 0.2) circles.add(new Circle(me.position(), System.currentTimeMillis()));
            wasOnGround = me.onGround();
        }
    }

    private void onDeath(Minecraft mc, LivingEntity e) {
        Vec3 at = e.position();
        switch (death.get()) {
            case "Lightning" -> {
                List<Vec3> pts = new ArrayList<>();
                double x = at.x, z = at.z;
                for (double y = at.y + 40; y > at.y; y -= 2.5) {
                    pts.add(new Vec3(x, y, z));
                    x += (rnd.nextDouble() - 0.5) * 1.6;
                    z += (rnd.nextDouble() - 0.5) * 1.6;
                }
                pts.add(at);
                bolts.add(new Bolt(pts, System.currentTimeMillis()));
                burst(mc, ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.5, 0), 30, 1.2, 0.4, rnd);
            }
            case "Totem" -> burst(mc, ParticleTypes.TOTEM_OF_UNDYING, at.add(0, 1, 0), 60, 1.0, 0.8, rnd);
            case "Hearts" -> burst(mc, ParticleTypes.HEART, at.add(0, 1, 0), 14, 1.2, 0.2, rnd);
            case "Souls" -> burst(mc, ParticleTypes.SOUL, at.add(0, 0.5, 0), 30, 1.0, 0.15, rnd);
            case "Flames" -> burst(mc, ParticleTypes.FLAME, at.add(0, 0.5, 0), 40, 1.0, 0.25, rnd);
            case "Fireworks" -> burst(mc, ParticleTypes.FIREWORK, at.add(0, 1, 0), 60, 0.6, 0.6, rnd);
            default -> { }
        }
        var snd = switch (deathSound.get()) {
            case "Bell" -> SoundEvents.NOTE_BLOCK_BELL;
            case "Chime" -> SoundEvents.NOTE_BLOCK_CHIME;
            case "Bass" -> SoundEvents.NOTE_BLOCK_BASS;
            default -> null;
        };
        if (snd != null) mc.player.playSound(snd.value(), 1f, 0.7f);
    }

    /** From the attack mixin. */
    public void onAttack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.level == null || hit.is("None")) return;
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        ParticleOptions type = switch (hit.get()) {
            case "Hearts" -> ParticleTypes.HEART;
            case "Flames" -> ParticleTypes.FLAME;
            case "Sparkles" -> ParticleTypes.END_ROD;
            default -> ParticleTypes.CRIT;
        };
        burst(mc, type, at, hit.is("Hearts") ? 3 : 10, target.getBbWidth(), 0.25, rnd);
    }

    private static void ring(WorldRender.Ctx ctx, Vec3 c, double radius, double y, int argb, double phase) {
        int seg = 40;
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = phase + i * Math.PI * 2 / seg;
            Vec3 p = new Vec3(c.x + Math.cos(a) * radius, y, c.z + Math.sin(a) * radius);
            if (prev != null) ctx.line(prev, p, argb);
            prev = p;
        }
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        long now = System.currentTimeMillis();
        int rgb = color.get() & 0xFFFFFF;
        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            float age = (now - b.start) / 600f;
            if (age >= 1f) { it.remove(); continue; }
            int a = (int) (255 * (1 - age));
            for (int i = 1; i < b.points.size(); i++) {
                ctx.line(b.points.get(i - 1), b.points.get(i), Draw.alpha(0xE0E7FF, a));
                ctx.line(b.points.get(i - 1).add(0.06, 0, 0), b.points.get(i).add(0.06, 0, 0), Draw.alpha(rgb, a / 2));
            }
        }
        for (Iterator<Circle> it = circles.iterator(); it.hasNext(); ) {
            Circle c = it.next();
            float age = (now - c.start) / 700f;
            if (age >= 1f) { it.remove(); continue; }
            ring(ctx, c.pos, 0.3 + age * 1.4, c.pos.y + 0.02, Draw.alpha(rgb, (int) (230 * (1 - age))), 0);
        }
        if (targetRing.get()) {
            LivingEntity t = CombatState.current();
            if (t != null) {
                double phase = (now % 3000) / 3000.0 * Math.PI * 2;
                double bob = (Math.sin(now / 400.0) * 0.5 + 0.5) * t.getBbHeight();
                Vec3 c = t.getPosition(ctx.partialTick);
                ring(ctx, c, t.getBbWidth() * 0.8, c.y + bob, Draw.alpha(rgb, 230), phase);
                ring(ctx, c, t.getBbWidth() * 0.8, c.y + bob - 0.08, Draw.alpha(rgb, 90), phase);
            }
        }
    }
}
