package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: don't draw your hand floating in front of the free camera. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFreecamMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void abd$hideHand(CallbackInfo ci) {
        if (ModuleManager.FREECAM.hidesHand()) ci.cancel();
    }
}
