package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Velocity: explosion knockback. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void abd$explodeHead(ClientboundExplodePacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (dev.utilclient.UtilClient.isMainThread() && mc.player != null) ModuleManager.VELOCITY.remember(mc.player.getDeltaMovement());
    }

    @Inject(method = "handleExplosion", at = @At("RETURN"))
    private void abd$explodeReturn(ClientboundExplodePacket packet, CallbackInfo ci) {
        ModuleManager.VELOCITY.applyAfterPacket(true);
    }
}
