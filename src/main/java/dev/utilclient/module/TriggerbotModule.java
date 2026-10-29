package dev.utilclient.module;

import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

/** Attacks only when your crosshair is exactly on a valid target (unlike AutoHit, which picks targets itself). */
public final class TriggerbotModule extends Module {
    private final BoolSetting players  = add("Players", true);
    private final BoolSetting hostile  = add("Hostile mobs", true);
    private final BoolSetting passive  = add("Passive mobs", false);
    private final BoolSetting spare    = add("Spare pets", true);
    private final BoolSetting cooldown = add("Cooldown", true);
    private final NumberSetting delay  = num("Delay (ticks)", 0, 0, 10, 1);
    private int waited;

    public TriggerbotModule() { super("Triggerbot", "Attack what your crosshair is on", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.gui.screen() != null || p.isSpectator()) return;
        if (!(mc.hitResult instanceof EntityHitResult hit)) { waited = 0; return; }
        Entity e = hit.getEntity();
        if (!TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), spare.get())) { waited = 0; return; }
        if (cooldown.get() && p.getAttackStrengthScale(0.0f) < 1.0f) return;
        if (waited++ < delay.getInt()) return;
        mc.gameMode.attack(p, e);
        waited = 0;
    }
}
