package dev.utilclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/** Reflective bridges for APIs whose names moved between Minecraft versions. */
public final class Compat {
    private static final Logger LOG = Logger.getLogger("alonebydeathclient");

    private static boolean searched;
    private static Method containerClick;
    private static Object swapConstant;

    private Compat() {}

    /** Swaps inventory-menu slot {@code slotId} with the offhand (the same as hovering a slot and pressing F). */
    public static boolean swapWithOffhand(Minecraft mc, int slotId) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) return false;
        if (!searched) {
            searched = true;
            for (Method m : mc.gameMode.getClass().getMethods()) {
                String n = m.getName();
                if (!(n.equals("handleContainerInput") || n.equals("handleInventoryMouseClick"))) continue;
                if (m.getParameterCount() != 5) continue;
                Class<?> type = m.getParameterTypes()[3];
                if (!type.isEnum()) continue;
                for (Object c : type.getEnumConstants()) {
                    if (((Enum<?>) c).name().equals("SWAP")) { containerClick = m; swapConstant = c; }
                }
            }
            if (containerClick == null) LOG.warning("AutoTotem: could not find the container click method");
        }
        if (containerClick == null) return false;
        try {
            containerClick.invoke(mc.gameMode, p.inventoryMenu.containerId, slotId, 40, swapConstant, p);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Selects a hotbar slot (0-8). */
    public static boolean selectHotbar(LocalPlayer p, int slot) {
        Inventory inv = p.getInventory();
        try {
            Method m = inv.getClass().getMethod("setSelectedSlot", int.class);
            m.invoke(inv, slot);
            return true;
        } catch (ReflectiveOperationException ignored) {
            // fall through to the field
        }
        try {
            Field f = inv.getClass().getDeclaredField("selected");
            f.setAccessible(true);
            f.setInt(inv, slot);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** First hotbar slot (0-8) whose item has the given registry path, or -1. */
    public static int findHotbar(LocalPlayer p, String itemPath) {
        for (int i = 0; i < 9; i++) {
            if (Ids.isItem(p.getInventory().getItem(i), itemPath)) return i;
        }
        return -1;
    }
}
