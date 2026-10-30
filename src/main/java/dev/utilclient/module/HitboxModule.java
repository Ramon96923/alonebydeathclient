package dev.utilclient.module;

import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Slightly enlarges other entities' hitboxes so your crosshair catches them more easily. */
public final class HitboxModule extends Module {
    private final NumberSetting size = num("Size", 0.1, 0.0, 1.0, 0.05).unit("b").desc("Extra reach added around each target");
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", false);

    public HitboxModule() { super("Hitbox", "Expand targets' hitboxes", Category.COMBAT); }

    /** Added to an entity's pick radius from EntityMixin. 0 means leave it alone. */
    public float extraFor(Entity e) {
        if (!isEnabled()) return 0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0f;
        if (!TargetFilter.accept(e, mc.player, players.get(), hostile.get(), passive.get(), false)) return 0f;
        return size.getFloat();
    }
}
