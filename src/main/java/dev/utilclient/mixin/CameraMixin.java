package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import dev.utilclient.util.RenderState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    /** Zoom, and remember the real FOV so ESP/tracer overlays line up. */
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void abd$fov(CallbackInfoReturnable<Float> cir) {
        float fov = cir.getReturnValue();
        if (ModuleManager.CUSTOM_FOV.isEnabled()) fov = ModuleManager.CUSTOM_FOV.apply(fov);
        if (ModuleManager.ZOOM.isEnabled()) fov = ModuleManager.ZOOM.apply(fov);
        RenderState.fov = fov;
        cir.setReturnValue(fov);
    }

    /** CustomFOV: hand / held item FOV. */
    @Inject(method = "calculateHudFov", at = @At("RETURN"), cancellable = true)
    private void dw$hudFov(CallbackInfoReturnable<Float> cir) {
        if (ModuleManager.CUSTOM_FOV.isEnabled()) cir.setReturnValue(ModuleManager.CUSTOM_FOV.applyHand(cir.getReturnValue()));
    }
}
