package dev.utilclient.module;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/** Shared entity classification used by ESP, Tracker and AutoHit. */
public enum EntityClass {
    PLAYER, HOSTILE, PASSIVE, OTHER;

    public static EntityClass of(Entity e) {
        if (e instanceof Player) return PLAYER;
        if (!(e instanceof LivingEntity)) return OTHER;
        if (e.getType() == EntityType.ARMOR_STAND) return OTHER;
        if (e.getType().getCategory() == MobCategory.MONSTER) return HOSTILE;
        return PASSIVE;
    }

    /** Things you generally don't want auto-attacked: pets, villagers, traders. */
    public static boolean isProtected(Entity e) {
        if (e instanceof TamableAnimal t && t.isTame()) return true;
        EntityType<?> t = e.getType();
        return t == EntityType.VILLAGER || t == EntityType.WANDERING_TRADER || t == EntityType.IRON_GOLEM;
    }
}
