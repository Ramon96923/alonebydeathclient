package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.core.Friends;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/** Middle-click a player to add or remove them as a friend. Friends are never targeted. */
public final class MiddleClickFriendModule extends Module {
    public MiddleClickFriendModule() {
        super("MiddleClickFriend", "Middle-click players to friend them", Category.MISC);
        enabledByDefault = true;
    }

    public void onMiddleClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || !(mc.hitResult instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof Player target) || target == mc.player) return;
        String name = target.getName().getString();
        boolean now = Friends.toggle(name);
        Chat.info(now ? "Added " + name + " as a friend." : "Removed " + name + " from friends.");
    }
}
