package dev.utilclient.core;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Client-side chat messages (only you can see them). */
public final class Chat {
    private Chat() {}

    public static void info(String text) {
        send(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    public static void good(String text) {
        send(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    public static void error(String text) {
        send(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    public static void send(Component body) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        MutableComponent msg = Component.empty()
                .append(Component.literal("ABD").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
                .append(body);
        mc.execute(() -> {
            if (mc.gui != null) mc.gui.hud.getChat().addClientSystemMessage(msg);
        });
    }

    /** A short chime for alerts (base found, player nearby...). */
    public static void ping() {
        try {
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p != null) p.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 1f, 1.5f);
        } catch (Throwable ignored) {
            // sound is optional
        }
    }
}
