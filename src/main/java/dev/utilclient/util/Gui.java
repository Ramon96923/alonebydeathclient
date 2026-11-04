package dev.utilclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Helpers for server menus (DonutSMP confirm screens, /sell, /ah...). */
public final class Gui {
    private Gui() {}

    /** The open chest-style server menu, or null (the player's own inventory doesn't count). */
    public static AbstractContainerScreen<?> serverMenu(Minecraft mc) {
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> s
                && !(s instanceof InventoryScreen) && !(s instanceof CreativeModeInventoryScreen)) return s;
        return null;
    }

    /** Lower-case title of the open screen, or "". */
    public static String title(Minecraft mc) {
        return mc.gui.screen() == null ? "" : mc.gui.screen().getTitle().getString().toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeConfirm(ItemStack s) {
        if (s.isEmpty()) return false;
        String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("confirm") || name.contains("accept") || name.equals("yes")) return true;
        String id = Ids.item(s);
        return id.startsWith("lime_") || id.startsWith("green_");   // green panes/wool/concrete = "yes"
    }

    /**
     * Clicks the confirm button in an open server menu (a slot named Confirm/Accept, or a green/lime item).
     * @return true if something was clicked
     */
    public static boolean clickConfirm(Minecraft mc) {
        AbstractContainerScreen<?> s = serverMenu(mc);
        if (s == null || mc.gameMode == null) return false;
        AbstractContainerMenu menu = s.getMenu();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory || !looksLikeConfirm(slot.getItem())) continue;
            mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.PICKUP, mc.player);
            return true;
        }
        return false;
    }
}
