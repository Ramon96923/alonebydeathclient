package dev.utilclient.util;

import net.minecraft.client.Minecraft;

/** Sends "/command" lines as commands and anything else as chat. */
public final class Cmd {
    private Cmd() {}

    public static void send(Minecraft mc, String text) {
        if (text == null || text.isBlank() || mc.getConnection() == null) return;
        String t = text.strip();
        if (t.startsWith("/")) mc.getConnection().sendCommand(t.substring(1));
        else mc.getConnection().sendChat(t);
    }
}
