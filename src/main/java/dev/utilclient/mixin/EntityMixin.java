package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void abd$glow(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.ESP.shouldGlow((Entity) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
    private void abd$hitbox(CallbackInfoReturnable<Float> cir) {
        float extra = ModuleManager.HITBOX.extraFor((Entity) (Object) this);
        if (extra > 0f) cir.setReturnValue(cir.getReturnValue() + extra);
    }
}
