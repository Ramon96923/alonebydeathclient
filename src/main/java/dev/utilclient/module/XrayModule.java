package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Only renders the blocks you pick; everything else turns invisible. Ores are drawn fully lit (LightmapMixin). */
public final class XrayModule extends Module {
    private final BoolSetting coal, iron, copper, gold, redstone, lapis, diamond, emerald, debris, quartz, storage, spawners;

    private volatile Set<Block> targets = Set.of();

    public XrayModule() {
        super("Xray", "See ores through the ground", Category.RENDER);
        Runnable reload = () -> { rebuild(); reloadWorld(); };
        coal = bool("Coal", false, reload);
        iron = bool("Iron", true, reload);
        copper = bool("Copper", false, reload);
        gold = bool("Gold", true, reload);
        redstone = bool("Redstone", true, reload);
        lapis = bool("Lapis", true, reload);
        diamond = bool("Diamond", true, reload);
        emerald = bool("Emerald", true, reload);
        debris = bool("Ancient Debris", true, reload);
        quartz = bool("Nether Quartz", false, reload);
        storage = bool("Chests & storage", false, reload);
        spawners = bool("Spawners", true, reload);
        rebuild();
    }

    private void rebuild() {
        Set<Block> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Block b : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (wanted(id)) set.add(b);
        }
        targets = set;
    }

    private boolean wanted(String id) {
        if (id.endsWith("_ore")) {
            if (id.contains("coal")) return coal.get();
            if (id.contains("iron")) return iron.get();
            if (id.contains("copper")) return copper.get();
            if (id.contains("gold")) return gold.get();
            if (id.contains("redstone")) return redstone.get();
            if (id.contains("lapis")) return lapis.get();
            if (id.contains("diamond")) return diamond.get();
            if (id.contains("emerald")) return emerald.get();
            if (id.contains("quartz")) return quartz.get();
            return false;
        }
        if (id.equals("ancient_debris")) return debris.get();
        if (id.equals("spawner") || id.equals("trial_spawner")) return spawners.get();
        if (id.equals("chest") || id.equals("trapped_chest") || id.equals("ender_chest") || id.equals("barrel")
                || id.endsWith("shulker_box")) return storage.get();
        return false;
    }

    public boolean isTarget(BlockState s) { return targets.contains(s.getBlock()); }

    static void reloadWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.levelExtractor != null) mc.levelExtractor.allChanged();
    }

    @Override protected void onEnable() { rebuild(); reloadWorld(); }

    @Override
    protected void onDisable() { reloadWorld(); }
}
