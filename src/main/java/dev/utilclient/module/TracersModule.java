package dev.utilclient.module;

import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lines (and optional boxes) from your crosshair to nearby players and mobs. Colours follow the ESP colours. */
public final class TracersModule extends Module {
    private final BoolSetting players = add("Players", true);
    private final BoolSetting hostile = add("Hostile mobs", true);
    private final BoolSetting passive = add("Passive mobs", true);
    private final BoolSetting boxes = add("Boxes", false);
    private final BoolSetting fromBottom = add("From bottom", false);
    private final NumberSetting range = num("Range", 64, 16, 128, 16);
    private final NumberSetting max = num("Max lines", 15, 5, 40, 5);

    private List<Entity> targets = List.of();

    public TracersModule() { super("Tracers", "Lines to players and mobs", Category.RENDER); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        List<Entity> list = new ArrayList<>(mc.level.getEntities(p, p.getBoundingBox().inflate(range.get()),
                e -> TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), false)));
        list.sort(Comparator.comparingDouble(p::distanceToSqr));
        targets = list.size() > max.getInt() ? new ArrayList<>(list.subList(0, max.getInt())) : list;
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || targets.isEmpty()) return;
        Projection pr = new Projection(mc, pt);
        double[] o = new double[2];
        int[] rect = new int[4];
        int ox = pr.width / 2;
        int oy = fromBottom.get() ? pr.height : pr.height / 2;

        for (Entity e : targets) {
            if (e.isRemoved()) continue;
            int rgb = ModuleManager.ESP.colorOf(EntityClass.of(e));
            Vec3 pos = e.getPosition(pt);
            double hw = e.getBbWidth() / 2.0, h = e.getBbHeight();

            pr.projectClamped(pos.x, pos.y + h / 2.0, pos.z, o);
            Draw.line(g, ox, oy, (int) o[0], (int) o[1], 0xC0000000 | rgb);

            if (boxes.get() && pr.boxRect(pos.x - hw, pos.y, pos.z - hw, pos.x + hw, pos.y + h, pos.z + hw, rect)) {
                Draw.outline(g, rect[0], rect[1], Math.max(rect[2], rect[0] + 2), Math.max(rect[3], rect[1] + 2), 0xFF000000 | rgb);
            }
        }
    }
}
