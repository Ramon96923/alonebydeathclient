package dev.utilclient.module;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/** Shared entity classification used by ESP, Tracker and AutoHit. */
public enum EntityClass {
    PLAYER, HOSTILE, PASSIVE, OTHER;

    private static final Set<String> PROTECTED_IDS = Set.of("villager", "wandering_trader", "iron_golem");

    private static String id(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
    }

    public static EntityClass of(Entity e) {
        if (e instanceof Player) return PLAYER;
        if (!(e instanceof LivingEntity)) return OTHER;
        if (id(e).equals("armor_stand")) return OTHER;
        if (e.getType().getCategory() == MobCategory.MONSTER) return HOSTILE;
        return PASSIVE;
    }

    /** Things you generally don't want auto-attacked: pets, villagers, traders, golems. */
    public static boolean isProtected(Entity e) {
        if (e instanceof TamableAnimal t && t.isTame()) return true;
        return PROTECTED_IDS.contains(id(e));
    }
}
