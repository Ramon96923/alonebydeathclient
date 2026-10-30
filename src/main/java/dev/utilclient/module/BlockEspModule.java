package dev.utilclient.module;

import dev.utilclient.render.WorldRender;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Boxes (and optional tracers) around ores and storage through walls. Scans a little each tick to avoid lag. */
public final class BlockEspModule extends Module {
    private record Kind(String name, Predicate<String> test, ColorSetting color, BoolSetting on) {}
    private record Found(BlockPos pos, int argb) {}

    private final List<Kind> kinds = new ArrayList<>();
    private final NumberSetting range = num("Range", 32, 8, 64, 4).unit("m");
    private final NumberSetting maxBoxes = num("Max boxes", 150, 20, 500, 10);
    private final ModeSetting style = mode("Style", "Outline", "Fill", "Outline", "Both");
    private final NumberSetting fillAlpha = num("Fill opacity", 22, 5, 150, 5).visible(() -> !style.is("Outline"));
    private final BoolSetting merge = bool("Merge veins", true)
            .desc("Draw clustered ores as a clean wireframe instead of overlapping filled blobs");
    private final BoolSetting fade = bool("Fade with distance", true).desc("Far blocks are drawn fainter");
    private final BoolSetting tracers = bool("Tracers", false);

    private final Map<Block, Kind> lookup = new IdentityHashMap<>();
    private List<Found> found = List.of();
    private final List<Found> building = new ArrayList<>();
    private int scanLayer = Integer.MIN_VALUE;
    private BlockPos scanCenter;

    private void kind(String name, Predicate<String> idTest, int argb, boolean def) {
        BoolSetting on = bool(name, def, this::rebuild);
        ColorSetting col = color(name + " colour", argb).visible(on::get);
        Kind k = new Kind(name, idTest, col, on);
        kinds.add(k);
    }

    public BlockEspModule() {
        super("BlockESP", "Highlight ores and storage through walls", Category.RENDER);
        kind("Diamond", id -> id.endsWith("diamond_ore"), 0xFF3DDCFF, true);
        kind("Emerald", id -> id.endsWith("emerald_ore"), 0xFF57F287, false);
        kind("Gold", id -> id.endsWith("gold_ore"), 0xFFFFD84D, false);
        kind("Iron", id -> id.endsWith("iron_ore"), 0xFFE0B890, false);
        kind("Debris", id -> id.equals("ancient_debris"), 0xFFB5651D, true);
        kind("Chests", id -> id.equals("chest") || id.equals("trapped_chest") || id.equals("barrel"), 0xFFFFB347, true);
        kind("Ender chests", id -> id.equals("ender_chest"), 0xFFA855F7, true);
        kind("Shulkers", id -> id.endsWith("shulker_box"), 0xFFF472B6, true);
        kind("Spawners", id -> id.equals("spawner") || id.equals("trial_spawner"), 0xFFEF4444, true);
        kind("Furnaces", id -> id.equals("furnace") || id.equals("blast_furnace") || id.equals("smoker"), 0xFF9CA3AF, false);
        kind("Hoppers", id -> id.equals("hopper") || id.equals("dropper") || id.equals("dispenser"), 0xFF64748B, false);
    }

    private void rebuild() {
        lookup.clear();
        for (Block b : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
            for (Kind k : kinds) {
                if (k.on.get() && k.test.test(id)) { lookup.put(b, k); break; }
            }
        }
        scanLayer = Integer.MIN_VALUE;
    }

    @Override protected void onEnable() { rebuild(); found = List.of(); }

