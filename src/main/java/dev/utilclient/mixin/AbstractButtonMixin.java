package dev.utilclient.mixin;

import dev.utilclient.gui.VanillaButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Round, themed button backgrounds everywhere (ClickGUI > Rounded game menus). */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void dw$roundSprite(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!VanillaButtons.enabled()) return;
        try {
            VanillaButtons.drawBackground(g, (AbstractWidget) (Object) this);
            ci.cancel();
        } catch (Throwable ignored) {
            // fall back to the vanilla sprite
        }
    }

    @Inject(method = "extractDefaultLabel", at = @At("HEAD"), cancellable = true)
    private void dw$label(net.minecraft.client.gui.ActiveTextCollector text, CallbackInfo ci) {
        try {
            if (VanillaButtons.ownsLabel((AbstractWidget) (Object) this)) ci.cancel();
        } catch (Throwable ignored) {
            // vanilla label
        }
    }
}
