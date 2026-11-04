package dev.utilclient.module;

import dev.utilclient.core.Friends;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.world.entity.player.Player;

/**
 * Leaves the server the moment another player comes within range: base protection while you AFK, mine or
 * wait at spawners. Friends and AntiBot's fake players don't count. Turns AutoReconnect off so you stay out.
 */
public final class AutoLeaveModule extends Module {
    private final NumberSetting radius = num("Radius", 32, 4, 128, 2).unit("m");
    private final BoolSetting ignoreFriends = bool("Ignore friends", true);
    private final BoolSetting stopReconnect = bool("Disable AutoReconnect", true);
    private final BoolSetting toggleOff = bool("Turn off after", true).desc("So you don't get kicked straight away when you rejoin");

    public AutoLeaveModule() { super("AutoLeave", "Disconnect when a player comes near", Category.DONUT); }

    @Override public String info() { return radius.getInt() + "m"; }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer me = mc.player;
        if (mc.getCurrentServer() == null || me.connection == null) return;   // never in singleplayer
        for (Player other : mc.level.players()) {
            if (other == me || other.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.isFriend(other)) continue;
            if (ModuleManager.ANTI_BOT.isBot(other)) continue;
            double d = me.distanceTo(other);
            if (d > radius.get()) continue;
            if (stopReconnect.get()) ModuleManager.AUTO_RECONNECT.setEnabled(false, false);
            if (toggleOff.get()) setEnabled(false, false);
            me.connection.handleDisconnect(new ClientboundDisconnectPacket(Component.literal(
                    "[DonutWaffen] Left: " + other.getName().getString() + " came within " + Math.round(d) + "m")));
            return;
        }
    }
}
