package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spear: a left-click that hits nothing (air) can trigger a sprint lunge. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void abd$startAttack(CallbackInfoReturnable<Boolean> cir) {
        try {
            Minecraft mc = Minecraft.getInstance();
            boolean air = mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS;
            if (air && ModuleManager.SPEAR.isEnabled()) ModuleManager.SPEAR.onSwingAir();
        } catch (Throwable ignored) {
            // input must never break
        }
    }
}
