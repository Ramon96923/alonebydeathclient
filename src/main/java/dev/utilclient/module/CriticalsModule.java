package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Makes your hits critical.
 * Packet / Strict: send a tiny fake hop right before the hit (works on every attack, even manual ones).
 * Jump: AutoHit and Triggerbot really jump and hit on the way down (most legit-looking).
 */
public final class CriticalsModule extends Module {
    private final ModeSetting mode = mode("Mode", "Packet", "Packet", "Strict", "Jump")
            .desc("Packet: classic offset. Strict: tiny offset for stricter servers. Jump: real jumps");
    private final BoolSetting onlyLiving = bool("Only living targets", true).desc("Skip crystals, boats, etc.");

    public CriticalsModule() { super("Criticals", "Every hit is a critical hit", Category.COMBAT); }

    @Override public String info() { return mode.get(); }

    private static boolean canCrit(LocalPlayer p) {
        return p.onGround() && !p.isInWater() && !p.isInLava() && !p.onClimbable() && !p.isPassenger();
    }

    /** Called from the attack mixin just before the attack packet is sent. */
    public void beforeAttack(Entity target) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.connection == null || mode.is("Jump")) return;
        if (onlyLiving.get() && !(target instanceof LivingEntity)) return;
        if (!canCrit(p)) return;
        if (mode.is("Strict")) {
            send(p, 0.0011);
            send(p, 0.0);
        } else {
            send(p, 0.0625);
            send(p, 0.0);
        }
    }

    private static void send(LocalPlayer p, double up) {
        p.connection.send(new ServerboundMovePlayerPacket.Pos(p.getX(), p.getY() + up, p.getZ(), false, p.horizontalCollision));
    }

    /**
     * Jump mode for AutoHit/Triggerbot: returns true while the hit should wait (jumping / rising),
     * false once you're falling and a crit will land.
     */
    public boolean holdForJump(LocalPlayer p) {
        if (!isEnabled() || !mode.is("Jump")) return false;
        if (p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()) return false;
        if (p.onGround()) {
            p.jumpFromGround();
            return true;
        }
        return p.getDeltaMovement().y >= 0 || p.fallDistance <= 0;
    }
}
