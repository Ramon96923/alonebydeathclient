package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;

/** Wears the best armour in your inventory, rating armour, toughness and protection enchantments. */
public final class AutoArmorModule extends Module {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final NumberSetting delay = num("Delay", 2, 0, 20, 1).unit("t");
    private final BoolSetting blastLeggings = bool("Blast prot leggings", true).desc("Prefer Blast Protection on leggings (crystal PvP)");
    private final BoolSetting antiBreak = bool("Anti break", true).desc("Take off pieces about to break");
    private final BoolSetting keepElytra = bool("Keep elytra", true).desc("Don't swap a worn elytra for a chestplate");

    private int wait;

    public AutoArmorModule() { super("AutoArmor", "Automatically wear your best armour", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (wait > 0) { wait--; return; }
        if (!Inv.canClick(mc) || p.isCreative()) return;

        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            int armorId = 5 + i;
            ItemStack worn = p.getItemBySlot(slot);
            if (keepElytra.get() && worn.has(DataComponents.GLIDER)) continue;
            int wornScore = worn.isEmpty() || nearlyBroken(worn) ? -1 : score(worn, slot);

            int bestId = -1, bestScore = wornScore;
            for (int id = 9; id <= 44; id++) {
                ItemStack s = p.inventoryMenu.getSlot(id).getItem();
                if (s.isEmpty() || slotOf(s) != slot || nearlyBroken(s)) continue;
                int sc = score(s, slot);
                if (sc > bestScore) { bestScore = sc; bestId = id; }
            }
            if (bestId < 0) continue;

            if (worn.isEmpty()) {
                Inv.quickMove(mc, bestId);
            } else {
                Inv.pickup(mc, bestId);
                Inv.pickup(mc, armorId);
                Inv.pickup(mc, bestId);
            }
            wait = delay.getInt();
            return;   // one piece per action
        }
    }

    private boolean nearlyBroken(ItemStack s) {
        return antiBreak.get() && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 10;
    }

    private static EquipmentSlot slotOf(ItemStack s) {
        var eq = s.get(DataComponents.EQUIPPABLE);
        return eq == null ? null : eq.slot();
    }

    private int score(ItemStack s, EquipmentSlot slot) {
        double score = 0;
        ItemAttributeModifiers mods = s.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
                if (e.attribute() == Attributes.ARMOR) score += e.modifier().amount() * 10;
                else if (e.attribute() == Attributes.ARMOR_TOUGHNESS) score += e.modifier().amount() * 6;
            }
        }
        for (var entry : s.getEnchantments().entrySet()) {
            int lvl = entry.getIntValue();
            if (entry.getKey().is(Enchantments.PROTECTION)) score += lvl * 3;
            else if (entry.getKey().is(Enchantments.BLAST_PROTECTION))
                score += lvl * (blastLeggings.get() && slot == EquipmentSlot.LEGS ? 5 : 2);
            else if (entry.getKey().is(Enchantments.FIRE_PROTECTION) || entry.getKey().is(Enchantments.PROJECTILE_PROTECTION))
                score += lvl;
            else if (entry.getKey().is(Enchantments.MENDING) || entry.getKey().is(Enchantments.UNBREAKING)) score += 1;
        }
        return (int) Math.round(score);
    }
}
