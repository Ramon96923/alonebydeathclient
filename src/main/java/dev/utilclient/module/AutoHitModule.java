package dev.utilclient.module;

import dev.utilclient.util.CombatState;
import dev.utilclient.util.Rotations;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Kill-aura style: picks targets itself and attacks them in range. Great for mob farms. */
public final class AutoHitModule extends Module {
    private final NumberSetting range = num("Range", 3.1, 2.0, 6.0, 0.1).unit("m");
    private final NumberSetting wallRange = num("Wall range", 0.0, 0.0, 6.0, 0.1).unit("m")
            .desc("Range for targets behind blocks (0 = never hit through walls)");
    private final ModeSetting priority = mode("Priority", "Distance", "Distance", "Health", "Angle");
    private final ModeSetting targetMode = mode("Target mode", "Single", "Single", "Switch")
            .desc("Single sticks to one target, Switch rotates between everything in range");
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", false);
    private final BoolSetting invisibles = bool("Invisibles", true);
    private final BoolSetting sparePets = bool("Spare pets", true).desc("Never hit tamed pets, villagers or iron golems");
    private final ModeSetting timing = mode("Timing", "Cooldown", "Cooldown", "CPS")
            .desc("Cooldown waits for full damage, CPS clicks at a set speed");
    private final NumberSetting charge = num("Charge", 1.0, 0.5, 1.0, 0.05)
            .desc("How charged the swing must be").visible(() -> timing.is("Cooldown"));
    private final NumberSetting minCps = num("Min CPS", 8, 1, 20, 1).visible(() -> timing.is("CPS"));
    private final NumberSetting maxCps = num("Max CPS", 12, 1, 20, 1).visible(() -> timing.is("CPS"));
    private final ModeSetting rotate = mode("Rotate", "Smooth", "Smooth", "Packet", "Client", "None")
            .desc("Smooth: turns your view naturally and only hits when facing the target (hardest to detect). "
                    + "Packet: instant server-side snap (easy to flag)");
    private final NumberSetting turnSpeed = num("Turn speed", 25, 5, 90, 5).unit("°/t")
            .desc("Smooth: how fast your view turns").visible(() -> rotate.is("Smooth"));
    private final NumberSetting hitAngle = num("Hit angle", 20, 5, 90, 5).unit("°")
            .desc("Smooth: only swing when the target is this close to your crosshair").visible(() -> rotate.is("Smooth"));
    private final BoolSetting needCrosshair = bool("Require crosshair", true)
            .desc("Smooth: only swing when your crosshair is really on them, like a real click").visible(() -> rotate.is("Smooth"));
    private final NumberSetting missChance = num("Miss chance", 4, 0, 30, 1).unit("%")
            .desc("Sometimes swing and miss, like a human");
    private final BoolSetting onlyWeapon = bool("Only with weapon", false);
    private final BoolSetting pauseOnUse = bool("Pause while using", true).desc("Don't attack while eating or blocking");
    private final BoolSetting swing = bool("Swing", true);

    private long nextClick, chargedAt;
    private Entity current;
    private final dev.utilclient.util.Human.Aim aim = new dev.utilclient.util.Human.Aim();
    private final dev.utilclient.util.Human.Reaction seen = new dev.utilclient.util.Human.Reaction();

    public AutoHitModule() { super("AutoHit", "Auto-attack nearby targets (kill aura)", Category.COMBAT); }

    @Override public String info() { return targetMode.get(); }

    @Override protected void onDisable() { current = null; aim.reset(); seen.clear(); }

    static boolean isWeapon(ItemStack s) {
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.SPEARS) || s.is(Items.MACE) || s.is(Items.TRIDENT);
    }

    private boolean valid(Minecraft mc, Entity e) {
        LocalPlayer p = mc.player;
        if (!TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), sparePets.get(), invisibles.get())) return false;
        double dist = Math.sqrt(e.getBoundingBox().distanceToSqr(p.getEyePosition()));
        if (dist > range.get()) return false;
        return p.hasLineOfSight(e) || dist <= wallRange.get();
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null || p.isSpectator()) return;
        if (pauseOnUse.get() && p.isUsingItem()) return;
        if (onlyWeapon.get() && !isWeapon(p.getMainHandItem())) return;

        Entity target = pick(mc);
        current = target;
        if (target == null) { aim.reset(); return; }
        CombatState.target(target);
        boolean human = dev.utilclient.util.Human.on() && !rotate.is("Packet");
        if (human && !seen.ready(target.getUUID())) return;   // notice the target first, like a person

        // Smooth: keep tracking every tick, so the view is already on target when the swing is charged
        double angleLeft = rotate.is("Smooth")
                ? aim.turnView(p, aim.point(target, target.getBoundingBox().deflate(0.1)), turnSpeed.getFloat()) : 0;
        if (!ready(p)) return;
        if (ModuleManager.CRITICALS.waiting()) return;   // a held-back crit hit is about to land
        if (angleLeft > hitAngle.get()) return;          // not facing them yet
        if (rotate.is("Smooth") && needCrosshair.get()
                && !(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() == target)) return;
        if (human && !dev.utilclient.util.Human.canClick()) return;

        Vec3 aim = Rotations.aimPoint(target, p.getEyePosition(), "Closest");
        if (!rotate.is("Smooth")) Rotations.face(mc, aim, rotate.get());
        if (ThreadLocalRandom.current().nextInt(100) < missChance.getInt()) {
            p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
            if (human) dev.utilclient.util.Human.clicked();
            scheduleNext();
            return;
        }
        mc.gameMode.attack(p, target);
        if (swing.get()) p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
        if (human) dev.utilclient.util.Human.clicked();
        chargedAt = 0;
        scheduleNext();
    }

    private Entity pick(Minecraft mc) {
        if (targetMode.is("Single") && current != null && current.isAlive() && valid(mc, current)) return current;
        double scan = Math.max(range.get(), wallRange.get()) + 1.0;
        if (targetMode.is("Switch")) {
            List<Entity> all = new ArrayList<>(mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(scan), e -> valid(mc, e)));
            if (all.isEmpty()) return null;
            all.remove(current);
            if (all.isEmpty()) return current;
            return all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }
        return TargetFilter.best(mc, scan, e -> valid(mc, e), priority.get());
    }

    private boolean ready(LocalPlayer p) {
        if (timing.is("Cooldown")) {
            if (p.getAttackStrengthScale(0.5f) < charge.getFloat()) { chargedAt = 0; return false; }
            // people never hit on the exact tick the swing finishes charging
            long now = System.currentTimeMillis();
            if (chargedAt == 0) chargedAt = now + (dev.utilclient.util.Human.on() ? dev.utilclient.util.Human.delay(45, 30, 0) : 0);
            return now >= chargedAt;
        }
        return System.currentTimeMillis() >= nextClick;
    }

    private void scheduleNext() {
        if (!timing.is("CPS")) return;
        int lo = Math.min(minCps.getInt(), maxCps.getInt()), hi = Math.max(minCps.getInt(), maxCps.getInt());
        double cps = lo == hi ? lo : lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
        nextClick = System.currentTimeMillis() + dev.utilclient.util.Human.delay(1000.0 / Math.max(1, cps));
    }
}
