package dev.utilclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilclient.gui.ClickGui;
import dev.utilclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

public class UtilClient implements ClientModInitializer {
    private static KeyMapping openGui;
    private static boolean nightVisionApplied;

    @Override
    public void onInitializeClient() {
        openGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.utilclient.gui", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));

        // Lambda parameter types are (GuiGraphicsExtractor, DeltaTracker); the method name behind it doesn't matter.
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("utilclient", "tracker"),
                (graphics, delta) -> ModuleManager.TRACKER.render(graphics));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openGui.consumeClick()) {
                if (mc.screen == null) mc.setScreenAndShow(new ClickGui());
            }
            ModuleManager.tick(mc);
            tickFullbright(mc);
        });
    }

    private static void tickFullbright(Minecraft mc) {
        if (mc.player == null) { nightVisionApplied = false; return; }
        boolean want = ModuleManager.FULLBRIGHT.isEnabled() || ModuleManager.XRAY.isEnabled();
        if (want) {
            if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
                mc.player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            }
            nightVisionApplied = true;
        } else if (nightVisionApplied) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            nightVisionApplied = false;
        }
    }
}
