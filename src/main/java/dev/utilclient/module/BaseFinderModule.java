package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.mixin.ChunkAccessAccessor;
import dev.utilclient.render.WorldRender;
import dev.utilclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches every chunk that loads around you (flying, walking, RTPing) and tells you when one looks like a
 * base or stash: lots of chests/barrels/shulkers, any spawners, or ender chests + hoppers. Each find is
 * announced once with its coordinates and marked in the world. Works without StorageESP.
 */
public final class BaseFinderModule extends Module {
    private record Find(int cx, int cz, int minY, int maxY, String what) {}

    private final NumberSetting minContainers = num("Containers", 6, 2, 60, 1).desc("Chests/barrels/shulkers in one chunk to count as a stash");
    private final BoolSetting spawners = bool("Spawners", true).desc("Any spawner counts as a base (DonutSMP)");
    private final NumberSetting minY = num("Min Y", -64, -64, 320, 8).desc("Ignore storage above/below this (e.g. skip villages on the surface)");
    private final NumberSetting maxY = num("Max Y", 320, -64, 320, 8);
    private final BoolSetting sound = bool("Sound", true);
    private final BoolSetting mark = bool("Mark in world", true).desc("Draw a column over found chunks");
    private final BoolSetting logout = bool("Log out on find", false);

    private final Set<Long> checked = new HashSet<>();
    private final List<Find> finds = new ArrayList<>();
    private ClientLevel level;
    private int timer;

    public BaseFinderModule() { super("BaseFinder", "Alert when a loaded chunk has a stash/base", Category.DONUT); }

    @Override public String info() { return finds.isEmpty() ? null : String.valueOf(finds.size()); }

    @Override protected void onDisable() { checked.clear(); finds.clear(); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != level) { level = mc.level; checked.clear(); finds.clear(); }
        if (timer-- > 0) return;
        timer = 10;
        int view = Math.max(2, mc.options.getEffectiveRenderDistance());
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        for (int cx = pcx - view; cx <= pcx + view; cx++) {
            for (int cz = pcz - view; cz <= pcz + view; cz++) {
                long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
                if (checked.contains(key) || !mc.level.hasChunk(cx, cz)) continue;
                var map = ((ChunkAccessAccessor) mc.level.getChunk(cx, cz)).abd$getBlockEntities();
                int containers = 0, spawnerCount = 0, ender = 0, hoppers = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (BlockEntity be : map.values()) {
                    int y = be.getBlockPos().getY();
                    if (y < minY.getInt() || y > maxY.getInt()) continue;
                    boolean counted = true;
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) containers++;
                    else if (be.getBlockState().is(Blocks.SPAWNER)) spawnerCount++;
                    else if (be instanceof EnderChestBlockEntity) ender++;
                    else if (be instanceof HopperBlockEntity) hoppers++;
                    else counted = false;
                    if (counted) { lo = Math.min(lo, y); hi = Math.max(hi, y); }
                }
                checked.add(key);
                String what = null;
                if (spawners.get() && spawnerCount > 0) what = spawnerCount + " spawner(s)" + (containers > 0 ? ", " + containers + " containers" : "");
                else if (containers >= minContainers.getInt()) what = containers + " containers";
                else if (ender > 0 && hoppers >= 2 && containers >= 2) what = "ender chest + hopper setup";
                if (what == null) continue;
                finds.add(new Find(cx, cz, lo, hi, what));
                if (finds.size() > 100) finds.remove(0);
                int bx = cx * 16 + 8, bz = cz * 16 + 8;
                Chat.info("§6Base/stash: §f" + what + " §7at §f" + bx + ", " + lo + ", " + bz);
                if (sound.get()) Chat.ping();
                if (logout.get() && mc.getConnection() != null) {
                    setEnabled(false, false);
                    mc.getConnection().handleDisconnect(new ClientboundDisconnectPacket(Component.literal(
                            "[DonutWaffen] Stash found at " + bx + ", " + lo + ", " + bz + " (" + what + ")")));
                    return;
                }
            }
        }
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        if (!mark.get()) return;
        for (Find f : finds) {
            AABB box = new AABB(f.cx * 16, f.minY, f.cz * 16, f.cx * 16 + 16, f.maxY + 1, f.cz * 16 + 16);
            ctx.box(box, Draw.alpha(0xF59E0B, 18), Draw.alpha(0xF59E0B, 200));
        }
    }
}
