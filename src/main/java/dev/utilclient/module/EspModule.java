package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/** Entity ESP using vanilla's glow outline (visible through walls). Hooked in EntityMixin. */
public final class EspModule extends Module {
    private final BoolSetting players = add("Players", true);
    private final BoolSetting hostile = add("Hostile mobs", true);
    private final BoolSetting passive = add("Passive mobs", true);
    private final BoolSetting items   = add("Dropped items", false);

    private final ColorSetting playerColor  = color("Player colour", 5);
    private final ColorSetting hostileColor = color("Hostile colour", 0);
    private final ColorSetting passiveColor = color("Passive colour", 3);
    private final ColorSetting itemColor    = color("Item colour", 2);

    public EspModule() { super("ESP", "Outline entities through walls", Category.RENDER); }

    private static boolean isSelf(Entity e) { return e == Minecraft.getInstance().player; }

    public boolean shouldGlow(Entity e) {
        if (!isEnabled() || isSelf(e)) return false;
        return switch (EntityClass.of(e)) {
            case PLAYER  -> players.get();
            case HOSTILE -> hostile.get();
            case PASSIVE -> passive.get();
            case OTHER   -> e instanceof ItemEntity && items.get();
        };
    }

    public int colorOf(EntityClass c) {
        return switch (c) {
            case PLAYER  -> playerColor.rgb();
            case HOSTILE -> hostileColor.rgb();
            case PASSIVE -> passiveColor.rgb();
            case OTHER   -> itemColor.rgb();
        };
    }

    /** RGB outline colour, or -1 if this entity isn't ours. */
    public int colorFor(Entity e) {
        if (!shouldGlow(e)) return -1;
        return switch (EntityClass.of(e)) {
            case PLAYER  -> playerColor.rgb();
            case HOSTILE -> hostileColor.rgb();
            case PASSIVE -> passiveColor.rgb();
            case OTHER   -> itemColor.rgb();
        };
    }
}
