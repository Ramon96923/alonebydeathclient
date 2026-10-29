package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import java.util.concurrent.ThreadLocalRandom;

/** Small random actions so servers don't kick you for being idle. */
public final class AntiAfkModule extends Module {
    private final NumberSetting interval = num("Interval", 15, 3, 120, 1).unit("s");
    private final BoolSetting jump = bool("Jump", true);
    private final BoolSetting spin = bool("Turn", true);
    private final BoolSetting swing = bool("Swing", true);
    private final BoolSetting sneak = bool("Sneak", false);

    private int ticks;
    private int sneakTicks;

    public AntiAfkModule() { super("AntiAFK", "Avoid idle kicks", Category.MISC); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (sneakTicks > 0 && --sneakTicks == 0) mc.options.keyShift.setDown(false);
        if (++ticks < interval.getInt() * 20) return;
        ticks = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (jump.get() && p.onGround()) p.jumpFromGround();
        if (spin.get()) p.setYRot(p.getYRot() + (float) r.nextDouble(-60, 60));
        if (swing.get()) p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
        if (sneak.get()) { mc.options.keyShift.setDown(true); sneakTicks = 5; }
    }

    @Override protected void onDisable() { Minecraft.getInstance().options.keyShift.setDown(false); }
}
