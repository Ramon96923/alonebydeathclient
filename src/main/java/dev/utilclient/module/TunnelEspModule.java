package dev.utilclient.module;

import dev.utilclient.render.WorldRender;
import dev.utilclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Highlights player-dug tunnels: dead-straight passages exactly 1 wide and 2 tall, a few blocks long.
 * Caves almost never look like that, so on DonutSMP these usually lead to someone's hidden base.
 * Scans a few layers per tick so it doesn't lag. Idea from Glazed's tunnel ESP; the code is original.
 */
public final class TunnelEspModule extends Module {
    private final NumberSetting range = num("Range", 32, 8, 64, 4).unit("m");
    private final NumberSetting height = num("Height range", 16, 4, 48, 4).unit("b").desc("Blocks above and below you to scan");
    private final NumberSetting minLength = num("Min length", 5, 3, 20, 1).unit("b");
    private final NumberSetting maxY = num("Below Y", 60, -40, 320, 5).desc("Only underground tunnels (ignores surface paths)");
    private final ColorSetting color = color("Colour", 0xFFFACC15);

    private List<AABB> tunnels = List.of();
    private final Set<Long> xCells = new HashSet<>(), zCells = new HashSet<>();
    private int layer = Integer.MIN_VALUE;
    private BlockPos center;

    public TunnelEspModule() { super("TunnelESP", "Find player-made tunnels", Category.RENDER); }

    @Override public String info() { return String.valueOf(tunnels.size()); }

    @Override protected void onDisable() { tunnels = List.of(); layer = Integer.MIN_VALUE; }

    private static boolean solid(ClientLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
    }

    private static boolean air(ClientLevel l, BlockPos p) { return l.getBlockState(p).isAir(); }

    @Override
    public void onTick(Minecraft mc) {
        ClientLevel l = mc.level;
        int h = height.getInt(), r = range.getInt();
        if (layer == Integer.MIN_VALUE) {
            center = mc.player.blockPosition();
            layer = -h;
            xCells.clear();
            zCells.clear();
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int n = 0; n < 2 && layer <= h; n++, layer++) {
            int y = center.getY() + layer;
            if (y > maxY.getInt()) continue;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    m.set(center.getX() + x, y, center.getZ() + z);
                    if (!air(l, m) || !air(l, m.above()) || !solid(l, m.below()) || !solid(l, m.above(2))) continue;
                    // walls on both sides = a passage along the other axis
                    if (solid(l, m.north()) && solid(l, m.south()) && solid(l, m.above().north()) && solid(l, m.above().south()))
                        xCells.add(m.asLong());
                    if (solid(l, m.east()) && solid(l, m.west()) && solid(l, m.above().east()) && solid(l, m.above().west()))
                        zCells.add(m.asLong());
                }
            }
        }
        if (layer > h) {
            List<AABB> out = new ArrayList<>();
            runs(xCells, 1, 0, out);
            runs(zCells, 0, 1, out);
            tunnels = out;
            layer = Integer.MIN_VALUE;
        }
    }

    /** Joins neighbouring cells along (dx, dz) into straight runs and keeps the long ones. */
    private void runs(Set<Long> cells, int dx, int dz, List<AABB> out) {
        for (long key : cells) {
            BlockPos start = BlockPos.of(key);
            if (cells.contains(start.offset(-dx, 0, -dz).asLong())) continue;   // not the start of a run
            int len = 1;
            while (cells.contains(start.offset(dx * len, 0, dz * len).asLong())) len++;
            if (len < minLength.getInt()) continue;
            BlockPos end = start.offset(dx * (len - 1), 0, dz * (len - 1));
            out.add(new AABB(start.getX(), start.getY(), start.getZ(), end.getX() + 1, end.getY() + 2, end.getZ() + 1));
        }
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        int rgb = color.get() & 0xFFFFFF;
        for (AABB b : tunnels) ctx.box(b, Draw.alpha(rgb, 28), Draw.alpha(rgb, 220));
    }
}
