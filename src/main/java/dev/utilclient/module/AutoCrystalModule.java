package dev.utilclient.module;

import dev.utilclient.gui.Fonts;
import dev.utilclient.util.CombatState;
import dev.utilclient.util.DamageCalc;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Inv;
import dev.utilclient.util.Projection;
import dev.utilclient.util.Rotations;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Crystal aura. Every candidate spot is scored with a real explosion-damage prediction (distance,
 * line of sight to the hitbox, armour, Protection, Resistance, difficulty) for the target and for you.
 */
public final class AutoCrystalModule extends Module {
    // targeting
    private final NumberSetting targetRange = num("Target range", 10, 4, 16, 1).unit("m");
    private final ModeSetting priority = mode("Priority", "Distance", "Distance", "Health");
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", false);
    // damage
    private final NumberSetting minDamage = num("Min damage", 6, 0, 36, 0.5).desc("Least damage a crystal must deal to the target");
    private final NumberSetting maxSelf = num("Max self damage", 8, 0, 36, 0.5).desc("Most damage you'll accept");
    private final BoolSetting antiSuicide = bool("Anti suicide", true).desc("Never place/break a crystal that could kill you");
    private final NumberSetting facePlace = num("Face place HP", 8, 0, 36, 1)
            .desc("Below this target HP, accept tiny damage to finish them");
    // place
    private final BoolSetting place = bool("Place", true);
    private final NumberSetting placeRange = num("Place range", 4.5, 1, 6, 0.1).unit("m").visible(place::get);
    private final NumberSetting placeWallRange = num("Place wall range", 3.0, 0, 6, 0.1).unit("m").visible(place::get);
    private final NumberSetting placeDelay = num("Place delay", 1, 0, 10, 1).unit("t").visible(place::get);
    private final BoolSetting oldPlace = bool("1.12 placement", false)
            .desc("Require 2 air blocks above (old servers)").visible(place::get);
    private final ModeSetting switchMode = mode("Switch", "Silent", "None", "Normal", "Silent")
            .desc("Silent swaps to crystals only for the placement").visible(place::get);
    // break
    private final BoolSetting breakCrystals = bool("Break", true);
    private final NumberSetting breakRange = num("Break range", 4.5, 1, 6, 0.1).unit("m").visible(breakCrystals::get);
    private final NumberSetting breakDelay = num("Break delay", 0, 0, 10, 1).unit("t").visible(breakCrystals::get);
    private final BoolSetting breakAny = bool("Break any", false)
            .desc("Break crystals you didn't place too (still damage-checked)").visible(breakCrystals::get);
    // misc
    private final ModeSetting rotate = mode("Rotate", "Packet", "None", "Packet");
    private final BoolSetting pauseOnEat = bool("Pause while eating", true);
    private final NumberSetting pauseHealth = num("Pause below HP", 0, 0, 36, 1).desc("0 = never pause");
    private final BoolSetting render = bool("Render", true);
    private final ColorSetting renderColor = color("Render colour", 0x80F43F5E).visible(render::get);
    private final BoolSetting renderDamage = bool("Show damage", true).visible(render::get);

    private int placeTimer, breakTimer;
    private BlockPos lastPlace;
    private float lastDamage;
    private long lastPlaceTime;
    private final java.util.Set<BlockPos> placedByUs = new java.util.HashSet<>();

    public AutoCrystalModule() { super("AutoCrystal", "Place & break end crystals with damage prediction", Category.COMBAT); }

    @Override
    public String info() {
        LivingEntity t = CombatState.current();
        return t != null && lastPlace != null ? String.format(Locale.ROOT, "%.1f", lastDamage) : null;
    }

    @Override
    protected void onDisable() {
        lastPlace = null;
        placedByUs.clear();
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || p.isSpectator()) return;
        if (mc.gui.screen() != null && !Inv.canClick(mc)) return;
        if (pauseOnEat.get() && p.isUsingItem()) return;
        if (pauseHealth.get() > 0 && DamageCalc.totalHealth(p) <= pauseHealth.get()) return;

