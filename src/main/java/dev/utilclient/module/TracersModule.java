package dev.utilclient.module;

import dev.utilclient.core.Friends;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lines from your screen to nearby players and mobs. */
public final class TracersModule extends Module {
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting friends = bool("Friends", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", false);
    private final ModeSetting origin = mode("From", "Crosshair", "Crosshair", "Bottom", "Top");
    private final ModeSetting colorMode = mode("Colour", "ESP", "ESP", "Distance").desc("ESP colours, or red near / green far");
    private final NumberSetting width = num("Width", 1.0, 0.5, 3.0, 0.5);
    private final NumberSetting opacity = num("Opacity", 200, 40, 255, 5);
    private final NumberSetting range = num("Range", 96, 16, 256, 8).unit("m");
    private final NumberSetting max = num("Max lines", 20, 1, 60, 1);
    private final BoolSetting boxes = bool("Boxes", false);

    private List<Entity> targets = List.of();

    public TracersModule() { super("Tracers", "Lines to players and mobs", Category.RENDER); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        List<Entity> list = new ArrayList<>();
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(range.get()), e -> want(p, e))) list.add(e);
        list.sort(Comparator.comparingDouble(p::distanceToSqr));
        targets = list.size() > max.getInt() ? new ArrayList<>(list.subList(0, max.getInt())) : list;
    }

    private boolean want(LocalPlayer p, Entity e) {
        if (e instanceof Player && Friends.isFriend(e)) return friends.get() && e.isAlive();
        return TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), false);
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        if (targets.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Projection pr = new Projection(mc, g.guiWidth(), g.guiHeight());
        double[] o = new double[3];
        int[] rect = new int[4];
        double ox = pr.width / 2.0;
        double oy = switch (origin.get()) {
            case "Bottom" -> pr.height;
            case "Top" -> 0;
            default -> pr.height / 2.0;
        };
        for (Entity e : targets) {
            if (e.isRemoved()) continue;
            double x = Mth.lerp(pt, e.xOld, e.getX()), y = Mth.lerp(pt, e.yOld, e.getY()), z = Mth.lerp(pt, e.zOld, e.getZ());
            double h = e.getBbHeight(), hw = e.getBbWidth() / 2.0;
            int rgb;
            if (colorMode.is("Distance")) {
                float d = (float) Math.min(1.0, mc.player.distanceTo(e) / 60.0);
                rgb = Draw.hsv(d * 0.33f, 0.85f, 1f) & 0xFFFFFF;
            } else {
                rgb = ModuleManager.ESP.colorOf(e);
            }
            int c = Draw.alpha(rgb, opacity.getInt());
            pr.projectClamped(x, y + h / 2.0, z, o);
            Draw.line(g, ox, oy, o[0], o[1], width.getFloat(), c);
            if (boxes.get() && pr.boxRect(x - hw, y, z - hw, x + hw, y + h, z + hw, rect)) {
                Draw.outline(g, rect[0], rect[1], rect[2], rect[3], c);
            }
        }
    }
}
