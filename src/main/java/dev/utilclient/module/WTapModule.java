package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Sprint reset so every hit deals full sprint knockback. Legit lets go of sprint for a moment after a hit
 * (like tapping W); Packet re-sends sprint right before each hit (1.8-style, instant).
 */
public final class WTapModule extends Module {
    private final ModeSetting mode = mode("Mode", "Legit", "Legit", "Packet");

    private int resetTicks;

    public WTapModule() { super("WTap", "Sprint-reset for extra knockback", Category.COMBAT); }

    @Override public String info() { return mode.get(); }

    /** From the attack mixin, before the hit. */
    public void beforeAttack() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.isSprinting()) return;
        if (mode.is("Packet")) {
            p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        } else {
            resetTicks = 2;
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        if (resetTicks > 0) {
            resetTicks--;
            mc.player.setSprinting(false);   // you start sprinting again on your own (or with Sprint)
        }
    }
}
