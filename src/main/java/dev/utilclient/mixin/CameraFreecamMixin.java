/*
 * Freecam camera hooks modelled on Meteor Client (GPL-3.0).
 */
package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Camera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Freecam: put the camera at the free position/rotation, detached from the player. */
@Mixin(Camera.class)
public abstract class CameraFreecamMixin {
    @Shadow
    private boolean detached;

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void abd$freecamPos(Args args) {
        var fc = ModuleManager.FREECAM;
        if (!fc.active()) return;
        fc.frame();   // advance by real frame time: smooth even when game ticks lag
        args.set(0, fc.camX());
        args.set(1, fc.camY());
        args.set(2, fc.camZ());
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void abd$freecamRot(Args args) {
        var fc = ModuleManager.FREECAM;
        if (!fc.active()) return;
        args.set(0, fc.yaw());
        args.set(1, fc.pitch());
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void abd$freecamDetach(CallbackInfo ci) {
        if (ModuleManager.FREECAM.active()) detached = true;   // render your own body
    }

    /** Render the world as a spectator would, so it looks right when the free camera is inside blocks. */
    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
    private boolean abd$freecamSpectator(boolean original) {
        return original || ModuleManager.FREECAM.active();
    }
}
