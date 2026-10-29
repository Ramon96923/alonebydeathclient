package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: camera shake when you take damage. Skipped quietly if this version renamed it. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void abd$hurtCam(CallbackInfo ci) {
        if (ModuleManager.NO_RENDER.noHurtCam()) ci.cancel();
    }
}
