package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Makes your hits critical.
 *
 * <p><b>Mini Jump</b> (default) and <b>Jump</b> use real movement only. The hit is held back, you hop (a tiny
 * 0.1-block hop, or a normal jump), and the hit is sent once you're falling — the moment the server itself
 * counts a crit. No spoofed position packets are sent, so these never cause the
 * "Invalid move player packet received" kick. (Delaying the hit like this follows Meteor's MiniJump.)
 *
 * <p><b>Packet</b> / <b>NCP</b> spoof a small vertical offset instead (sequences adapted from Wurst Client and
 * LiquidBounce, GPL-3.0). They crit instantly but some servers reject spoofed movement and kick you.
 */
public final class CriticalsModule extends Module {
    private final ModeSetting mode = mode("Mode", "Mini Jump", "Mini Jump", "Jump", "Packet", "NCP")
            .desc("Mini Jump / Jump: real hop, safe everywhere. Packet / NCP: instant but can get you kicked");
    private final BoolSetting stopSprint = bool("Stop sprint", true)
            .desc("Sprinting cancels crits, so stop sprinting for the hit (you re-sprint right after)");
    private final BoolSetting onlyCharged = bool("Only charged hits", true)
            .desc("Only crit fully charged swings (fewer hops, and spam clicks stay normal)");
    private final BoolSetting yieldToMace = bool("Yield to Mace", true)
            .desc("Leave mace hits to the Mace module");

    private Entity pending;
    private int waited;
    private boolean replaying;

    public CriticalsModule() { super("Criticals", "Every hit is a critical hit", Category.COMBAT); }

    @Override public String info() { return mode.get(); }

    @Override protected void onDisable() { pending = null; }

    /** True while a held-back crit hit is waiting for you to fall. AutoHit/Triggerbot pause meanwhile. */
    public boolean waiting() { return isEnabled() && pending != null; }

    /** The vanilla conditions for a crit, minus being airborne (we make that happen). */
    private static boolean canCrit(LocalPlayer p) {
        return !p.isInWater() && !p.isInLava() && !p.onClimbable() && !p.isPassenger()
                && !p.isFallFlying() && !p.getAbilities().flying;
    }

    /**
     * Called from the attack mixin before the attack is sent.
     * @return true to cancel this attack: it is replayed as a crit once you're falling.
     */
    public boolean beforeAttack(Entity target) {
        if (replaying) return false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.connection == null) return false;
        if (pending != null) return true;                    // a crit is on its way: swallow extra clicks
        if (!(target instanceof LivingEntity)) return false; // crystals, boats... crits don't apply
        if (yieldToMace.get() && ModuleManager.MACE.isEnabled() && p.getMainHandItem().is(Items.MACE)) return false;
        if (!p.onGround() || !canCrit(p)) return false;     // already airborne, or crits impossible here
        if (onlyCharged.get() && p.getAttackStrengthScale(0.5f) < 0.9f) return false;

        switch (mode.get()) {
            case "Packet" -> {
                unsprint(p);
                sendY(p, 0.0625, true);
                sendY(p, 0.0, false);
                sendY(p, 1.1e-5, false);
                sendY(p, 0.0, false);
                return false;
            }
            case "NCP" -> {
                unsprint(p);
                sendY(p, 0.11, false);
                sendY(p, 0.1100013579, false);
                sendY(p, 0.0000013579, false);
                return false;
            }
            case "Jump" -> p.jumpFromGround();
            default -> {   // Mini Jump: set the upward speed directly so the hop clears the ground
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, 0.1, v.z);
            }
        }
        pending = target;
        waited = 0;
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (pending == null) return;
        LocalPlayer p = mc.player;
        Entity t = pending;
        if (mc.gameMode == null || !t.isAlive() || t.isRemoved()) {
            pending = null;
            return;
        }
        boolean falling = !p.onGround() && p.getDeltaMovement().y < 0 && p.fallDistance > 0;
        boolean timedOut = ++waited > (mode.is("Jump") ? 20 : 10);   // e.g. a ceiling stopped the hop
        if (!falling && !timedOut) return;

        pending = null;
        if (p.distanceTo(t) > 6.5) return;   // walked out of reach while hopping
        if (falling) unsprint(p);
        replaying = true;
        try {
            mc.gameMode.attack(p, t);
            p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
        } finally {
            replaying = false;
        }
    }

    private void unsprint(LocalPlayer p) {
        if (!stopSprint.get() || !p.isSprinting()) return;
        p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        p.setSprinting(false);
    }

    private static void sendY(LocalPlayer p, double up, boolean onGround) {
        p.connection.send(new ServerboundMovePlayerPacket.Pos(
                p.getX(), p.getY() + up, p.getZ(), onGround, p.horizontalCollision));
    }
}
