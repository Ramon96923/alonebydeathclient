package dev.utilclient.module;

import dev.utilclient.core.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * DonutSMP staff awareness.
 * <ul>
 *   <li>Spots staff in the tab list by their rank prefix (Admin, Mod, Helper...) or by names you add.</li>
 *   <li>Tells you when staff join or leave, and keeps a live list for the HUD ("Staff" element).</li>
 *   <li>Warns when a staff member near you turns invisible / vanishes, or appears right next to you
 *       (they teleported to you).</li>
 * </ul>
 */
public final class StaffAlertModule extends Module {
    public record Staff(String name, String rank) {}

    private final StringSetting ranks = text("Rank words", "owner,admin,manager,mod,helper,staff,developer,dev,support")
            .desc("Words in a tab-list rank/prefix that mean staff (comma separated)");
    private final StringSetting names = text("Extra names", "").desc("Staff names to always treat as staff (comma separated)");
    private final BoolSetting joinLeave = bool("Join/leave alerts", true);
    private final BoolSetting invis = bool("Invisible/vanish alerts", true);
    private final BoolSetting teleport = bool("Teleport alerts", true);
    private final NumberSetting tpRange = num("Teleport range", 16, 4, 48, 2).unit("m").desc("Staff appearing this close counts as a TP to you")
            .visible(teleport::get);
    private final BoolSetting sound = bool("Sound", true);

    private final Map<UUID, Staff> online = new LinkedHashMap<>();
    private final Map<UUID, Double> lastDist = new HashMap<>();
    private final Map<UUID, Boolean> lastInvis = new HashMap<>();
    private boolean first = true;
    private int timer;

    public StaffAlertModule() { super("StaffAlert", "Staff online list, join/vanish/TP alerts", Category.DONUT); }

    @Override public String info() { return online.isEmpty() ? null : String.valueOf(online.size()); }

    @Override protected void onEnable() { online.clear(); lastDist.clear(); lastInvis.clear(); first = true; }

    /** Staff currently in the tab list (for the HUD). */
    public List<Staff> online() { return new ArrayList<>(online.values()); }

    private static String clean(String s) { return s.replaceAll("§.", "").toLowerCase(Locale.ROOT); }

    private String rankOf(Minecraft mc, PlayerInfo pi) {
        String name = pi.getProfile().name();
        for (String n : names.get().split(",")) if (!n.isBlank() && n.strip().equalsIgnoreCase(name)) return "Staff";
        StringBuilder tags = new StringBuilder();
        try {
            PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(name);
            if (team != null) tags.append(team.getPlayerPrefix().getString()).append(' ').append(team.getPlayerSuffix().getString());
        } catch (Throwable ignored) {
            // no scoreboard
        }
        if (pi.getTabListDisplayName() != null) tags.append(' ').append(pi.getTabListDisplayName().getString().replace(name, " "));
        String t = clean(tags.toString());
        for (String w : ranks.get().split(",")) {
            String word = w.strip().toLowerCase(Locale.ROOT);
            if (word.isEmpty()) continue;
            int i = t.indexOf(word);
            // whole-word match so "mod" doesn't fire on a clan tag like "MODERN"
            if (i >= 0 && (i == 0 || !Character.isLetter(t.charAt(i - 1)))
                    && (i + word.length() == t.length() || !Character.isLetter(t.charAt(i + word.length())))) {
                return Character.toUpperCase(word.charAt(0)) + word.substring(1);
            }
        }
        return null;
    }

    private void alert(String msg) {
        Chat.info("§c[Staff] §f" + msg);
        if (sound.get()) Chat.ping();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.getConnection() == null) return;
        if (timer-- <= 0) {
            timer = 20;
            Map<UUID, Staff> now = new LinkedHashMap<>();
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                String rank = rankOf(mc, pi);
                if (rank != null) now.put(pi.getProfile().id(), new Staff(pi.getProfile().name(), rank));
            }
            if (!first && joinLeave.get()) {
                for (var e : now.entrySet()) if (!online.containsKey(e.getKey())) alert(e.getValue().rank + " " + e.getValue().name + " joined");
                for (var e : online.entrySet()) if (!now.containsKey(e.getKey())) alert(e.getValue().rank + " " + e.getValue().name + " left (or vanished)");
            } else if (first && !now.isEmpty()) {
                Chat.info("§c[Staff] §7Online: §f" + String.join(", ", now.values().stream().map(Staff::name).toList()));
            }
            first = false;
            online.clear();
            online.putAll(now);
        }

        // staff near you: invisible, vanished, or just appeared next to you
        Map<UUID, Double> seen = new HashMap<>();
        for (Player pl : mc.level.players()) {
            if (pl == mc.player || !online.containsKey(pl.getUUID())) continue;
            UUID id = pl.getUUID();
            double d = mc.player.distanceTo(pl);
            seen.put(id, d);
            String name = online.get(id).name;
            if (teleport.get() && !lastDist.containsKey(id) && d <= tpRange.get()) alert(name + " appeared " + Math.round(d) + "m from you (teleported?)");
            boolean inv = pl.isInvisible();
            if (invis.get() && inv && !lastInvis.getOrDefault(id, false)) alert(name + " went invisible " + Math.round(d) + "m from you");
            lastInvis.put(id, inv);
        }
        if (invis.get()) {
            for (var e : lastDist.entrySet()) {
                // was close, still online, but gone from the world: vanished, not walked away
                if (!seen.containsKey(e.getKey()) && e.getValue() < 24 && online.containsKey(e.getKey())) {
                    alert(online.get(e.getKey()).name + " vanished " + Math.round(e.getValue()) + "m from you");
                }
            }
        }
        lastDist.clear();
        lastDist.putAll(seen);
        lastInvis.keySet().retainAll(seen.keySet());
    }
}
