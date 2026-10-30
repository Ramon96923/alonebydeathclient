package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Friends;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Counts totem pops for every player in crystal fights and says so in chat — including when they die. */
public final class PopCounterModule extends Module {
    private final BoolSetting self = bool("Your pops", true);
    private final BoolSetting friends = bool("Friends' pops", true);
    private final BoolSetting deaths = bool("Deaths", true).desc("Announce when someone dies after popping");

    private final Map<UUID, Integer> pops = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private ClientLevel level;

    public PopCounterModule() { super("PopCounter", "Count totem pops in chat", Category.MISC); }

    @Override protected void onDisable() { pops.clear(); names.clear(); }

    /** From the packet-listener mixin, on the main thread, for every entity event. */
    public void onEntityEvent(ClientboundEntityEventPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.level == null || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;
        Entity e = packet.getEntity(mc.level);
        if (!(e instanceof Player pl)) return;
        boolean me = pl == mc.player;
        if (me && !self.get()) return;
        if (!me && Friends.isFriend(pl) && !friends.get()) return;
        int n = pops.merge(pl.getUUID(), 1, Integer::sum);
        names.put(pl.getUUID(), pl.getName().getString());
        String who = me ? "§aYou" : "§c" + pl.getName().getString();
        Chat.info(who + " §7popped §f" + n + " §7totem" + (n == 1 ? "" : "s") + "!");
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != level) { level = mc.level; pops.clear(); names.clear(); }
        pops.entrySet().removeIf(entry -> {
            Player pl = mc.level.getPlayerByUUID(entry.getKey());
            if (pl == null || pl.isAlive()) return false;
            if (deaths.get()) {
                String who = pl == mc.player ? "§aYou" : "§c" + names.getOrDefault(entry.getKey(), "?");
                Chat.info(who + " §7died after popping §f" + entry.getValue() + " §7totem"
                        + (entry.getValue() == 1 ? "" : "s") + ".");
            }
            names.remove(entry.getKey());
            return true;
        });
    }
}
