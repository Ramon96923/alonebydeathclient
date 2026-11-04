package dev.utilclient.module;

import dev.utilclient.core.Chat;
import dev.utilclient.util.Cmd;
import dev.utilclient.util.Gui;
import dev.utilclient.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * DonutSMP /sell on autopilot: when your inventory fills up (or on a timer) it opens the sell menu, moves
 * your loot in and closes it, keeping your gear. Idea from Glazed's Auto Sell; the code is original.
 */
public final class AutoSellModule extends Module {
    private final StringSetting command = text("Command", "/sell");
    private final ModeSetting trigger = mode("When", "Inventory full", "Inventory full", "Timer");
    private final NumberSetting minutes = num("Every", 5, 1, 60, 1).unit("min").visible(() -> trigger.is("Timer"));
    private final NumberSetting perTick = num("Items per tick", 4, 1, 36, 1).desc("Lower is gentler on the server");
    private final BoolSetting keepHotbar = bool("Keep hotbar", true);
    private final BoolSetting keepGear = bool("Keep gear", true).desc("Never sell tools, weapons, armour, totems, food, pearls, shulkers");
    private final BoolSetting keepBlocks = bool("Keep blocks", false).desc("Don't sell placeable blocks");

    private long lastSell;
    private long openedAt;
    private boolean waiting;

    public AutoSellModule() { super("AutoSell", "Sell your loot with /sell automatically", Category.DONUT); }

    @Override protected void onEnable() { lastSell = System.currentTimeMillis(); waiting = false; }

    private boolean keep(ItemStack s) {
        if (s.isEmpty()) return true;
        if (keepBlocks.get() && s.getItem() instanceof BlockItem) return true;
        if (!keepGear.get()) return false;
        if (s.isDamageableItem() || s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.PICKAXES)
                || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES) || s.is(Items.TOTEM_OF_UNDYING) || s.is(Items.ENDER_PEARL)
                || s.is(Items.END_CRYSTAL) || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)
                || s.is(Items.EXPERIENCE_BOTTLE) || s.is(Items.SPAWNER)) return true;
        String id = Ids.item(s);
        return id.endsWith("shulker_box") || s.has(net.minecraft.core.component.DataComponents.FOOD);
    }

    private boolean inventoryFull(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        for (int i = 9; i < 36; i++) if (inv.getItem(i).isEmpty()) return false;
        return true;
    }

    @Override
    public void onTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (waiting) {
            AbstractContainerScreen<?> s = Gui.serverMenu(mc);
            if (s == null) {
                if (now - lastSell > 5000) waiting = false;   // menu never came
                return;
            }
            if (openedAt == 0) openedAt = now;
            if (now - openedAt < 300) return;   // let the menu fill in
            AbstractContainerMenu menu = s.getMenu();
            int moved = 0;
            for (int i = 0; i < menu.slots.size() && moved < perTick.getInt(); i++) {
                Slot slot = menu.slots.get(i);
                if (!(slot.container instanceof Inventory) || !slot.hasItem()) continue;
                if (keepHotbar.get() && slot.getContainerSlot() < 9) continue;
                if (keep(slot.getItem())) continue;
                mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                moved++;
            }
            if (moved == 0) {   // done: closing the sell menu sells what's in it
                s.onClose();
                waiting = false;
                Chat.info("AutoSell: sold your loot.");
            }
            return;
        }
        if (mc.gui.screen() != null) return;
        boolean due = trigger.is("Timer") ? now - lastSell > minutes.get() * 60_000 : inventoryFull(mc) && now - lastSell > 10_000;
        if (!due) return;
        Cmd.send(mc, command.get());
        lastSell = now;
        openedAt = 0;
        waiting = true;
    }
}
