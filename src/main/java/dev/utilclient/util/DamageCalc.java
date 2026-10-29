package dev.utilclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Predicts explosion damage the way the server computes it: distance falloff, how much of the
 * target's hitbox the blast can see, difficulty, armour, toughness, Resistance and Protection.
 */
public final class DamageCalc {
    public static final float CRYSTAL_POWER = 6f;
    public static final float ANCHOR_POWER = 5f;

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private DamageCalc() {}

    /** Damage from a crystal sitting on top of {@code base}. */
    public static float crystalOnBlock(LivingEntity target, BlockPos base) {
        return explosion(target, target.position(), target.getBoundingBox(),
                new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5), CRYSTAL_POWER, null);
    }

    /** Damage from an existing crystal entity at {@code pos}. */
    public static float crystal(LivingEntity target, Vec3 pos) {
        return explosion(target, target.position(), target.getBoundingBox(), pos, CRYSTAL_POWER, null);
    }

    /** Damage from a charged anchor at {@code pos} (the anchor itself doesn't shield the blast). */
    public static float anchor(LivingEntity target, BlockPos pos) {
        return explosion(target, target.position(), target.getBoundingBox(), Vec3.atCenterOf(pos), ANCHOR_POWER, pos);
    }

    public static float explosion(LivingEntity target, Vec3 targetPos, AABB box, Vec3 center, float power, BlockPos ignore) {
        double size = power * 2.0;
        double dist = targetPos.distanceTo(center) / size;
        if (dist > 1.0) return 0f;
        double seen = exposure(center, box, ignore);
        double impact = (1.0 - dist) * seen;
        float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * size + 1.0);
        return reduce(target, damage);
    }

    /** Fraction (0-1) of sample points on the box that have a clear line to the explosion. */
    public static double exposure(Vec3 center, AABB box, BlockPos ignore) {
        double sx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double sy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double sz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        if (sx <= 0 || sy <= 0 || sz <= 0) return 0;
        double ox = (1.0 - Math.floor(1.0 / sx) * sx) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / sz) * sz) / 2.0;
        int visible = 0, total = 0;
        for (double fx = 0; fx <= 1.0; fx += sx) {
            for (double fy = 0; fy <= 1.0; fy += sy) {
                for (double fz = 0; fz <= 1.0; fz += sz) {
                    Vec3 point = new Vec3(
                            box.minX + (box.maxX - box.minX) * fx + ox,
                            box.minY + (box.maxY - box.minY) * fy,
                            box.minZ + (box.maxZ - box.minZ) * fz + oz);
                    if (!blocked(point, center, ignore)) visible++;
                    total++;
                }
            }
        }
        return total == 0 ? 0 : (double) visible / total;
    }

    private record Ray(Vec3 from, Vec3 to, BlockPos ignore) {}

    private static boolean blocked(Vec3 from, Vec3 to, BlockPos ignore) {
        Minecraft mc = Minecraft.getInstance();
        BlockHitResult hit = BlockGetter.traverseBlocks(from, to, new Ray(from, to, ignore), (ray, pos) -> {
            if (ray.ignore() != null && ray.ignore().equals(pos)) return null;
            BlockState st = mc.level.getBlockState(pos);
            if (st.isAir()) return null;
            return st.getCollisionShape(mc.level, pos).clip(ray.from(), ray.to(), pos);
        }, ray -> null);
        return hit != null;
    }

    /** Applies difficulty, armour, Resistance and Protection to raw explosion damage. */
    public static float reduce(LivingEntity target, float damage) {
        Minecraft mc = Minecraft.getInstance();
        DamageSource source = mc.level.damageSources().explosion(null);
        if (source.scalesWithDifficulty()) {
            switch (mc.level.getDifficulty()) {
                case PEACEFUL -> damage = 0f;
                case EASY -> damage = Math.min(damage / 2f + 1f, damage);
                case HARD -> damage *= 1.5f;
                default -> { }
            }
        }
        float armor = (float) Math.floor(target.getAttributeValue(Attributes.ARMOR));
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        damage = CombatRules.getDamageAfterAbsorb(target, damage, source, armor, toughness);

        MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) damage *= Math.max(0f, 1f - (resistance.getAmplifier() + 1) * 0.2f);

        int epf = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            for (var entry : stack.getEnchantments().entrySet()) {
                if (entry.getKey().is(Enchantments.PROTECTION)) epf += entry.getIntValue();
                else if (entry.getKey().is(Enchantments.BLAST_PROTECTION)) epf += 2 * entry.getIntValue();
            }
        }
        damage = CombatRules.getDamageAfterMagicAbsorb(damage, epf);
        return Math.max(0f, damage);
    }

    /** Health including absorption hearts. */
    public static float totalHealth(LivingEntity e) { return e.getHealth() + e.getAbsorptionAmount(); }
}
