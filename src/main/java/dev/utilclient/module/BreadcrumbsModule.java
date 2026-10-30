package dev.utilclient.module;

import dev.utilclient.render.WorldRender;
import dev.utilclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Draws a trail of where you've been, so you can find your way back (stash hunting, nether highways). */
public final class BreadcrumbsModule extends Module {
    private final ColorSetting color = color("Colour", 0xFF22D3EE);
    private final NumberSetting length = num("Length", 1000, 50, 5000, 50).desc("How many points to keep");
    private final NumberSetting spacing = num("Spacing", 0.5, 0.1, 5.0, 0.1).unit("b").desc("Distance between points");
    private final BoolSetting fade = bool("Fade", true).desc("Older parts of the trail are fainter");

    private final ArrayDeque<Vec3> points = new ArrayDeque<>();
    private ClientLevel level;

    public BreadcrumbsModule() { super("Breadcrumbs", "Leave a trail behind you", Category.RENDER); }

    @Override protected void onDisable() { points.clear(); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != level) { level = mc.level; points.clear(); }   // trails don't cross dimensions
        Vec3 at = mc.player.position().add(0, 0.1, 0);
        Vec3 last = points.peekLast();
        if (last == null || last.distanceToSqr(at) >= spacing.get() * spacing.get()) points.addLast(at);
        while (points.size() > length.getInt()) points.removeFirst();
    }

    @Override
    public void renderWorld(WorldRender.Ctx ctx) {
        if (points.size() < 2) return;
        int rgb = color.get() & 0xFFFFFF;
        int n = points.size(), i = 0;
        Iterator<Vec3> it = points.iterator();
        Vec3 prev = it.next();
        while (it.hasNext()) {
            Vec3 cur = it.next();
            i++;
            int a = fade.get() ? 40 + (int) (200 * (i / (float) n)) : 220;
            ctx.line(prev, cur, Draw.alpha(rgb, a));
            prev = cur;
        }
    }
}
