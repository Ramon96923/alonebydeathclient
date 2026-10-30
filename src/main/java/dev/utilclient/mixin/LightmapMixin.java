/*
 * Full-brightness lightmap adapted from Meteor Client (https://github.com/MeteorDevelopment/meteor-client),
 * Copyright (c) Meteor Development, GPL-3.0.
 */
package dev.utilclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.utilclient.module.ModuleManager;
import net.minecraft.client.renderer.Lightmap;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Xray and Fullbright (Gamma mode): paint the whole lightmap white so everything is fully lit. */
@Mixin(Lightmap.class)
public abstract class LightmapMixin {
    @Shadow
    @Final
    private GpuTexture texture;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void abd$fullbright(CallbackInfo ci) {
        if (ModuleManager.FULLBRIGHT.gamma() || ModuleManager.XRAY.isEnabled()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, new Vector4f(1));
            ci.cancel();
        }
    }
}
