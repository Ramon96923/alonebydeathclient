package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.Set;

/** Empties any chest, shulker or barrel you open into your inventory — fast stash looting. */
public final class ChestStealerModule extends Module {
    private final NumberSetting delay = num("Delay", 1, 0, 10, 1).unit("t").desc("Ticks between items (0 = all at once)");
    private final BoolSetting autoClose = bool("Close when done", true);

    private Screen current;
    private final Set<Integer> tried = new HashSet<>();
    private int timer;

    public ChestStealerModule() { super("ChestStealer", "Loot opened containers instantly", Category.PLAYER); }

    @Override
    public void onTick(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)
                || screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
            current = null;
            return;
        }
        if (screen != current) { current = screen; tried.clear(); timer = 2; }   // let the contents arrive
        if (timer-- > 0) return;

        LocalPlayer p = mc.player;
        AbstractContainerMenu menu = screen.getMenu();
        int moved = 0, budget = delay.getInt() == 0 ? 64 : 1;
        boolean left = false;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory || !slot.hasItem()) continue;
            if (tried.contains(i)) { left = true; continue; }   // didn't fit last time (inventory full)
            mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, p);
            tried.add(i);
            left = true;
            if (++moved >= budget) break;
        }
        timer = delay.getInt();
        // nothing left: close. Items left but none would move: your inventory is full, so leave it open.
        if (!left && autoClose.get()) screen.onClose();
    }
}
