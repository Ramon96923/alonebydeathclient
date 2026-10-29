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
    private void utilclient$glow(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.ESP.shouldGlow((Entity) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void utilclient$color(CallbackInfoReturnable<Integer> cir) {
        int c = ModuleManager.ESP.colorFor((Entity) (Object) this);
        if (c != -1) cir.setReturnValue(c);
    }
}
