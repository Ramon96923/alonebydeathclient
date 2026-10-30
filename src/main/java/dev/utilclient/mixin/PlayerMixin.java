package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** OldPvP: force a zero attack-cooldown so every hit is fully charged, like 1.8. Client player only. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("HEAD"), cancellable = true)
    private void abd$noCooldown(CallbackInfoReturnable<Float> cir) {
        if ((Object) this == Minecraft.getInstance().player && ModuleManager.OLD_PVP.noCooldown()) {
            cir.setReturnValue(0.0f);
        }
    }
}
