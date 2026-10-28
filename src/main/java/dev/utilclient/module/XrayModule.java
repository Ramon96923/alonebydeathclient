package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Hides every block except the selected ores (face culling is done in BlockMixin). */
public final class XrayModule extends Module {
    private final BoolSetting coal, iron, copper, gold, redstone, lapis, diamond, emerald, debris, quartz;

    public XrayModule() {
        super("Xray", "Only render selected ores");
        Runnable reload = XrayModule::reloadWorld;
        coal     = add("Coal", true, reload);
        iron     = add("Iron", true, reload);
        copper   = add("Copper", true, reload);
        gold     = add("Gold", true, reload);
        redstone = add("Redstone", true, reload);
        lapis    = add("Lapis", true, reload);
        diamond  = add("Diamond", true, reload);
        emerald  = add("Emerald", true, reload);
        debris   = add("Ancient Debris", true, reload);
        quartz   = add("Nether Quartz", false, reload);
    }

    public boolean isTarget(BlockState s) {
        return (coal.get() && s.is(BlockTags.COAL_ORES))
            || (iron.get() && s.is(BlockTags.IRON_ORES))
            || (copper.get() && s.is(BlockTags.COPPER_ORES))
            || (gold.get() && s.is(BlockTags.GOLD_ORES))
            || (redstone.get() && s.is(BlockTags.REDSTONE_ORES))
            || (lapis.get() && s.is(BlockTags.LAPIS_ORES))
            || (diamond.get() && s.is(BlockTags.DIAMOND_ORES))
            || (emerald.get() && s.is(BlockTags.EMERALD_ORES))
            || (debris.get() && s.is(Blocks.ANCIENT_DEBRIS))
            || (quartz.get() && s.is(Blocks.NETHER_QUARTZ_ORE));
    }

    static void reloadWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
    }

    @Override protected void onEnable()  { reloadWorld(); }
    @Override protected void onDisable() { reloadWorld(); }
}
