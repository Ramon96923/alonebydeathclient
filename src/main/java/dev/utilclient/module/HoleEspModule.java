package dev.utilclient.module;

import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Shows 1x1 holes you can stand in to be safe from crystals (bedrock = green, obsidian = red). */
public final class HoleEspModule extends Module {
    private record Hole(BlockPos pos, boolean bedrock) {}

    private final NumberSetting range = num("Range", 8, 3, 16, 1).unit("m");
    private final NumberSetting height = num("Box height", 0.2, 0, 1, 0.1).desc("0 = flat outline on the floor");
    private final ColorSetting bedrockColor = color("Bedrock colour", 0xFF22C55E);
    private final ColorSetting obsidianColor = color("Obsidian colour", 0xFFEF4444);

    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
    private List<Hole> holes = List.of();
    private int timer;

    public HoleEspModule() { super("HoleESP", "Highlight safe holes for crystal PvP", Category.RENDER); }

    @Override
    public void onTick(Minecraft mc) {
        if (timer-- > 0) return;
        timer = 5;
        ClientLevel level = mc.level;
        BlockPos c = mc.player.blockPosition();
        int r = range.getInt();
        List<Hole> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -4, -r), c.offset(r, 3, r))) {
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                    || !level.getBlockState(pos.above(2)).isAir()) continue;
            boolean all = true, bedrock = true;
            for (Direction d : SIDES) {
                BlockState st = level.getBlockState(pos.relative(d));
                if (st.is(Blocks.BEDROCK)) continue;
                bedrock = false;
                if (st.getBlock().getExplosionResistance() < 600) { all = false; break; }
            }
            if (all) out.add(new Hole(pos.immutable(), bedrock));
        }
        holes = out;
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        if (holes.isEmpty()) return;
        Projection pr = new Projection(Minecraft.getInstance(), g.guiWidth(), g.guiHeight());
        double[][] p = new double[8][3];
        double h = height.get();
        for (Hole hole : holes) {
            BlockPos b = hole.pos;
            int c = (hole.bedrock ? bedrockColor.get() : obsidianColor.get()) | 0xFF000000;
            double y0 = b.getY(), y1 = b.getY() + h;
            boolean ok = true;
            for (int i = 0; i < 8 && ok; i++) {
                double x = b.getX() + ((i & 1) == 0 ? 0 : 1);
                double z = b.getZ() + ((i & 2) == 0 ? 0 : 1);
                ok = pr.project(x, (i & 4) == 0 ? y0 : y1, z, p[i]);
            }
            if (!ok) continue;
            int[][] edges = {{0, 1}, {1, 3}, {3, 2}, {2, 0}};
            for (int[] e : edges) Draw.line(g, p[e[0]][0], p[e[0]][1], p[e[1]][0], p[e[1]][1], 1.5f, c);
            if (h > 0) {
                for (int[] e : edges) Draw.line(g, p[e[0] + 4][0], p[e[0] + 4][1], p[e[1] + 4][0], p[e[1] + 4][1], 1f, Draw.withAlpha(c, 0.5f));
                for (int i = 0; i < 4; i++) Draw.line(g, p[i][0], p[i][1], p[i + 4][0], p[i + 4][1], 1f, Draw.withAlpha(c, 0.5f));
            }
        }
    }
}
