package dev.utilclient.mixin;

import dev.utilclient.util.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Silent rotations ride along in the normal movement packet instead of extra packets. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerRotationMixin {
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void abd$rotPre(CallbackInfo ci) {
        RotationManager.preSend((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void abd$rotPost(CallbackInfo ci) {
        RotationManager.postSend((LocalPlayer) (Object) this);
    }
}
