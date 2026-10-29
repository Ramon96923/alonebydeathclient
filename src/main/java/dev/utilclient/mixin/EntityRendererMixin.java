package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    /** Recolour the glow outline right after vanilla sets it (opcode 181 = PUTFIELD). */
    @Inject(method = "extractRenderState",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;outlineColor:I",
                    opcode = 181, shift = At.Shift.AFTER))
    private void abd$outline(T entity, S state, float partialTicks, CallbackInfo ci) {
        int color = ModuleManager.ESP.colorFor(entity);
        if (color != -1) state.outlineColor = 0xFF000000 | color;
    }

    /** Hide vanilla name tags while our Nametags module draws its own. */
    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true)
    private void abd$nameTag(T entity, CallbackInfoReturnable<Component> cir) {
        if (ModuleManager.NAMETAGS.replaces(entity)) cir.setReturnValue(null);
    }
}
