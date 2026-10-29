package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Eats automatically when hungry (or a golden apple when health is low). */
public final class AutoEatModule extends Module {
    private final NumberSetting hunger = num("Hunger", 14, 1, 19, 1).desc("Eat at or below this food level (20 = full)");
    private final NumberSetting health = num("Gapple health", 0, 0, 20, 1).unit("hp").desc("Eat a golden apple at or below this (0 = off)");
    private final BoolSetting avoidBad = bool("Avoid bad food", true).desc("Never eat rotten flesh, spider eyes, etc.");

    private boolean eating;
    private int previous = -1;

    public AutoEatModule() { super("AutoEat", "Eat automatically when hungry", Category.PLAYER); }

    public boolean isEating() { return isEnabled() && eating; }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p.isCreative() || mc.gui.screen() != null) { stop(mc); return; }

        boolean needGapple = health.get() > 0 && p.getHealth() <= health.get();
        boolean hungry = p.getFoodData().getFoodLevel() <= hunger.getInt();
        if (eating) {
            if (!p.isUsingItem() && !(needGapple || hungry)) { stop(mc); return; }
            mc.options.keyUse.setDown(true);
            if (!(needGapple || hungry)) stop(mc);
            return;
        }
        if (!needGapple && !hungry) return;

        int slot = needGapple ? bestGapple(p) : -1;
        if (slot < 0 && hungry) slot = bestFood(p);
        if (slot < 0) return;
        previous = Inv.selected(p);
        Inv.select(p, slot);
        mc.options.keyUse.setDown(true);
        eating = true;
    }

    private void stop(Minecraft mc) {
        if (!eating) return;
        eating = false;
        mc.options.keyUse.setDown(false);
        if (previous >= 0 && mc.player != null) Inv.select(mc.player, previous);
        previous = -1;
    }

    @Override protected void onDisable() { stop(Minecraft.getInstance()); }

    private int bestGapple(LocalPlayer p) {
        int s = Inv.hotbar(p, Items.ENCHANTED_GOLDEN_APPLE);
        return s >= 0 ? s : Inv.hotbar(p, Items.GOLDEN_APPLE);
    }

    private boolean bad(ItemStack s) {
        return avoidBad.get() && (s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO)
                || s.is(Items.PUFFERFISH) || s.is(Items.CHORUS_FRUIT) || s.is(Items.SUSPICIOUS_STEW));
    }

    private int bestFood(LocalPlayer p) {
        int best = -1, bestNutrition = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            FoodProperties food = s.get(DataComponents.FOOD);
            if (food == null || bad(s) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) continue;
            if (food.nutrition() > bestNutrition) { bestNutrition = food.nutrition(); best = i; }
        }
        return best;
    }
}
