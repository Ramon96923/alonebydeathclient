package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.mixin.ChunkAccessAccessor;
import dev.utilclient.render.WorldRender;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base finding: outlines every chest, barrel, shulker, ender chest, hopper, furnace and dispenser in loaded
 * chunks (uses the chunk's own block-entity list, so it's instant and covers your whole render distance).
 * Stash finder warns when a chunk holds lots of storage — usually someone's base or stash.
 */
public final class StorageEspModule extends Module {
    private record Box(AABB box, int rgb) {}

    private final BoolSetting chests = bool("Chests", true);
    private final ColorSetting chestColor = color("Chest colour", 0xFFFFA62B).visible(chests::get);
    private final BoolSetting barrels = bool("Barrels", true);
    private final ColorSetting barrelColor = color("Barrel colour", 0xFFC08040).visible(barrels::get);
    private final BoolSetting shulkers = bool("Shulkers", true);
    private final ColorSetting shulkerColor = color("Shulker colour", 0xFFF472B6).visible(shulkers::get);
    private final BoolSetting enderChests = bool("Ender chests", true);
    private final ColorSetting enderColor = color("Ender colour", 0xFFA855F7).visible(enderChests::get);
    private final BoolSetting hoppers = bool("Hoppers", false);
    private final BoolSetting furnaces = bool("Furnaces", false);
    private final BoolSetting dispensers = bool("Dispensers", false);
    private final ColorSetting otherColor = color("Other colour", 0xFF9CA3AF);
    private final NumberSetting fill = num("Fill opacity", 30, 0, 150, 5);
    private final BoolSetting tracers = bool("Tracers", false);
    private final BoolSetting stash = bool("Stash finder", true).desc("Alert when a chunk has lots of storage");
    private final NumberSetting stashCount = num("Stash size", 8, 3, 50, 1).desc("Containers in one chunk to count as a stash")
            .visible(stash::get);

    private List<Box> boxes = List.of();
    private final Set<Long> alerted = new HashSet<>();
    private ClientLevel level;
    private int timer;
    private boolean warned;

    public StorageEspModule() { super("StorageESP", "Find chests, shulkers and stashes", Category.RENDER); }

    @Override public String info() { return String.valueOf(boxes.size()); }

    @Override protected void onDisable() { boxes = List.of(); alerted.clear(); }

    private int colorFor(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return chests.get() ? chestColor.get() : 0;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? barrelColor.get() : 0;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? shulkerColor.get() : 0;
        if (be instanceof EnderChestBlockEntity) return enderChests.get() ? enderColor.get() : 0;
        if (be instanceof HopperBlockEntity) return hoppers.get() ? otherColor.get() : 0;
        if (be instanceof AbstractFurnaceBlockEntity) return furnaces.get() ? otherColor.get() : 0;
        if (be instanceof DispenserBlockEntity) return dispensers.get() ? otherColor.get() : 0;
        return 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != level) { level = mc.level; alerted.clear(); }
        if (timer-- > 0) return;
        timer = 10;
        int view = Math.max(2, mc.options.getEffectiveRenderDistance());
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        List<Box> out = new ArrayList<>();
        for (int cx = pcx - view; cx <= pcx + view; cx++) {
            for (int cz = pcz - view; cz <= pcz + view; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                Map<BlockPos, BlockEntity> map;
                try {
                    map = ((ChunkAccessAccessor) mc.level.getChunk(cx, cz)).abd$getBlockEntities();
                } catch (ClassCastException e) {
                    if (!warned) { warned = true; Chat.error("StorageESP couldn't read chunks on this version."); }
                    setEnabled(false, false);
                    return;
                }
                int storage = 0;
                for (BlockEntity be : map.values()) {
                    boolean container = be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                            || be instanceof ShulkerBoxBlockEntity;
                    if (container) storage++;
                    int c = colorFor(be);
                    if (c == 0) continue;
                    BlockPos p = be.getBlockPos();
                    boolean small = be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity;
                    AABB box = small
                            ? new AABB(p.getX() + 0.0625, p.getY(), p.getZ() + 0.0625, p.getX() + 0.9375, p.getY() + 0.875, p.getZ() + 0.9375)
                            : WorldRender.block(p);
                    out.add(new Box(box, c & 0xFFFFFF));
                }
                if (stash.get() && storage >= stashCount.getInt() && alerted.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL))) {
                    Chat.info("§6Possible stash: §f" + storage + " containers §7in the chunk at §f"
                            + (cx * 16 + 8) + ", " + (cz * 16 + 8));
                }
            }
        }
        boxes = out;
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        for (Box b : boxes) {
            ctx.box(b.box, Draw.alpha(b.rgb, fill.getInt()), Draw.alpha(b.rgb, 230));
            if (tracers.get()) ctx.tracer(b.box.getCenter(), Draw.alpha(b.rgb, 150));
        }
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        if (boxes.isEmpty() || WorldRender.active()) return;
        Projection pr = new Projection(Minecraft.getInstance(), g.guiWidth(), g.guiHeight());
        int[] r = new int[4];
        for (Box b : boxes) {
            AABB a = b.box;
            if (pr.boxRect(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ, r))
                Draw.outline(g, r[0], r[1], Math.max(r[2], r[0] + 2), Math.max(r[3], r[1] + 2), b.rgb | 0xFF000000);
        }
    }
}
