package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** HUD list (top-right) of the nearest mobs/players with distance and direction relative to where you face. */
public final class TrackerModule extends Module {
    private final BoolSetting players = add("Players", true);
    private final BoolSetting mobs    = add("Mobs", true);
    private final NumberSetting range = num("Range", 48, 16, 128, 16);
    private final NumberSetting max   = num("Max shown", 6, 1, 12, 1);

    private static final String[] DIRS = {"Ahead", "Ahead R", "Right", "Behind R", "Behind", "Behind L", "Left", "Ahead L"};

    public TrackerModule() { super("Tracker", "List nearby mobs & players"); }

    private boolean accept(Entity e, LocalPlayer p) {
        if (e == p || e.isSpectator() || !(e instanceof net.minecraft.world.entity.LivingEntity le) || !le.isAlive()) return false;
        return switch (EntityClass.of(e)) {
            case PLAYER -> players.get();
            case HOSTILE, PASSIVE -> mobs.get();
            case OTHER -> false;
        };
    }

    private static String direction(LocalPlayer p, Entity e) {
        double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = Mth.wrapDegrees(yaw - p.getYRot());
        int idx = (int) Math.floor(((diff + 22.5 + 360.0) % 360.0) / 45.0);
        return DIRS[Math.floorMod(idx, 8)];
    }

    /** Called from the HUD element registered in UtilClient. */
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!isEnabled() || p == null || mc.level == null || mc.options.hideGui) return;

        List<Entity> found = new ArrayList<>(mc.level.getEntities(p, p.getBoundingBox().inflate(range.get()), e -> accept(e, p)));
        found.sort(Comparator.comparingDouble(p::distanceToSqr));
        int n = Math.min(found.size(), max.getInt());

        Font font = mc.font;
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add("Tracker (" + found.size() + ")");
        colors.add(0xFFFFFFFF);
        for (int i = 0; i < n; i++) {
            Entity e = found.get(i);
            lines.add(e.getName().getString() + "  " + Math.round(p.distanceTo(e)) + "m  " + direction(p, e));
            colors.add(switch (EntityClass.of(e)) {
                case PLAYER -> 0xFF4DA3FF;
                case HOSTILE -> 0xFFFF4D4D;
                default -> 0xFF57F287;
            });
        }

        int w = 0;
        for (String s : lines) w = Math.max(w, font.width(s));
        int x = mc.getWindow().getGuiScaledWidth() - w - 10;
        int y = 6;
        g.fill(x - 4, y - 3, x + w + 4, y + lines.size() * 11 + 1, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x, y + i * 11, colors.get(i), true);
        }
    }
}
