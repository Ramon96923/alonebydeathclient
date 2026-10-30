package dev.utilclient.module;

import dev.utilclient.core.Friends;
import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Disconnects you before you die: low health, running out of totems, or an enemy getting close.
 * Turns AutoReconnect off when it fires so you don't rejoin straight back into the fight.
 */
public final class AutoLogModule extends Module {
    private final NumberSetting health = num("Health", 6, 0, 19, 1).unit("hp").desc("Log out at or below this health (0 = off)");
    private final NumberSetting totems = num("Totems left", 0, 0, 10, 1).desc("Log out when you have this many totems or fewer (0 = off)");
    private final BoolSetting players = bool("On player", false).desc("Log out when a non-friend player comes close");
    private final NumberSetting playerRange = num("Player range", 16, 4, 64, 2).unit("m").visible(players::get);
    private final BoolSetting stopReconnect = bool("Disable AutoReconnect", true);

    public AutoLogModule() { super("AutoLog", "Disconnect before you die", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p.connection == null || p.isCreative() || p.isSpectator() || mc.getCurrentServer() == null) return;
        if (p.getHealth() <= 0) return;   // already dead

        if (health.getInt() > 0 && p.getHealth() <= health.getInt()) {
            log(mc, "Health was " + Math.round(p.getHealth()) + ".");
            return;
        }
        if (totems.getInt() > 0) {
            int left = Inv.count(p, Items.TOTEM_OF_UNDYING);
            if (left <= totems.getInt()) { log(mc, left + " totem(s) left."); return; }
        }
        if (players.get()) {
            for (Player other : mc.level.players()) {
                if (other == p || Friends.isFriend(other) || other.isSpectator()) continue;
                if (p.distanceTo(other) <= playerRange.get()) {
                    log(mc, other.getName().getString() + " came within " + playerRange.getInt() + "m.");
                    return;
                }
            }
        }
    }

    private void log(Minecraft mc, String why) {
        if (stopReconnect.get()) ModuleManager.AUTO_RECONNECT.setEnabled(false, false);
        setEnabled(false, false);   // don't trigger again the moment you rejoin
        mc.player.connection.handleDisconnect(new ClientboundDisconnectPacket(Component.literal("[AutoLog] " + why)));
    }
}
