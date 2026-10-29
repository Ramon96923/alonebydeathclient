package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: fire and underwater overlays. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void abd$fire(CallbackInfo ci) {
        if (ModuleManager.NO_RENDER.noFire()) ci.cancel();
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void abd$water(CallbackInfo ci) {
        if (ModuleManager.NO_RENDER.noWaterOverlay()) ci.cancel();
    }
}
