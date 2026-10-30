package dev.utilclient.module;

import dev.utilclient.util.Moves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Easier, faster elytra flight without fireworks — the highway-travel staple on anarchy servers.
 * <ul>
 *   <li><b>Control</b>: W/S speed you up or slow you down, Space/Shift climb or dive (feels like vanilla).</li>
 *   <li><b>Cruise</b>: fly at a fixed speed wherever you look, and hover when no key is held.</li>
 * </ul>
 * Instant takeoff starts gliding the moment you jump, no double-tap. Idea after Wurst's ExtraElytra (GPL-3.0).
 */
public final class ElytraFlyModule extends Module {
    private final ModeSetting mode = mode("Mode", "Control", "Control", "Cruise");
    private final BoolSetting instant = bool("Instant takeoff", true).desc("Start gliding as soon as you jump");
    private final NumberSetting accel = num("Acceleration", 0.05, 0.01, 0.2, 0.01).visible(() -> mode.is("Control"));
    private final NumberSetting speed = num("Speed", 1.8, 0.5, 5.0, 0.1).unit("b/t").desc("Top horizontal speed");
    private final NumberSetting vSpeed = num("Vertical speed", 0.6, 0.1, 2.0, 0.1).unit("b/t").visible(() -> mode.is("Cruise"));
    private final BoolSetting heightCtrl = bool("Height control", true).desc("Space climbs, Shift dives")
            .visible(() -> mode.is("Control"));
    private final BoolSetting stopInWater = bool("Stop in water", true);

    private int takeoffCooldown;

    public ElytraFlyModule() { super("ElytraFly", "Fly an elytra without fireworks", Category.MOVEMENT); }

    @Override public String info() { return mode.get(); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (takeoffCooldown > 0) takeoffCooldown--;
        if (!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA)
                || p.isPassenger()) return;

        if (!p.isFallFlying()) {
            if (instant.get() && mc.options.keyJump.isDown() && takeoffCooldown == 0) {
                takeoffCooldown = 20;
                p.setJumping(false);
                p.setSprinting(true);
                p.jumpFromGround();
                startGliding(p);
            }
            return;
        }
        if (stopInWater.get() && p.isInWater()) { startGliding(p); return; }   // the same packet toggles gliding off

        if (mode.is("Cruise")) cruise(mc, p);
        else control(mc, p);
    }

    private static void startGliding(LocalPlayer p) {
        p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }

    private void control(Minecraft mc, LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        double yaw = Math.toRadians(p.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).scale(accel.get());
        if (mc.options.keyUp.isDown()) v = v.add(fwd);
        else if (mc.options.keyDown.isDown()) v = v.subtract(fwd);
        if (heightCtrl.get()) {
            boolean up = mc.options.keyJump.isDown(), down = mc.options.keyShift.isDown();
            if (up && !down) v = v.add(0, 0.08, 0);
            else if (down && !up) v = v.add(0, -0.04, 0);
        }
        double h = Math.sqrt(v.x * v.x + v.z * v.z), max = speed.get();
        if (h > max) v = new Vec3(v.x / h * max, v.y, v.z / h * max);
        p.setDeltaMovement(v);
    }

    private void cruise(Minecraft mc, LocalPlayer p) {
        Vec3 dir = Moves.inputDir(mc, p.getYRot());
        double vy = 0;
        if (mc.options.keyJump.isDown()) vy = vSpeed.get();
        else if (mc.options.keyShift.isDown()) vy = -vSpeed.get();
        p.setDeltaMovement(dir.x * speed.get(), vy, dir.z * speed.get());
    }
}
