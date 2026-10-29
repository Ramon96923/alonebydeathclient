package dev.utilclient.module;

import dev.utilclient.core.Friends;
import dev.utilclient.gui.Fonts;
import dev.utilclient.util.Draw;
import dev.utilclient.util.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Readable name tags through walls with health, distance, ping and gear. */
public final class NametagsModule extends Module {
    private static final EquipmentSlot[] GEAR = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    private final BoolSetting players = bool("Players", true);
    private final BoolSetting mobs = bool("Mobs", false);
    private final BoolSetting health = bool("Health", true);
    private final BoolSetting distance = bool("Distance", true);
    private final BoolSetting ping = bool("Ping", true);
    private final BoolSetting gear = bool("Gear", true).desc("Held items and armour above the tag");
    private final NumberSetting scale = num("Scale", 1.0, 0.5, 2.0, 0.1);
    private final BoolSetting background = bool("Background", true);
    private final NumberSetting range = num("Range", 128, 16, 512, 16).unit("m");

    private List<LivingEntity> tagged = List.of();

    public NametagsModule() { super("Nametags", "Better name tags with health and gear", Category.RENDER); }

    /** Vanilla tags are hidden for entities we draw ourselves. */
    public boolean replaces(Entity e) {
        if (!isEnabled() || e == Minecraft.getInstance().player) return false;
        return e instanceof Player ? players.get() : (mobs.get() && e instanceof LivingEntity);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        double r2 = range.get() * range.get();
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity le && replaces(e) && e.distanceToSqr(p) <= r2 && le.isAlive()) out.add(le);
        }
        out.sort(Comparator.comparingDouble(e -> -e.distanceToSqr(p)));   // far first, near drawn on top
        tagged = out;
    }

    @Override
    public void renderHud(GuiGraphicsExtractor g, float pt) {
        if (tagged.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Projection pr = new Projection(mc, g.guiWidth(), g.guiHeight());
        double[] o = new double[3];
        for (LivingEntity e : tagged) {
            if (e.isRemoved()) continue;
            double x = Mth.lerp(pt, e.xOld, e.getX());
            double y = Mth.lerp(pt, e.yOld, e.getY()) + e.getBbHeight() + 0.35;
            double z = Mth.lerp(pt, e.zOld, e.getZ());
            if (!pr.project(x, y, z, o) || !pr.onScreen(o)) continue;
            draw(g, mc, e, (float) o[0], (float) o[1]);
        }
    }

    private void draw(GuiGraphicsExtractor g, Minecraft mc, LivingEntity e, float sx, float sy) {
        String name = e.getName().getString();
        boolean friend = Friends.isFriend(e);
        StringBuilder extra = new StringBuilder();
        if (ping.get() && e instanceof Player pl && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(pl.getUUID());
            if (info != null) extra.append(' ').append(info.getLatency()).append("ms");
        }
        if (distance.get()) extra.append(String.format(Locale.ROOT, " %.0fm", mc.player.distanceTo(e)));
        String hp = health.get() ? String.format(Locale.ROOT, " %.1f", e.getHealth() + e.getAbsorptionAmount()) : "";
        int w = Fonts.width(name) + Fonts.width(hp) + Fonts.width(extra.toString()) + 8;
        int h = 12;
        float s = scale.getFloat();

        g.pose().pushMatrix();
        g.pose().translate(sx, sy);
        g.pose().scale(s, s);
        int x0 = -w / 2, y0 = -h;
        if (background.get()) Draw.soft(g, x0, y0, x0 + w, y0 + h, 0xB0101016);
        int tx = x0 + 4;
        Fonts.text(g, name, tx, y0 + 2, friend ? 0xFF2DD4BF : (e instanceof Player ? 0xFFFFFFFF : 0xFFFFB4B4));
        tx += Fonts.width(name);
        if (!hp.isEmpty()) {
            float frac = Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth()));
            Fonts.text(g, hp, tx, y0 + 2, Draw.healthColor(frac));
            tx += Fonts.width(hp);
        }
        if (extra.length() > 0) Fonts.text(g, extra.toString(), tx, y0 + 2, 0xFFA0A0B0);

        if (gear.get()) {
            List<ItemStack> stacks = new ArrayList<>();
            for (EquipmentSlot slot : GEAR) {
                ItemStack st = e.getItemBySlot(slot);
                if (!st.isEmpty()) stacks.add(st);
            }
            int ix = -stacks.size() * 9;
            for (ItemStack st : stacks) {
                g.item(st, ix, y0 - 18);
                g.itemDecorations(mc.font, st, ix, y0 - 18);
                ix += 18;
            }
        }
        g.pose().popMatrix();
    }
}
