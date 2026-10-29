package dev.utilclient.util;

import net.minecraft.client.player.LocalPlayer;

/** Kept for older code paths; prefer {@link Inv}. */
public final class Compat {
    private Compat() {}

    public static boolean selectHotbar(LocalPlayer p, int slot) {
        if (slot < 0 || slot > 8) return false;
        Inv.select(p, slot);
        return true;
    }

    /** First hotbar slot (0-8) whose item has the given registry path, or -1. */
    public static int findHotbar(LocalPlayer p, String itemPath) {
        return Inv.hotbar(p, s -> Ids.isItem(s, itemPath));
    }
}
