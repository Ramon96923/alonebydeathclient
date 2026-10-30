package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.gui.Fonts;
import dev.utilclient.render.WorldRender;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Marks where players logged out (combat loggers on anarchy servers): a box where they stood and their name,
 * health and how long ago. The marker clears when they log back in.
 */
public final class LogoutSpotsModule extends Module {
    private record Seen(String name, AABB box, float health) {}
    private record Spot(UUID id, String name, AABB box, float health, long time) {}

    private final ColorSetting color = color("Colour", 0xFFF59E0B);
    private final BoolSetting notify = bool("Chat alert", true).desc("Say in chat when someone logs out nearby");
    private final NumberSetting maxSpots = num("Max spots", 20, 1, 100, 1);

    private final Map<UUID, Seen> seen = new HashMap<>();
    private final List<Spot> spots = new ArrayList<>();
    private ClientLevel level;

    public LogoutSpotsModule() { super("LogoutSpots", "Show where players logged out", Category.RENDER); }

    @Override protected void onDisable() { seen.clear(); spots.clear(); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != level) {   // new world or dimension: old markers no longer apply
            level = mc.level;
            seen.clear();
            spots.clear();
        }
        Set<UUID> present = new HashSet<>();
        for (Player pl : mc.level.players()) {
            if (pl == mc.player) continue;
            UUID id = pl.getUUID();
            present.add(id);
            seen.put(id, new Seen(pl.getName().getString(), pl.getBoundingBox(), pl.getHealth()));
            spots.removeIf(s -> s.id.equals(id));   // they're back
        }
        if (mc.getConnection() == null) return;
        seen.entrySet().removeIf(e -> {
            if (present.contains(e.getKey())) return false;
            // gone from the world: still in the tab list means they just walked out of range
            if (mc.getConnection().getPlayerInfo(e.getKey()) == null) {
                Seen s = e.getValue();
                spots.add(new Spot(e.getKey(), s.name, s.box, s.health, System.currentTimeMillis()));
                if (spots.size() > maxSpots.getInt()) spots.remove(0);
                if (notify.get()) Chat.info("§e" + s.name + " §7logged out at §f"
                        + (int) s.box.minX + ", " + (int) s.box.minY + ", " + (int) s.box.minZ);
            }
            return true;
        });
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        int rgb = color.get() & 0xFFFFFF;
        for (Spot s : spots) ctx.box(s.box, Draw.alpha(rgb, 45), Draw.alpha(rgb, 235));
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        if (spots.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Projection pr = new Projection(mc, g.guiWidth(), g.guiHeight());
        double[] o = new double[3];
        int col = color.get() | 0xFF000000;
        long now = System.currentTimeMillis();
        for (Spot s : spots) {
            AABB b = s.box;
            if (!pr.project((b.minX + b.maxX) / 2, b.maxY + 0.35, (b.minZ + b.maxZ) / 2, o)) continue;
            long sec = (now - s.time) / 1000;
            String label = s.name + "  " + Math.round(s.health) + "hp  " + (sec < 60 ? sec + "s" : sec / 60 + "m") + " ago";
            int w = Fonts.width(label) + 8, x = (int) o[0] - w / 2, y = (int) o[1] - 6;
            Draw.round(g, x, y, x + w, y + 12, 3, 0xC00E0E14);
            Fonts.text(g, label, x + 4, y + 2, col);
        }
        if (!WorldRender.active()) {   // 2D fallback boxes
            int[] rect = new int[4];
            for (Spot s : spots) {
                AABB b = s.box;
                if (pr.boxRect(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, rect))
                    Draw.outline(g, rect[0], rect[1], Math.max(rect[2], rect[0] + 2), Math.max(rect[3], rect[1] + 2), col);
            }
        }
    }
}
