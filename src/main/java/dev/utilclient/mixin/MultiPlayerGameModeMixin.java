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
    /** Runs before every attack (yours or a module's): weapon swap, then critical packets. */
    @Inject(method = "attack", at = @At("HEAD"))
    private void abd$attack(Player player, Entity target, CallbackInfo ci) {
        try {
            if (ModuleManager.AUTO_WEAPON.isEnabled()) ModuleManager.AUTO_WEAPON.beforeAttack(target);
            if (ModuleManager.CRITICALS.isEnabled()) ModuleManager.CRITICALS.beforeAttack(target);
        } catch (Throwable ignored) {
            // the attack itself must still happen
        }
    }
}
