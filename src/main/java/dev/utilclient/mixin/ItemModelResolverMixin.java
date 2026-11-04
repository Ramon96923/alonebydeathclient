package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** CustomTotem: the renderer sees a different stack for totems; the real item is untouched. */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @ModifyVariable(method = {"updateForTopItem", "appendItemLayers", "updateForLiving", "updateForNonLiving"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack dw$totem(ItemStack stack) {
        try {
            if (ModuleManager.CUSTOM_TOTEM.isEnabled()) return ModuleManager.CUSTOM_TOTEM.swap(stack);
        } catch (Throwable ignored) {
            // render the real item
        }
        return stack;
    }
}
