package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Config;
import dev.utilclient.gui.Fonts;
import dev.utilclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * DonutSMP region map. The overworld (+-225,000 blocks) is split into a 9 x 9 grid of 50,000-block regions,
 * numbered 1-81 from the north-west corner, row by row, and each region is served from one location
 * (EU Central, EU West, NA East, NA West, Asia, Oceania). Shows a round minimap of the grid with you on it,
 * plus your region number and name.
 * <p>
 * Names come from {@code config/donutwaffen/regions.txt} ("41 = NA East"), so you can copy them from the
 * official map; {@code .region set <number> <name>} edits it in-game.
 */
public final class RegionMapModule extends Module {
    public static final int GRID = 9, CELL = 50_000, HALF = GRID * CELL / 2;

    private final BoolSetting onlyDonut = bool("Only on DonutSMP", true).desc("Hide the map on other servers");
    private final BoolSetting announce = bool("Announce changes", true).desc("Chat message when you cross into another region");

    private final HudModule.Element el;
    private final NumberSetting size;
    private final BoolSetting numbers, legend;
    private final Map<Integer, String> names = new TreeMap<>();
    private boolean loaded;
    private int lastCell = -1;

    public RegionMapModule() {
        super("RegionMap", "Region map", Category.DONUT);
        el = ModuleManager.HUD.addElement("Region map", true, true, false, 4, 60, this::paint);
        size = el.num("Size", 81, 54, 162, 9);
        numbers = el.bool("Numbers", false, "Region numbers on the map");
        legend = el.bool("Name", true, "Region number and name under the map");
    }

    // ---- data ----------------------------------------------------------------------------------

