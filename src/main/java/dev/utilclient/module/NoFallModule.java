package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Tells the server you landed while falling so fall damage never builds up. */
public final class NoFallModule extends Module {
    private final NumberSetting minFall = num("Min fall", 2.5, 2.0, 10.0, 0.5).unit("b").desc("Start protecting after this distance");
    private final BoolSetting elytra = bool("While flying", false).desc("Also while gliding with an elytra");

    public NoFallModule() { super("NoFall", "No fall damage", Category.MOVEMENT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p.connection == null || p.getAbilities().mayfly || p.onGround()) return;
        if (!elytra.get() && p.isFallFlying()) return;
        if (p.fallDistance >= minFall.get() && p.getDeltaMovement().y < 0) {
            p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
        }
    }
}