    @Override
    public void onTick(Minecraft mc) {
        if (lookup.isEmpty()) rebuild();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        int r = range.getInt();
        int vr = Math.max(8, r / 2);
        if (scanLayer == Integer.MIN_VALUE || scanCenter == null) {
            scanCenter = p.blockPosition();
            scanLayer = -vr;
            building.clear();
        }
        // a few horizontal layers per tick: a full scan completes in about a second
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int layersPerTick = Math.max(1, (vr * 2 + 1) / 16 + 1);
        for (int n = 0; n < layersPerTick && scanLayer <= vr; n++, scanLayer++) {
            int y = scanCenter.getY() + scanLayer;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                m.set(scanCenter.getX() + x, y, scanCenter.getZ() + z);
                Kind k = lookup.get(level.getBlockState(m).getBlock());
                if (k != null) building.add(new Found(m.immutable(), k.color.get()));
            }
        }
        if (scanLayer > vr) {
            BlockPos c = p.blockPosition();
            building.sort((a, b) -> Double.compare(a.pos.distSqr(c), b.pos.distSqr(c)));
            int max = maxBoxes.getInt();
            found = new ArrayList<>(building.size() > max ? building.subList(0, max) : building);
            scanLayer = Integer.MIN_VALUE;
        }
    }

    private float fadeFor(BlockPos b, net.minecraft.world.phys.Vec3 cam) {
        if (!fade.get()) return 1f;
        double d = Math.sqrt(b.distToCenterSqr(cam));
        double r = range.get();
        return (float) Math.max(0.25, Math.min(1.0, 1.0 - (d - r * 0.5) / (r * 0.5)));
    }

    /** Packed key for a block position, for the merge lookup. */
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        boolean fill = !style.is("Outline"), outline = !style.is("Fill");
        java.util.Set<Long> set = null;
        if (merge.get() && fill) {
            set = new java.util.HashSet<>(found.size() * 2);
            for (Found f : found) set.add(key(f.pos.getX(), f.pos.getY(), f.pos.getZ()));
        }
        for (Found f : found) {
            float a = fadeFor(f.pos, ctx.cam);
            int rgb = f.argb & 0xFFFFFF;
            var box = WorldRender.block(f.pos);
            // In a vein, skip the fill so the cluster reads as a clean wireframe, not an opaque blob.
            boolean clustered = set != null && hasNeighbour(set, f.pos);
            if (fill && !clustered) ctx.fill(box, Draw.alpha(rgb, (int) (fillAlpha.getInt() * a)));
            if (outline) ctx.outline(box, Draw.alpha(rgb, (int) (235 * a)));
            if (tracers.get()) ctx.tracer(box.getCenter(), Draw.alpha(rgb, (int) (170 * a)));
        }
    }

    private static boolean hasNeighbour(java.util.Set<Long> set, BlockPos p) {
        int x = p.getX(), y = p.getY(), z = p.getZ();
        return set.contains(key(x + 1, y, z)) || set.contains(key(x - 1, y, z))
                || set.contains(key(x, y + 1, z)) || set.contains(key(x, y - 1, z))
                || set.contains(key(x, y, z + 1)) || set.contains(key(x, y, z - 1));
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        if (found.isEmpty() || WorldRender.active()) return;
        Minecraft mc = Minecraft.getInstance();
        Projection pr = new Projection(mc, g.guiWidth(), g.guiHeight());
        int[] rect = new int[4];
        double[] o = new double[3];
        boolean fill = !style.is("Outline"), outline = !style.is("Fill");
        for (Found f : found) {
            BlockPos b = f.pos;
            if (pr.boxRect(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1, rect)) {
                if (fill) g.fill(rect[0], rect[1], rect[2], rect[3], Draw.alpha(f.argb, fillAlpha.getInt()));
                if (outline) Draw.outline(g, rect[0], rect[1], Math.max(rect[2], rect[0] + 2), Math.max(rect[3], rect[1] + 2), f.argb | 0xFF000000);
            }
            if (tracers.get()) {
                pr.projectClamped(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, o);
                Draw.line(g, pr.width / 2.0, pr.height / 2.0, o[0], o[1], Draw.withAlpha(f.argb | 0xFF000000, 0.7f));
            }
        }
    }
}
