package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Friends;
import dev.utilclient.util.Cmd;
import dev.utilclient.util.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
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
    public static final String DEFAULT_BASE_BLOCKS = "chest,trapped_chest,barrel,*shulker_box,ender_chest,hopper,furnace,"
            + "blast_furnace,smoker,crafting_table,anvil,chipped_anvil,damaged_anvil,enchanting_table,brewing_stand,*_bed,"
            + "beacon,respawn_anchor,dropper,dispenser,crafter";
    private final ModeSetting where = mode("Search", "Everywhere", "Everywhere", "Underground", "Surface")
            .desc("Underground: only blocks below the Y level (hidden bases). Surface: only above it");
    private final NumberSetting depthY = num("Underground below Y", 50, -64, 120, 1)
            .desc("Anything below this Y counts as underground");
    private final StringSetting baseBlocks = text("Base blocks", DEFAULT_BASE_BLOCKS)
            .desc("Blocks that mean people live here (block ids, comma separated, * works). Edit in the GUI or with .rtpbf");
    private final StringSetting blacklist = text("Blacklist", "")
            .desc("Never count these, even if they're in Base blocks (e.g. chest to skip lone mineshaft chests). Edit with .rtpbf");
    private final NumberSetting storage = num("Blocks to stop", 6, 1, 80, 1)
            .desc("How many base blocks it takes to count as a base (one dungeon chest won't do it)");
    private final NumberSetting spawnerStop = num("Spawners to stop", 2, 0, 10, 1)
            .desc("Stop at this many spawners (0 = ignore). Natural dungeons have 1, player spawner rooms many");
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
                BlockPos b = lastHit != null ? lastHit : p.blockPosition();
                Chat.good("Base found after " + tries + " RTPs: " + found + " at " + b.getX() + ", " + b.getY() + ", " + b.getZ());
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

    // ---- block lists -------------------------------------------------------------------------------

    private String parsedBase, parsedBlack;
    private java.util.List<java.util.regex.Pattern> basePatterns = java.util.List.of(), blackPatterns = java.util.List.of();
    private final java.util.Map<net.minecraft.world.level.block.Block, Boolean> wantedCache = new java.util.IdentityHashMap<>();

    static java.util.List<java.util.regex.Pattern> patterns(String list) {
        java.util.List<java.util.regex.Pattern> out = new java.util.ArrayList<>();
        for (String raw : list.split("[,\\s]+")) {
            String id = raw.strip().toLowerCase(java.util.Locale.ROOT);
            if (id.startsWith("minecraft:")) id = id.substring(10);
            if (id.isEmpty()) continue;
            out.add(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(id).replace("*", "\\E.*\\Q")));
        }
        return out;
    }

    private static boolean any(java.util.List<java.util.regex.Pattern> ps, String id) {
        for (var p : ps) if (p.matcher(id).matches()) return true;
        return false;
    }

    private void refreshLists() {
        if (!baseBlocks.get().equals(parsedBase) || !blacklist.get().equals(parsedBlack)) {
            parsedBase = baseBlocks.get();
            parsedBlack = blacklist.get();
            basePatterns = patterns(parsedBase);
            blackPatterns = patterns(parsedBlack);
            wantedCache.clear();
        }
    }

    /** 1 = base block, 2 = spawner, 0 = ignore. */
    private int kind(net.minecraft.world.level.block.state.BlockState st) {
        if (st.isAir()) return 0;
        var b = st.getBlock();
        if (b == Blocks.SPAWNER) return spawnerStop.getInt() > 0 && !any(blackPatterns, "spawner") ? 2 : 0;
        return wantedCache.computeIfAbsent(b, k -> {
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(k).getPath();
            return any(basePatterns, id) && !any(blackPatterns, id);
        }) ? 1 : 0;
    }

    private boolean yOk(int y) {
        return switch (where.get()) {
            case "Underground" -> y < depthY.getInt();
            case "Surface" -> y >= depthY.getInt();
            default -> true;
        };
    }

    /** For .rtpbf: add/remove an id on one of the lists. */
    public String editList(boolean black, boolean add, String id) {
        StringSetting set = black ? blacklist : baseBlocks;
        java.util.LinkedHashSet<String> items = new java.util.LinkedHashSet<>();
        for (String x : set.get().split(",")) if (!x.isBlank()) items.add(x.strip());
        id = id.strip().toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "");
        if (add) items.add(id); else items.remove(id);
        set.set(String.join(",", items));
        return set.get().isEmpty() ? "(empty)" : set.get();
    }

    public String list(boolean black) {
        String v = (black ? blacklist : baseBlocks).get();
        return v.isEmpty() ? "(empty)" : v;
    }

    public void resetList(boolean black) { (black ? blacklist : baseBlocks).set(black ? "" : DEFAULT_BASE_BLOCKS); }

    /** What makes this spot interesting, or null if nothing. */
    private String scan(Minecraft mc) {
        refreshLists();
        int view = Math.max(2, mc.options.getEffectiveRenderDistance());
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        int found = 0, spawnerCount = 0;
        java.util.Map<String, Integer> byType = new java.util.TreeMap<>();
        java.util.List<BlockPos> hits = new java.util.ArrayList<>();
        for (int cx = pcx - view; cx <= pcx + view; cx++) {
            for (int cz = pcz - view; cz <= pcz + view; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                net.minecraft.world.level.chunk.LevelChunk ch = mc.level.getChunk(cx, cz);
                var sections = ch.getSections();
                // every section from bedrock up, so underground bases are found too
                for (int i = 0; i < sections.length; i++) {
                    var sec = sections[i];
                    if (sec == null || sec.hasOnlyAir()) continue;
                    int baseY = ch.getMinY() + i * 16;
                    if (!yOk(baseY) && !yOk(baseY + 15)) continue;
                    if (!sec.maybeHas(st -> kind(st) != 0)) continue;   // palette check: skip sections without any
                    for (int y = 0; y < 16; y++) {
                        if (!yOk(baseY + y)) continue;
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                var st = sec.getBlockState(x, y, z);
                                int k = kind(st);
                                if (k == 0) continue;
                                if (k == 2) spawnerCount++;
                                else {
                                    found++;
                                    byType.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath(), 1, Integer::sum);
                                }
                                if (hits.size() < 4000) hits.add(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z));
                            }
                        }
                    }
                }
            }
        }
        boolean base = found >= storage.getInt();
        boolean spawn = spawnerStop.getInt() > 0 && spawnerCount >= spawnerStop.getInt();
        if (base || spawn) {
            lastHit = centre(hits);
            StringBuilder sb = new StringBuilder();
            if (spawn) sb.append(spawnerCount).append(" spawner(s)");
            if (base) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(found).append(" base blocks (");
                int n = 0;
                for (var e : byType.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).toList()) {
                    if (n++ == 4) { sb.append(", ..."); break; }
                    if (n > 1) sb.append(", ");
                    sb.append(e.getValue()).append(' ').append(e.getKey());
                }
                sb.append(')');
            }
            if (lastHit != null && lastHit.getY() < depthY.getInt()) sb.append(" - underground");
            return sb.toString();
        }
        if (players.get()) {
            for (Player other : mc.level.players()) {
                if (other != mc.player && !Friends.isFriend(other)) { lastHit = other.blockPosition(); return "player " + other.getName().getString(); }
            }
        }
        return null;
    }

    private BlockPos lastHit;

    /** The hit nearest the middle of all hits: where the base actually is. */
    private static BlockPos centre(java.util.List<BlockPos> hits) {
        if (hits.isEmpty()) return null;
        double sx = 0, sy = 0, sz = 0;
        for (BlockPos b : hits) { sx += b.getX(); sy += b.getY(); sz += b.getZ(); }
        double mx = sx / hits.size(), my = sy / hits.size(), mz = sz / hits.size();
        BlockPos best = hits.get(0);
        double bd = Double.MAX_VALUE;
        for (BlockPos b : hits) {
            double d = b.distToCenterSqr(mx, my, mz);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }
}
