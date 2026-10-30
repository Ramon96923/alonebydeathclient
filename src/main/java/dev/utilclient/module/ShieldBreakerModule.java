package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** When your target is blocking with a shield, hits it with an axe (which disables the shield), then swaps back. */
public final class ShieldBreakerModule extends Module {
    private final NumberSetting swapBack = num("Swap back", 3, 1, 20, 1).unit("t");

    private int prev = -1, timer;

    public ShieldBreakerModule() { super("ShieldBreaker", "Axe-hit blocking targets to break shields", Category.COMBAT); }

    /** From the attack mixin, before the hit. */
    public void beforeAttack(Entity target) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !(target instanceof LivingEntity le) || !le.isBlocking()) return;
        if (p.getMainHandItem().is(ItemTags.AXES)) return;
        int axe = Inv.hotbar(p, s -> s.is(ItemTags.AXES));
        if (axe < 0) return;
        if (prev < 0) prev = Inv.selected(p);
        Inv.select(p, axe);
        timer = swapBack.getInt();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (prev >= 0 && timer-- <= 0) {
            Inv.select(mc.player, prev);
            prev = -1;
        }
    }

    @Override protected void onDisable() { prev = -1; }
}
