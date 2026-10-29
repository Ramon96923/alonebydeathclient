package dev.utilclient.module;

import dev.utilclient.core.Friends;
import dev.utilclient.gui.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** On-screen list of the nearest players/mobs with distance and direction. */
public final class TrackerModule extends Module {
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting mobs = bool("Mobs", true);
    private final NumberSetting range = num("Range", 64, 16, 256, 16).unit("m");
    private final NumberSetting max = num("Max shown", 6, 1, 15, 1);
    private final ModeSetting side = mode("Side", "Left", "Left", "Right");
    private final NumberSetting yPos = num("Y position", 60, 0, 400, 5);

    private static final String[] DIRS = {"Ahead", "Ahead R", "Right", "Behind R", "Behind", "Behind L", "Left", "Ahead L"};

    public TrackerModule() { super("Tracker", "List nearby players and mobs", Category.RENDER); }

    private boolean accept(Entity e, LocalPlayer p) {
        if (e == p || e.isSpectator() || !(e instanceof LivingEntity le) || !le.isAlive()) return false;
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

    @Override
    public void renderHud(GuiGraphicsExtractor g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        List<Entity> found = new ArrayList<>(mc.level.getEntities(p, p.getBoundingBox().inflate(range.get()), e -> accept(e, p)));
        found.sort(Comparator.comparingDouble(p::distanceToSqr));
        int n = Math.min(found.size(), max.getInt());

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add("Nearby (" + found.size() + ")");
        colors.add(ModuleManager.CLICK_GUI.accent(0f));
        for (int i = 0; i < n; i++) {
            Entity e = found.get(i);
            lines.add(String.format(Locale.ROOT, "%s  %dm  %s", e.getName().getString(), Math.round(p.distanceTo(e)), direction(p, e)));
            colors.add(0xFF000000 | (Friends.isFriend(e) ? 0x2DD4BF : ModuleManager.ESP.colorOf(EntityClass.of(e))));
        }
        int w = 0;
        for (String s : lines) w = Math.max(w, Fonts.width(s));
        int x = side.is("Right") ? g.guiWidth() - w - 8 : 6;
        int y = yPos.getInt();
        g.fill(x - 3, y - 2, x + w + 3, y + lines.size() * 10 + 1, 0x90000000);
        for (int i = 0; i < lines.size(); i++) Fonts.text(g, lines.get(i), x, y + i * 10, colors.get(i), i == 0);
    }
}
