package dev.utilclient.module;

import dev.utilclient.util.Moves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Flies by setting your velocity from the movement keys: WASD to move, Space up, Shift down.
 * Anti-kick dips you slightly every couple of seconds so vanilla's "flying is not enabled" check doesn't fire.
 */
public final class FlightModule extends Module {
    private final NumberSetting speed = num("Speed", 1.0, 0.1, 5.0, 0.1).unit("b/t");
    private final NumberSetting vSpeed = num("Vertical speed", 0.5, 0.1, 3.0, 0.1).unit("b/t");
    private final BoolSetting antiKick = bool("Anti-kick", true).desc("Drop a tiny bit every 2 seconds");

    private int ticks;

    public FlightModule() { super("Flight", "Fly in survival", Category.MOVEMENT); }

    @Override protected void onEnable() { ticks = 0; }

    @Override
    protected void onDisable() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.setDeltaMovement(Vec3.ZERO);   // don't fling yourself on release
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p.isPassenger() || p.isFallFlying()) return;
        Vec3 dir = Moves.inputDir(mc, p.getYRot());
        double vy = 0;
        if (mc.options.keyJump.isDown()) vy += vSpeed.get();
        if (mc.options.keyShift.isDown()) vy -= vSpeed.get();
        if (antiKick.get() && ++ticks % 40 == 0 && vy >= 0) vy = -0.04;
        p.setDeltaMovement(dir.x * speed.get(), vy, dir.z * speed.get());
        p.fallDistance = 0;
    }
}
