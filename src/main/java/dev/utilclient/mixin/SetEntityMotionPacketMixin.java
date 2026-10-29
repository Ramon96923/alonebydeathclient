package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Velocity: knockback from hits. Wraps the packet's own handle() so no listener method name is needed. */
@Mixin(ClientboundSetEntityMotionPacket.class)
public abstract class SetEntityMotionPacketMixin {
    @Inject(method = "handle", at = @At("HEAD"))
    private void abd$head(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ClientboundSetEntityMotionPacket self = (ClientboundSetEntityMotionPacket) (Object) this;
        if (dev.utilclient.UtilClient.isMainThread() && mc.player != null && self.id() == mc.player.getId()) {
            ModuleManager.VELOCITY.remember(mc.player.getDeltaMovement());
        }
    }

    @Inject(method = "handle", at = @At("RETURN"))
    private void abd$return(CallbackInfo ci) {
        ModuleManager.VELOCITY.applyAfterPacket(false);
    }
}
