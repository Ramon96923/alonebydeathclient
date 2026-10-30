package dev.utilclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilclient.core.Commands;
import dev.utilclient.core.Config;
import dev.utilclient.core.Toasts;
import dev.utilclient.gui.ClickGui;
import dev.utilclient.gui.MainMenu;
import dev.utilclient.module.ModuleManager;
import dev.utilclient.util.RenderState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

import java.util.logging.Level;
import java.util.logging.Logger;

public class UtilClient implements ClientModInitializer {
    public static final String MOD_ID = "alonebydeathclient";
    private static final Logger LOG = Logger.getLogger(MOD_ID);

    private static KeyMapping openGui;
    private static Thread mainThread;
    private static boolean openGuiNextTick;

    @Override
    public void onInitializeClient() {
        mainThread = Thread.currentThread();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.alonebydeathclient.gui", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, category));

        try {
            Config.load();   // also initialises every module
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Config load failed", t);
        }

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "overlay"), (graphics, delta) -> {
            RenderState.frame();
            ModuleManager.renderHud(graphics, delta.getGameTimeDeltaPartialTick(false));
            try {
                Toasts.render(graphics, graphics.guiWidth(), graphics.guiHeight());
            } catch (Throwable ignored) {
                // notifications are cosmetic
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openGui.consumeClick()) {
                if (mc.gui.screen() == null) mc.gui.setScreen(new ClickGui());
            }
            if (openGuiNextTick && mc.gui.screen() == null) {
                openGuiNextTick = false;
                mc.gui.setScreen(new ClickGui());
            }
            dev.utilclient.module.AutoReconnectModule.remember(mc.getCurrentServer());
            ModuleManager.tick(mc);
            Config.tick();
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !Commands.handle(message));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

        // Title-screen card: client name/version, an Open Click GUI button and a Spotify link.
        // Also the Reconnect / AutoReconnect buttons on the disconnect screen.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) MainMenu.attach(screen);
            else if (screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
                ModuleManager.AUTO_RECONNECT.attach(screen);
            }
        });
    }

    public static void openGuiNextTick() { openGuiNextTick = true; }

    /** Mod version from fabric.mod.json, e.g. "2.1.0". */
    public static String version() {
        try {
            return FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("2.1.0");
        } catch (Throwable t) {
            return "2.1.0";
        }
    }

    /** True on the client (render) thread, where game state may be touched. */
    public static boolean isMainThread() { return Thread.currentThread() == mainThread; }

    /** The key currently bound to "Open Click GUI" in Controls. */
    public static int guiKey() {
        try {
            return KeyMappingHelper.getBoundKeyOf(openGui).getValue();
        } catch (Throwable t) {
            return InputConstants.KEY_RSHIFT;
        }
    }

    public static Minecraft mc() { return Minecraft.getInstance(); }
}
