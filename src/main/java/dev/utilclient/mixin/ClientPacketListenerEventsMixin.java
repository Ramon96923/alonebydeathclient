package dev.utilclient.mixin;

import dev.utilclient.UtilClient;
import dev.utilclient.module.ModuleManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PopCounter: totem pops arrive as entity event 35. Packet handlers run once on the network thread (which
 * only schedules the work) and again on the main thread, so only the main-thread call is used.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerEventsMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void abd$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (!UtilClient.isMainThread()) return;
        try {
            ModuleManager.POP_COUNTER.onEntityEvent(packet);
        } catch (Throwable ignored) {
            // never break packet handling
        }
    }
}
