package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: vanilla HUD overlays. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void abd$vignette(CallbackInfo ci) {
        if (ModuleManager.NO_RENDER.noVignette()) ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void abd$portal(CallbackInfo ci) {
        if (ModuleManager.NO_RENDER.noPortal()) ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void abd$scoreboard(CallbackInfo ci) {
        if (ModuleManager.STREAMER_MODE.hideScoreboard()) ci.cancel();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void abd$crosshair(CallbackInfo ci) {
        if (ModuleManager.CROSSHAIR.isEnabled()) ci.cancel();   // the custom one is drawn instead
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void abd$hotbar(CallbackInfo ci) {
        if (ModuleManager.HOTBAR.isEnabled()) ci.cancel();   // the rounded one is drawn instead
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void abd$effects(CallbackInfo ci) {
        if (ModuleManager.NO_RENDER.noPotionIcons()) ci.cancel();
    }
}
