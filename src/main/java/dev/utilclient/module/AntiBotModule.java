package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Stops combat modules targeting fake players: server NPCs and anticheat "bots" that aren't in the tab list.
 * Hitting an invisible anticheat bot is one of the quickest ways to get a kill-aura ban.
 */
public final class AntiBotModule extends Module {
    private final BoolSetting tabList = bool("Not in tab list", true).desc("Ignore players missing from the player list");
    private final BoolSetting invisible = bool("Invisible", false).desc("Ignore invisible players too");

    public AntiBotModule() {
        super("AntiBot", "Don't attack NPCs or anticheat bots", Category.COMBAT);
        enabledByDefault = true;
    }

    public boolean isBot(Entity e) {
        if (!isEnabled() || !(e instanceof Player pl) || pl == Minecraft.getInstance().player) return false;
        if ("FakePlayer".equals(pl.getName().getString())) return false;   // the client's own test dummy
        var conn = Minecraft.getInstance().getConnection();
        if (tabList.get() && conn != null && conn.getPlayerInfo(pl.getUUID()) == null) return true;
        return invisible.get() && pl.isInvisible();
    }
}
