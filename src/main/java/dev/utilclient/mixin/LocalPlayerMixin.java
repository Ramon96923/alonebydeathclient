package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: the big totem-pop animation. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void abd$itemActivation(ItemStack stack, CallbackInfo ci) {
        if (stack.is(Items.TOTEM_OF_UNDYING) && ModuleManager.NO_RENDER.noTotemAnimation()) ci.cancel();
    }
}
