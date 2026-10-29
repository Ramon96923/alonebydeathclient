package dev.utilclient.module;

import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Boxes (and optional tracers) around chosen blocks, drawn on the HUD layer so they show through walls. */
public final class BlockEspModule extends Module {
    private record Kind(String name, Predicate<BlockState> test, int rgb, BoolSetting on) {}
    private record Found(BlockPos pos, int rgb) {}

    private final List<Kind> kinds = new ArrayList<>();
    private final NumberSetting range = num("Range", 24, 8, 32, 8);
    private final NumberSetting maxBoxes = num("Max boxes", 60, 20, 100, 20);
    private final BoolSetting fill = add("Fill", true);
    private final BoolSetting tracers = add("Tracers", false);
    private final BoolSetting fromBottom = add("Tracers from bottom", false);

    private List<Found> found = List.of();
    private int scanIn;

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", path));
    }

    private void kind(String name, Predicate<BlockState> test, int rgb, boolean def) {
        kinds.add(new Kind(name, test, rgb, add(name, def)));
    }

    public BlockEspModule() {
        super("BlockESP", "Highlight ores & containers through walls", Category.RENDER);
        TagKey<Block> coal = tag("coal_ores"), iron = tag("iron_ores"), copper = tag("copper_ores"),
                gold = tag("gold_ores"), redstone = tag("redstone_ores"), lapis = tag("lapis_ores"),
                diamond = tag("diamond_ores"), emerald = tag("emerald_ores");
        kind("Diamond",  s -> s.is(diamond),  0x3DDCFF, true);
        kind("Emerald",  s -> s.is(emerald),  0x57F287, true);
        kind("Gold",     s -> s.is(gold),     0xFFD84D, true);
        kind("Iron",     s -> s.is(iron),     0xE0B890, false);
        kind("Copper",   s -> s.is(copper),   0xFF8A4D, false);
        kind("Coal",     s -> s.is(coal),     0xA0A0A0, false);
        kind("Redstone", s -> s.is(redstone), 0xFF3B3B, false);
        kind("Lapis",    s -> s.is(lapis),    0x4D6BFF, false);
        kind("Debris",   s -> s.is(Blocks.ANCIENT_DEBRIS), 0xB5651D, true);
        kind("Chests",   s -> s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST) || s.is(Blocks.ENDER_CHEST), 0xFFB347, false);
        kind("Spawners", s -> s.is(Blocks.SPAWNER), 0xB36BFF, true);
    }

    @Override protected void onEnable() { scanIn = 0; }

    @Override
    public void onTick(Minecraft mc) {
        if (scanIn-- > 0) return;
        scanIn = 20;
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;

        List<Kind> active = new ArrayList<>();
        for (Kind k : kinds) if (k.on.get()) active.add(k);
        if (active.isEmpty()) { found = List.of(); return; }

        BlockPos c = p.blockPosition();
        int r = range.getInt();
        int vy = Math.max(8, r / 2);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        List<Found> out = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int y = -vy; y <= vy; y++) for (int z = -r; z <= r; z++) {
            mp.set(c.getX() + x, c.getY() + y, c.getZ() + z);
            BlockState st = level.getBlockState(mp);
            if (st.isAir()) continue;
            for (Kind k : active) {
                if (k.test.test(st)) { out.add(new Found(mp.immutable(), k.rgb)); break; }
            }
        }
        out.sort((a, b) -> Double.compare(a.pos.distSqr(c), b.pos.distSqr(c)));
        int max = maxBoxes.getInt();
        found = out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || found.isEmpty()) return;
        Projection pr = new Projection(mc, pt);
        double[] o = new double[2];
        int[] rect = new int[4];
        int ox = pr.width / 2;
        int oy = fromBottom.get() ? pr.height : pr.height / 2;

        for (Found f : found) {
            BlockPos b = f.pos;
            int rgb = f.rgb;
            if (pr.boxRect(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1, rect)
                    && rect[2] >= 0 && rect[0] <= pr.width && rect[3] >= 0 && rect[1] <= pr.height) {
                if (fill.get()) g.fill(rect[0], rect[1], rect[2], rect[3], 0x30000000 | rgb);
                Draw.outline(g, rect[0], rect[1], Math.max(rect[2], rect[0] + 2), Math.max(rect[3], rect[1] + 2), 0xFF000000 | rgb);
            }
            if (tracers.get()) {
                pr.projectClamped(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, o);
                Draw.line(g, ox, oy, (int) o[0], (int) o[1], 0xB0000000 | rgb);
            }
        }
    }
}
