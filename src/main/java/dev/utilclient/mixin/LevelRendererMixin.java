/*
 * World render hook adapted from Wurst Client (https://github.com/Wurst-Imperium/Wurst7),
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors, GPL-3.0.
 */
package dev.utilclient.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.utilclient.render.WorldRender;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
            at = @At("RETURN"))
    private void abd$render(GraphicsResourceAllocator allocator, boolean renderBlockOutline, CameraRenderState cameraState,
                            GpuBufferSlice gpuBufferSlice, Vector4f fogColor, boolean shouldRenderSky,
                            boolean consistentDepthRequired, CallbackInfo ci) {
        PoseStack stack = new PoseStack();
        stack.mulPose(cameraState.viewRotationMatrix);
        WorldRender.onLevelRender(stack, levelRenderState.worldPartialTicks);
    }
}
