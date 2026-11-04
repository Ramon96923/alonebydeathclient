package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Friends;
import dev.utilclient.util.Cmd;
import dev.utilclient.util.Gui;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Accepts teleport requests from your friends (or anyone) and presses DonutSMP's confirm button for you. */
public final class TpaAcceptModule extends Module {
    private final BoolSetting anyone = bool("Anyone", false).desc("Accept everyone, not just friends");
    private final StringSetting command = text("Command", "/tpaccept");
    private final BoolSetting confirm = bool("Click confirm menu", true);

    private String pending;
    private long requestedAt, sentAt;

    public TpaAcceptModule() { super("TpaAccept", "Auto-accept TPA requests from friends", Category.DONUT); }

    /** Every incoming server message (from UtilClient). */
    public void onMessage(String text) {
        if (!isEnabled()) return;
        String low = text.toLowerCase(Locale.ROOT);
        if (!(low.contains("tpa") || low.contains("teleport"))) return;
        if (!(low.contains("request") || low.contains("wants"))) return;
        // the first word that is a player name we know
        for (String word : text.split("[^A-Za-z0-9_]+")) {
            if (word.length() < 3) continue;
            boolean friend = Friends.isFriend(word);
            boolean online = Minecraft.getInstance().getConnection() != null
                    && Minecraft.getInstance().getConnection().getPlayerInfo(word) != null;
            if (friend || (anyone.get() && online)) {
                pending = word;
                requestedAt = System.currentTimeMillis();
                return;
            }
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (pending != null && now - requestedAt > 400) {   // a small human-like pause
            Cmd.send(mc, command.get() + " " + pending);
            Chat.info("Accepted the teleport request from " + pending + ".");
            pending = null;
            sentAt = now;
        }
        if (confirm.get() && sentAt > 0 && now - sentAt < 4000 && Gui.serverMenu(mc) != null && Gui.clickConfirm(mc)) sentAt = 0;
    }
}
