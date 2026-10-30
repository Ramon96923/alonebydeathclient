package dev.utilclient.module;

import dev.utilclient.util.Moves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Moves you faster. Strafe bunny-hops and keeps your speed pointed wherever your keys say, so you can turn
 * in mid-air; Ground just speeds up walking without jumping.
 */
public final class SpeedModule extends Module {
    private final ModeSetting mode = mode("Mode", "Strafe", "Strafe", "Ground");
    private final NumberSetting speed = num("Speed", 0.36, 0.2, 1.5, 0.01).unit("b/t");
    private final BoolSetting autoJump = bool("Auto jump", true).desc("Hop automatically while moving").visible(() -> mode.is("Strafe"));
    private final BoolSetting inLiquids = bool("In liquids", false);

    public SpeedModule() { super("Speed", "Move faster", Category.MOVEMENT); }

    @Override public String info() { return mode.get(); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p.isPassenger() || p.isFallFlying() || p.getAbilities().flying || p.isShiftKeyDown()) return;
        if (!inLiquids.get() && (p.isInWater() || p.isInLava())) return;
        if (ModuleManager.FLIGHT.isEnabled()) return;
        if (!Moves.moving(mc)) return;

        Vec3 dir = Moves.inputDir(mc, p.getYRot());
        Vec3 v = p.getDeltaMovement();
        if (mode.is("Ground")) {
            if (p.onGround()) p.setDeltaMovement(dir.x * speed.get(), v.y, dir.z * speed.get());
            return;
        }
        if (p.onGround() && autoJump.get() && !mc.options.keyJump.isDown()) p.jumpFromGround();
        v = p.getDeltaMovement();
        p.setDeltaMovement(dir.x * speed.get(), v.y, dir.z * speed.get());
    }
}
