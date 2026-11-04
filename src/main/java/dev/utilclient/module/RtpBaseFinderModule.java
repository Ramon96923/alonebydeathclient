package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Friends;
import dev.utilclient.mixin.ChunkAccessAccessor;
import dev.utilclient.util.Cmd;
import dev.utilclient.util.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * DonutSMP base hunting on autopilot: /rtp, wait for the chunks to load, look for storage, spawners and
 * players, and repeat until something turns up. Then it stops, chimes, prints the coordinates and can log
 * you out so you can come back safely. Idea from Glazed's RTP Base Finder; the code is original.
 */
public final class RtpBaseFinderModule extends Module {
    private final StringSetting command = text("Command", "/rtp east").desc("What to send to teleport (DonutSMP: /rtp east, /rtp west...)");
    private final NumberSetting interval = num("RTP every", 15, 5, 120, 1).unit("s").desc("Keep this above the server's RTP cooldown");
    private final NumberSetting settle = num("Scan after", 4, 1, 20, 1).unit("s").desc("Time for chunks to load after a teleport");
    private final NumberSetting storage = num("Storage to stop", 4, 1, 60, 1).desc("Chests/barrels/shulkers that count as a base");
    private final BoolSetting spawners = bool("Stop on spawner", true);
    private final BoolSetting players = bool("Stop on player", false).desc("Stop if someone (not a friend) is nearby");
    private final BoolSetting confirm = bool("Click confirm menus", true).desc("If the RTP command opens a menu, press its confirm button");
    private final BoolSetting logout = bool("Log out when found", false);

    private Vec3 lastPos;
    private long lastRtp, teleportedAt;
    private boolean scanned = true;
    private int tries;

    public RtpBaseFinderModule() { super("RTPBaseFinder", "RTP until you land near a base", Category.DONUT); }

    @Override public String info() { return tries > 0 ? tries + " RTPs" : null; }

    @Override
    protected void onEnable() {
        lastPos = null;
        lastRtp = 0;
        teleportedAt = 0;
        scanned = true;
        tries = 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        long now = System.currentTimeMillis();
        Vec3 pos = p.position();
        if (lastPos != null && pos.distanceToSqr(lastPos) > 64 * 64) {   // just got teleported
            teleportedAt = now;
            scanned = false;
        }
        lastPos = pos;

        if (confirm.get() && now - lastRtp < 6000 && Gui.serverMenu(mc) != null) Gui.clickConfirm(mc);

        if (!scanned && now - teleportedAt > settle.get() * 1000) {
            scanned = true;
            String found = scan(mc);
            if (found != null) {
                BlockPos b = p.blockPosition();
                Chat.good("Base found after " + tries + " RTPs: " + found + " near " + b.getX() + ", " + b.getY() + ", " + b.getZ());
                Chat.ping();
                setEnabled(false);
                if (logout.get() && mc.getConnection() != null) {
                    mc.getConnection().handleDisconnect(new ClientboundDisconnectPacket(Component.literal(
                            "[DonutWaffen] Base found at " + b.getX() + ", " + b.getY() + ", " + b.getZ() + " (" + found + ")")));
                }
                return;
            }
        }
        boolean waiting = !scanned;   // still waiting to scan the last spot
        if (!waiting && mc.gui.screen() == null && now - lastRtp > interval.get() * 1000) {
            Cmd.send(mc, command.get());
            lastRtp = now;
            tries++;
        }
    }

    /** What makes this spot interesting, or null if nothing. */
    private String scan(Minecraft mc) {
        int view = Math.max(2, mc.options.getEffectiveRenderDistance());
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        int containers = 0, spawnerCount = 0;
        for (int cx = pcx - view; cx <= pcx + view; cx++) {
            for (int cz = pcz - view; cz <= pcz + view; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : ((ChunkAccessAccessor) mc.level.getChunk(cx, cz)).abd$getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) containers++;
                    else if (be.getBlockState().is(Blocks.SPAWNER)) spawnerCount++;
                }
            }
        }
        if (spawners.get() && spawnerCount > 0) return spawnerCount + " spawner(s)";
        if (containers >= storage.getInt()) return containers + " containers";
        if (players.get()) {
            for (Player other : mc.level.players()) {
                if (other != mc.player && !Friends.isFriend(other)) return "player " + other.getName().getString();
            }
        }
        return null;
    }
}
