package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void abd$key(long handle, int action, KeyEvent event, CallbackInfo ci) {
        try {
            ModuleManager.onKey(event.key(), action);
        } catch (Throwable ignored) {
            // never break vanilla input
        }
    }
}
