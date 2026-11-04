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
            dev.utilclient.module.HudModule.onClick(info.button());   // keystrokes CPS
            if ((info.button() == InputConstants.MOUSE_BUTTON_LEFT || info.button() == InputConstants.MOUSE_BUTTON_RIGHT)
                    && net.minecraft.client.Minecraft.getInstance().gui.screen() == null) {
                dev.utilclient.util.Human.userClick();   // your clicks share the Humanizer's CPS budget
            }
            // title-screen card buttons (left click); swallow the click so vanilla doesn't also handle it
            // 26.3 uses SDL button numbers here, so compare with the constants, never 0/1
            if (info.button() == InputConstants.MOUSE_BUTTON_LEFT && dev.utilclient.gui.MainMenu.onMousePress()) { ci.cancel(); return; }
            if (info.button() == InputConstants.MOUSE_BUTTON_MIDDLE && ModuleManager.MIDDLE_CLICK_FRIEND.isEnabled()) {
                ModuleManager.MIDDLE_CLICK_FRIEND.onMiddleClick();
            }
        } catch (Throwable ignored) {
            // never break vanilla input
        }
    }

    /** Freecam: the mouse wheel changes camera speed instead of your hotbar slot. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void abd$scroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        try {
            if (ModuleManager.FREECAM.scroll(yoffset)) ci.cancel();
        } catch (Throwable ignored) {
            // never break vanilla input
        }
    }
}
