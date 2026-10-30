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
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void abd$button(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action != InputConstants.PRESS) return;
        try {
            // title-screen card buttons (left click); swallow the click so vanilla doesn't also handle it
            if (info.button() == 0 && dev.utilclient.gui.MainMenu.onMousePress()) { ci.cancel(); return; }
            if (info.button() == InputConstants.MOUSE_BUTTON_MIDDLE && ModuleManager.MIDDLE_CLICK_FRIEND.isEnabled()) {
                ModuleManager.MIDDLE_CLICK_FRIEND.onMiddleClick();
            }
        } catch (Throwable ignored) {
            // never break vanilla input
        }
    }
}
