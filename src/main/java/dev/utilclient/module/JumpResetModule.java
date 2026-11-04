package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The legit way to take less knockback: jump the instant you get hit, like a good PvPer. Unlike Velocity it
 * doesn't change your motion at all, so anticheats see a normal jump.
 */
public final class JumpResetModule extends Module {
    private final NumberSetting chance = num("Chance", 80, 10, 100, 5).unit("%").desc("How often to jump-reset (100 looks robotic)");

    private int prevHurt;

    public JumpResetModule() { super("JumpReset", "Jump on hit to cut knockback (legit)", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean justHit = p.hurtTime > prevHurt;   // the hurt timer restarts when a hit lands
        prevHurt = p.hurtTime;
        if (!justHit || !p.onGround() || p.isInWater() || mc.gui.screen() != null) return;
        if (ThreadLocalRandom.current().nextInt(100) < chance.getInt()) p.jumpFromGround();
    }
}
