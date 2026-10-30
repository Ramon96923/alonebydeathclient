/*
 * Freecam camera hooks modelled on Meteor Client (GPL-3.0).
 */
package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Camera;
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
        float pt = fc.partial();
        args.set(0, fc.camX(pt));
        args.set(1, fc.camY(pt));
        args.set(2, fc.camZ(pt));
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
}
