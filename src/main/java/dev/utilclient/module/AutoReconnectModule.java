package dev.utilclient.module;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rejoins a server after you get disconnected — queue drops, restarts, the usual anarchy pain. Adds a
 * Reconnect button and an AutoReconnect toggle (with a countdown) to the disconnect screen. The reconnect
 * call follows Wurst's LastServerRememberer and Meteor's AutoReconnect (both GPL-3.0); the code is original.
 */
public final class AutoReconnectModule extends Module {
    private static final Logger LOG = Logger.getLogger("alonebydeathclient");

    private final NumberSetting delay = num("Delay", 3.0, 0.0, 30.0, 0.5).unit("s").desc("Wait before auto-reconnecting");

    private static ServerData lastServer;
    private Button reconnectBtn;
    private double ticksLeft;

    public AutoReconnectModule() { super("AutoReconnect", "Rejoin a server after a disconnect", Category.MISC); }

    /** Called every client tick from UtilClient so we know where to return to, even while disabled. */
    public static void remember(ServerData server) {
        if (server != null) lastServer = server;
    }

    private void reconnect(Screen fallback) {
        if (lastServer == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            ConnectScreen.startConnecting(fallback != null ? fallback : new TitleScreen(), mc,
                    ServerAddress.parseString(lastServer.ip), lastServer, false, null);
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Reconnect failed", t);
        }
    }

    private String countdownLabel() {
        if (!isEnabled()) return "AutoReconnect: Off";
        return "AutoReconnect: " + String.format("%.1f", Math.max(0, ticksLeft) / 20.0) + "s";
    }

    /** Called from UtilClient AFTER_INIT for every DisconnectedScreen. */
    public void attach(Screen screen) {
        if (lastServer == null) return;
        ticksLeft = delay.get() * 20.0;

        ScreenEvents.afterTick(screen).register(s -> {
            if (!isEnabled()) return;
            if (ticksLeft <= 0) { ticksLeft = Double.MAX_VALUE; reconnect(null); return; }   // fire once
            ticksLeft--;
            if (reconnectBtn != null) reconnectBtn.setMessage(Component.literal(countdownLabel()));
        });

        try {
            int cx = screen.width / 2;
            int y = screen.height / 4 + 120;   // fallback; normally just under the "Back to server list" button
            int lowest = -1;
            for (net.minecraft.client.gui.components.AbstractWidget w : Screens.getWidgets(screen)) {
                lowest = Math.max(lowest, w.getY() + w.getHeight());
            }
            if (lowest > 0 && lowest + 24 < screen.height) y = lowest + 4;
            Button reconnect = Button.builder(Component.literal("Reconnect"), b -> reconnect(null))
                    .bounds(cx - 102, y, 100, 20).build();
            reconnectBtn = Button.builder(Component.literal(countdownLabel()), b -> {
                toggle();
                ticksLeft = delay.get() * 20.0;
                reconnectBtn.setMessage(Component.literal(countdownLabel()));
            }).bounds(cx + 2, y, 100, 20).build();
            Screens.getWidgets(screen).add(reconnect);
            Screens.getWidgets(screen).add(reconnectBtn);
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Couldn't add reconnect buttons", t);
        }
    }
}