    private Path file() { return Config.dir().resolve("regions.txt"); }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, """
                        # DonutSMP region names, one per line: <number> = <name>
                        # Regions are numbered 1-81 from the north-west (-X, -Z) corner, left to right, top to bottom.
                        # Copy the names from the official DonutSMP region map, or use .region set <number> <name>.
                        # Known names: EU Central, EU West, NA East, NA West, Asia, Oceania
                        """);
            }
            for (String line : Files.readAllLines(f)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                try {
                    names.put(Integer.parseInt(line.substring(0, eq).strip()), line.substring(eq + 1).strip());
                } catch (NumberFormatException ignored) {
                    // skip bad lines
                }
            }
        } catch (Exception e) {
            Chat.error("Couldn't read regions.txt: " + e.getMessage());
        }
    }

    private void save() {
        try {
            StringBuilder sb = new StringBuilder("# DonutSMP region names: <number> = <name> (1-81, from the north-west corner)\n");
            for (var e : names.entrySet()) sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            Files.writeString(file(), sb.toString());
        } catch (Exception e) {
            Chat.error("Couldn't save regions.txt: " + e.getMessage());
        }
    }

    /** Region number 1-81 for a block position, or -1 outside the grid. */
    public static int cell(double x, double z) {
        int cx = (int) Math.floor((x + HALF) / CELL), cz = (int) Math.floor((z + HALF) / CELL);
        if (cx < 0 || cz < 0 || cx >= GRID || cz >= GRID) return -1;
        return cz * GRID + cx + 1;
    }

    public String name(int cell) {
        load();
        return names.getOrDefault(cell, "");
    }

    public void setName(int cell, String name) {
        load();
        if (name.isBlank()) names.remove(cell); else names.put(cell, name.strip());
        save();
    }

    /** For .region: a description of where you are. */
    public String describe() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return "Not in a world.";
        if (p.level().dimension() != Level.OVERWORLD) return "Regions are only mapped in the overworld.";
        int c = cell(p.getX(), p.getZ());
        if (c < 0) return "Outside the region grid.";
        int cx = (c - 1) % GRID, cz = (c - 1) / GRID;
        int x0 = cx * CELL - HALF, z0 = cz * CELL - HALF;
        String n = name(c);
        return "Region " + c + (n.isEmpty() ? "" : " (" + n + ")") + String.format(Locale.ROOT,
                "  -  X %,d to %,d, Z %,d to %,d", x0, x0 + CELL, z0, z0 + CELL);
    }

    private static final Map<String, Integer> COLORS = new HashMap<>(Map.of(
            "eu central", 0xFF5B8CFF, "eu west", 0xFF46C2E8, "na east", 0xFFFF6B8A,
            "na west", 0xFFFFA04D, "asia", 0xFF6EE08A, "oceania", 0xFFC58CFF));

    private static int colorOf(String name) {
        if (name.isEmpty()) return 0xFF3A3A4A;
        Integer c = COLORS.get(name.toLowerCase(Locale.ROOT));
        return c != null ? c : Draw.hsv((name.hashCode() & 0xFFFF) / 65535f, 0.5f, 0.9f);
    }

    // ---- tick / HUD ----------------------------------------------------------------------------

    private boolean shown(Minecraft mc) {
        if (!isEnabled() || mc.player == null) return false;
        if (!onlyDonut.get()) return true;
        var sd = mc.getCurrentServer();
        return sd != null && sd.ip != null && sd.ip.toLowerCase(Locale.ROOT).contains("donutsmp");
    }

    @Override
    public void onTick(Minecraft mc) {
        if (!announce.get() || !shown(mc) || mc.level.dimension() != Level.OVERWORLD) return;
        int c = cell(mc.player.getX(), mc.player.getZ());
        if (c != lastCell && lastCell != -1 && c > 0) {
            String n = name(c);
            Chat.info("Entered region " + c + (n.isEmpty() ? "" : " - " + n));
        }
        lastCell = c;
    }

    private int[] paint(GuiGraphicsExtractor g, int x, int y, boolean right, boolean dry) {
        Minecraft mc = Minecraft.getInstance();
        HudModule hud = ModuleManager.HUD;
        if (!shown(mc) && !hud.editing) return new int[] {0, 0};
        boolean over = mc.level != null && mc.level.dimension() == Level.OVERWORLD;
        int s = size.getInt();
        int cs = Math.max(4, s / GRID), map = cs * GRID, pad = 5;
        int c = over && mc.player != null ? cell(mc.player.getX(), mc.player.getZ()) : -1;
        String label = !over ? "Overworld only" : c < 0 ? "Outside the grid"
                : "Region " + c + (name(c).isEmpty() ? "" : "  " + name(c));
        int w = map + pad * 2, h = map + pad * 2 + (legend.get() ? 12 : 0);
        w = Math.max(w, legend.get() ? Fonts.width(label) + 10 : 0);
        if (dry) return new int[] {w, h};

        int r = hud.cornerRadius();
        Draw.round(g, x, y, x + w, y + h, Math.max(r, 6), hud.bgColor());
        int mx = x + (w - map) / 2, my = y + pad;
        // cells: small round tiles coloured by region
        for (int i = 0; i < GRID * GRID; i++) {
            int gx = i % GRID, gz = i / GRID;
            int x0 = mx + gx * cs, y0 = my + gz * cs;
            int col = colorOf(name(i + 1));
            boolean here = i + 1 == c;
            Draw.round(g, x0 + 1, y0 + 1, x0 + cs - 1, y0 + cs - 1, 99, Draw.withAlpha(col, here ? 1f : 0.45f));
            if (numbers.get() && cs >= 9) {
                String n = String.valueOf(i + 1);
                g.pose().pushMatrix();
                g.pose().translate(x0 + cs / 2f, y0 + cs / 2f - 2f);
                g.pose().scale(0.5f, 0.5f);
                Fonts.text(g, n, -Fonts.width(n) / 2, 0, 0xE0FFFFFF);
                g.pose().popMatrix();
            }
        }
        // you
        if (over && mc.player != null) {
            double fx = (mc.player.getX() + HALF) / (CELL * (double) GRID), fz = (mc.player.getZ() + HALF) / (CELL * (double) GRID);
            int px = mx + (int) Math.round(Math.max(0, Math.min(1, fx)) * map);
            int py = my + (int) Math.round(Math.max(0, Math.min(1, fz)) * map);
            float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 350.0));
            Draw.round(g, px - 3, py - 3, px + 3, py + 3, 99, Draw.withAlpha(0xFFFFFFFF, pulse));
            Draw.round(g, px - 2, py - 2, px + 2, py + 2, 99, hud.accent(0f));
            // heading
            double yaw = Math.toRadians(mc.player.getYRot());
            int hx = px + (int) Math.round(-Math.sin(yaw) * 6), hy = py + (int) Math.round(Math.cos(yaw) * 6);
            Draw.line(g, px, py, hx, hy, 1f, 0xFFFFFFFF);
        }
        if (legend.get()) Fonts.centered(g, label, x + w / 2, y + map + pad + 2, 0xFFE6E6EE, false);
        return new int[] {w, h};
    }
}
