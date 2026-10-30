package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Friends;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Warns you when another player enters or leaves your render range — the anarchy early-warning system. */
public final class VisualRangeModule extends Module {
    private final BoolSetting onEnter = bool("On enter", true);
    private final BoolSetting onLeave = bool("On leave", false);
    private final BoolSetting ignoreFriends = bool("Ignore friends", true);

    private final Set<UUID> seen = new HashSet<>();

    public VisualRangeModule() { super("VisualRange", "Alert when players come into view", Category.MISC); }

    @Override protected void onDisable() { seen.clear(); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer me = mc.player;
        Set<UUID> now = new HashSet<>();
        for (Player pl : mc.level.players()) {
            if (pl == me) continue;
            if (ignoreFriends.get() && Friends.isFriend(pl)) continue;
            UUID id = pl.getUUID();
            now.add(id);
            if (onEnter.get() && seen.add(id)) {
                Chat.info("§a+ §f" + pl.getName().getString() + " §7entered your range");
            }
        }
        if (onLeave.get()) {
            for (UUID id : seen) {
                if (!now.contains(id)) Chat.info("§c- §7a player left your range");
            }
        }
        seen.retainAll(now);
        seen.addAll(now);
    }
}
