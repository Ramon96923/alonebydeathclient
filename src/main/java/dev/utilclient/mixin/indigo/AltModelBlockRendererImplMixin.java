/*
 * Xray hook for Fabric's Indigo renderer, modelled on Meteor Client (GPL-3.0).
 */
package dev.utilclient.mixin.indigo;

import dev.utilclient.module.ModuleManager;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric API replaces vanilla block rendering with Indigo, so Xray needs the same rules here. */
@SuppressWarnings("UnstableApiUsage")
@Mixin(AltModelBlockRendererImpl.class)
public abstract class AltModelBlockRendererImplMixin {
    @Shadow
    private BlockState blockState;

    @Inject(method = "shouldCullFace", at = @At("RETURN"), cancellable = true)
    private void abd$cull(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (direction == null || !ModuleManager.XRAY.isEnabled()) return;
        cir.setReturnValue(!ModuleManager.XRAY.isTarget(blockState));
    }

    @Inject(method = "transform", at = @At("RETURN"), cancellable = true)
    private void abd$transform(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.XRAY.isEnabled() && !ModuleManager.XRAY.isTarget(blockState)) cir.setReturnValue(false);
    }
}
