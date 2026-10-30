package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    /**
     * Runs before every attack (yours or a module's): weapon swaps first, then Criticals, which may hold the
     * hit back and replay it once you're falling so it lands as a crit.
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void abd$attack(Player player, Entity target, CallbackInfo ci) {
        try {
            if (ModuleManager.AUTO_WEAPON.isEnabled()) ModuleManager.AUTO_WEAPON.beforeAttack(target);
            if (ModuleManager.SPEAR.isEnabled()) ModuleManager.SPEAR.beforeAttack(target);
            if (ModuleManager.SHIELD_BREAKER.isEnabled()) ModuleManager.SHIELD_BREAKER.beforeAttack(target);
            if (ModuleManager.MACE.isEnabled()) ModuleManager.MACE.beforeAttack(target);
            if (ModuleManager.CRITICALS.isEnabled() && ModuleManager.CRITICALS.beforeAttack(target)) ci.cancel();
        } catch (Throwable ignored) {
            // the attack itself must still happen
        }
    }
}
