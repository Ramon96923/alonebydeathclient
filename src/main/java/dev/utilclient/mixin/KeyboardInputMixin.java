package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: your keys fly the camera, so the real player gets no movement input. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void abd$freecamInput(CallbackInfo ci) {
        if (!ModuleManager.FREECAM.active()) return;
        keyPresses = new Input(false, false, false, false, false, false, false);
        moveVector = Vec2.ZERO;
    }
}
