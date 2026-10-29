package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Hides every block except the selected ores (face culling is done in BlockMixin). */
public final class XrayModule extends Module {
    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", path));
    }

    private static final TagKey<Block> COAL_ORES = tag("coal_ores");
    private static final TagKey<Block> IRON_ORES = tag("iron_ores");
    private static final TagKey<Block> COPPER_ORES = tag("copper_ores");
    private static final TagKey<Block> GOLD_ORES = tag("gold_ores");
    private static final TagKey<Block> REDSTONE_ORES = tag("redstone_ores");
    private static final TagKey<Block> LAPIS_ORES = tag("lapis_ores");
    private static final TagKey<Block> DIAMOND_ORES = tag("diamond_ores");
    private static final TagKey<Block> EMERALD_ORES = tag("emerald_ores");

    private final BoolSetting coal, iron, copper, gold, redstone, lapis, diamond, emerald, debris, quartz;

    public XrayModule() {
        super("Xray", "Only render selected ores", Category.RENDER);
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
        return (coal.get() && s.is(COAL_ORES))
            || (iron.get() && s.is(IRON_ORES))
            || (copper.get() && s.is(COPPER_ORES))
            || (gold.get() && s.is(GOLD_ORES))
            || (redstone.get() && s.is(REDSTONE_ORES))
            || (lapis.get() && s.is(LAPIS_ORES))
            || (diamond.get() && s.is(DIAMOND_ORES))
            || (emerald.get() && s.is(EMERALD_ORES))
            || (debris.get() && s.is(Blocks.ANCIENT_DEBRIS))
            || (quartz.get() && s.is(Blocks.NETHER_QUARTZ_ORE));
    }

    /** Rebuilds chunk meshes. Method names are tried reflectively because they move between versions. */
    static void reloadWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.levelRenderer != null) {
            for (String n : new String[] {"allChanged", "needsUpdate"}) {
                if (tryInvoke(mc.levelRenderer, n)) return;
            }
        }
        tryInvoke(mc, "reloadResourcePacks"); // heavy last resort, same as F3+T
    }

    private static boolean tryInvoke(Object target, String name) {
        try {
            java.lang.reflect.Method m = target.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(target);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Override protected void onEnable()  { reloadWorld(); }
    @Override protected void onDisable() { reloadWorld(); }
}
