package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Jesus: let the local player stand on liquids, like a strider on lava. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canStandOnFluid", at = @At("RETURN"), cancellable = true)
    private void abd$standOnFluid(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != Minecraft.getInstance().player || Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (ModuleManager.JESUS.standOn(fluid)) cir.setReturnValue(true);
    }
}