        LivingEntity target = (LivingEntity) TargetFilter.best(mc, targetRange.get(),
                e -> TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), true), priority.get());
        if (target == null) {
            if (System.currentTimeMillis() - lastPlaceTime > 1000) lastPlace = null;
            return;
        }
        CombatState.target(target);

        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;
        if (breakCrystals.get() && breakTimer <= 0 && doBreak(mc, p, target)) breakTimer = breakDelay.getInt();
        if (place.get() && placeTimer <= 0 && doPlace(mc, p, target)) placeTimer = placeDelay.getInt();
    }

    private float needed(LivingEntity target) {
        float hp = DamageCalc.totalHealth(target);
        if (facePlace.get() > 0 && hp <= facePlace.get()) return Math.min(minDamage.getFloat(), 1.5f);
        return minDamage.getFloat();
    }

    private boolean safeForMe(LocalPlayer p, float self) {
        if (self > maxSelf.get()) return false;
        return !antiSuicide.get() || self < DamageCalc.totalHealth(p) - 1f;
    }

    // ---- break --------------------------------------------------------------------------------

    private boolean doBreak(Minecraft mc, LocalPlayer p, LivingEntity target) {
        double r = breakRange.get();
        Vec3 eye = p.getEyePosition();
        Entity best = null;
        float bestDmg = 0;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(r + 2), x -> x instanceof EndCrystal && x.isAlive())) {
            if (e.getBoundingBox().distanceToSqr(eye) > r * r) continue;
            if (!breakAny.get() && !placedByUs.contains(e.blockPosition().below()) && !placedByUs.isEmpty()) {
                // only skip foreign crystals once we've started placing our own
                continue;
            }
            float dmg = DamageCalc.crystal(target, e.position());
            float self = DamageCalc.crystal(p, e.position());
            if (!safeForMe(p, self)) continue;
            if (dmg < needed(target) && dmg < DamageCalc.totalHealth(target)) continue;
            if (dmg > bestDmg) { bestDmg = dmg; best = e; }
        }
        if (best == null) return false;
        Rotations.face(mc, best.position(), rotate.get());
        mc.gameMode.attack(p, best);
        p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
        placedByUs.remove(best.blockPosition().below());
        return true;
    }

    // ---- place --------------------------------------------------------------------------------

    private boolean doPlace(Minecraft mc, LocalPlayer p, LivingEntity target) {
        InteractionHand hand = null;
        int crystalSlot = -1;
        if (p.getOffhandItem().is(Items.END_CRYSTAL)) hand = InteractionHand.OFF_HAND;
        else if (p.getMainHandItem().is(Items.END_CRYSTAL)) hand = InteractionHand.MAIN_HAND;
        else if (!switchMode.is("None")) {
            crystalSlot = Inv.hotbar(p, Items.END_CRYSTAL);
            if (crystalSlot >= 0) hand = InteractionHand.MAIN_HAND;
        }
        if (hand == null) return false;

        BlockPos best = null;
        float bestDmg = 0;
        Vec3 eye = p.getEyePosition();
        int r = (int) Math.ceil(placeRange.get());
        BlockPos center = p.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        float need = needed(target);
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
            if (!canCrystalGo(mc, m)) continue;
            Vec3 top = new Vec3(m.getX() + 0.5, m.getY() + 1.0, m.getZ() + 0.5);
            double dist = eye.distanceTo(top);
            if (dist > placeRange.get()) continue;
            if (dist > placeWallRange.get() && !visible(mc, p, eye, top, m)) continue;
            if (target.position().distanceTo(top) > 12) continue;
            float dmg = DamageCalc.crystalOnBlock(target, m);
            if (dmg < need && dmg < DamageCalc.totalHealth(target)) continue;
            if (dmg <= bestDmg) continue;
            if (!safeForMe(p, DamageCalc.crystalOnBlock(p, m))) continue;
            bestDmg = dmg;
            best = m.immutable();
        }
        if (best == null) return false;

        int prev = Inv.selected(p);
        if (crystalSlot >= 0) Inv.select(p, crystalSlot);
        Vec3 hit = new Vec3(best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5);
        Rotations.face(mc, hit, rotate.get());
        mc.gameMode.useItemOn(p, hand, new BlockHitResult(hit, Direction.UP, best, false));
        p.swing(hand, p.getItemInHand(hand).getInteractAnimation(), false);
        if (crystalSlot >= 0 && switchMode.is("Silent")) Inv.select(p, prev);

        placedByUs.add(best);
        if (placedByUs.size() > 64) placedByUs.clear();
        lastPlace = best;
        lastDamage = bestDmg;
        lastPlaceTime = System.currentTimeMillis();
        return true;
    }

    private boolean canCrystalGo(Minecraft mc, BlockPos base) {
        BlockState st = mc.level.getBlockState(base);
        if (!st.is(Blocks.OBSIDIAN) && !st.is(Blocks.BEDROCK)) return false;
        BlockPos up = base.above();
        if (!mc.level.getBlockState(up).isAir()) return false;
        if (oldPlace.get() && !mc.level.getBlockState(up.above()).isAir()) return false;
        AABB box = new AABB(up.getX(), up.getY(), up.getZ(), up.getX() + 1.0, up.getY() + 2.0, up.getZ() + 1.0);
        return mc.level.getEntities((Entity) null, box, Entity::isAlive).isEmpty();
    }

    private static boolean visible(Minecraft mc, LocalPlayer p, Vec3 eye, Vec3 point, BlockPos base) {
        BlockHitResult r = mc.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return r.getType() == HitResult.Type.MISS || r.getBlockPos().equals(base);
    }

    // ---- render -------------------------------------------------------------------------------

    @Override
    public void renderHud(GuiGraphicsExtractor g, float partialTick) {
        if (!render.get() || lastPlace == null || System.currentTimeMillis() - lastPlaceTime > 1500) return;
        Minecraft mc = Minecraft.getInstance();
        Projection pr = new Projection(mc, g.guiWidth(), g.guiHeight());
        int[] rect = new int[4];
        BlockPos b = lastPlace;
        if (!pr.boxRect(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1, rect)) return;
        int c = renderColor.get();
        g.fill(rect[0], rect[1], rect[2], rect[3], Draw.withAlpha(c, 0.45f));
        Draw.outline(g, rect[0], rect[1], rect[2], rect[3], c | 0xFF000000);
        if (renderDamage.get()) {
            String s = String.format(Locale.ROOT, "%.1f", lastDamage);
            Fonts.centered(g, s, (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2 - 4, 0xFFFFFFFF, true);
        }
    }
}
