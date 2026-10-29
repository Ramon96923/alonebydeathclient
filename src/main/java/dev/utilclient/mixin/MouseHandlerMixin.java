package dev.utilclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilclient.module.ModuleManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void abd$button(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action != InputConstants.PRESS || info.button() != InputConstants.MOUSE_BUTTON_MIDDLE) return;
        try {
            if (ModuleManager.MIDDLE_CLICK_FRIEND.isEnabled()) ModuleManager.MIDDLE_CLICK_FRIEND.onMiddleClick();
        } catch (Throwable ignored) {
            // never break vanilla input
        }
    }
}
