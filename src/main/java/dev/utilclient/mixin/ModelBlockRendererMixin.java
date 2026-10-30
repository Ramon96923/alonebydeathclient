/*
 * Xray hooks modelled on Meteor Client and Wurst Client (both GPL-3.0).
 */
package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Vanilla block renderer: skip non-Xray blocks entirely and draw every face of Xray blocks. */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("HEAD"), cancellable = true)
    private void abd$xraySkip(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts,
                              BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (ModuleManager.XRAY.isEnabled() && !ModuleManager.XRAY.isTarget(state)) ci.cancel();
    }

    @Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true)
    private static void abd$xrayFaces(BlockAndTintGetter level, BlockState state, Direction direction, BlockPos neighborPos,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.XRAY.isEnabled()) cir.setReturnValue(ModuleManager.XRAY.isTarget(state));
    }
}
