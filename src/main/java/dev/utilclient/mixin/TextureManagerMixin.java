package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GlintColor: hand out the client's tinted glint texture whenever the vanilla glint is asked for. */
@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
    @Inject(method = "getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;",
            at = @At("HEAD"), cancellable = true)
    private void abd$glint(Identifier id, CallbackInfoReturnable<AbstractTexture> cir) {
        Identifier swap;
        try {
            swap = ModuleManager.GLINT_COLOR.replace(id);
        } catch (Throwable t) {
            return;   // texture loading must never break
        }
        if (swap != null) cir.setReturnValue(((TextureManager) (Object) this).getTexture(swap));
    }
}
