package dev.utilclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Inventory helpers. Slot ids are those of the player's inventory menu:
 * 5-8 armour (head..feet), 9-35 main inventory, 36-44 hotbar, 45 offhand.
 */
public final class Inv {
    public static final int OFFHAND_SLOT = 45;

    private Inv() {}

    /** Hotbar index 0-8 of the first matching stack, or -1. */
    public static int hotbar(LocalPlayer p, Predicate<ItemStack> test) {
        for (int i = 0; i < 9; i++) if (test.test(p.getInventory().getItem(i))) return i;
        return -1;
    }

    public static int hotbar(LocalPlayer p, Item item) { return hotbar(p, s -> s.is(item)); }

    /** Menu slot id (9-44) of the first matching stack, main inventory first, or -1. */
    public static int menuSlot(LocalPlayer p, Predicate<ItemStack> test, boolean includeHotbar) {
        int last = includeHotbar ? 44 : 35;
        for (int i = 9; i <= last; i++) {
            ItemStack s = p.inventoryMenu.getSlot(i).getItem();
            if (!s.isEmpty() && test.test(s)) return i;
        }
        return -1;
    }

    public static int count(LocalPlayer p, Item item) {
        int n = 0;
        for (int i = 9; i <= 45; i++) {
            ItemStack s = p.inventoryMenu.getSlot(i).getItem();
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    public static int selected(LocalPlayer p) { return p.getInventory().getSelectedSlot(); }

    /** Changes the held hotbar slot. The server learns about it on your next attack/use or tick. */
    public static void select(LocalPlayer p, int slot) {
        if (slot >= 0 && slot < 9 && slot != selected(p)) p.getInventory().setSelectedSlot(slot);
    }

    public static void click(Minecraft mc, int slotId, int button, ContainerInput type) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slotId, button, type, p);
    }

    /** Same as hovering the slot and pressing the swap-offhand key. */
    public static void swapWithOffhand(Minecraft mc, int slotId) { click(mc, slotId, 40, ContainerInput.SWAP); }

    /** Swaps a menu slot with a hotbar index (0-8). */
    public static void swapWithHotbar(Minecraft mc, int slotId, int hotbarIndex) {
        click(mc, slotId, hotbarIndex, ContainerInput.SWAP);
    }

    public static void quickMove(Minecraft mc, int slotId) { click(mc, slotId, 0, ContainerInput.QUICK_MOVE); }

    public static void pickup(Minecraft mc, int slotId) { click(mc, slotId, 0, ContainerInput.PICKUP); }

    /** True while no container screen could be confused with the inventory menu. */
    public static boolean canClick(Minecraft mc) {
        return mc.player != null && mc.player.containerMenu == mc.player.inventoryMenu;
    }
}
