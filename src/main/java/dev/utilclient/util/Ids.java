package dev.utilclient.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Registry-path lookups ("end_crystal", "glowstone"...) so we don't depend on moved constants. */
public final class Ids {
    private Ids() {}

    public static String entity(Entity e) { return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(); }

    public static String item(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(); }

    public static boolean isItem(ItemStack s, String path) { return !s.isEmpty() && item(s).equals(path); }
}
