package dev.utilclient.module;

import dev.utilclient.util.CombatState;
import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Holds your best weapon whenever you're about to fight. It switches early (when a target is near)
 * because changing items resets the attack charge.
 */
public final class AutoWeaponModule extends Module {
    private final ModeSetting prefer = mode("Prefer", "Damage", "Damage", "Sword", "Axe");
    private final BoolSetting axeOnShield = bool("Axe vs shields", true).desc("Use an axe when the target is blocking");
    private final NumberSetting range = num("Switch range", 5, 3, 8, 0.5).unit("m");

    public AutoWeaponModule() { super("AutoWeapon", "Switch to your best weapon in fights", Category.COMBAT); }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.gui.screen() != null) return;
        Entity t = mc.hitResult instanceof EntityHitResult h ? h.getEntity() : CombatState.current();
        if (t instanceof LivingEntity && mc.player.distanceTo(t) <= range.get()) select(mc.player, t);
    }

    /** From the attack mixin. */
    public void beforeAttack(Entity target) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && target instanceof LivingEntity) select(p, target);
    }

    private void select(LocalPlayer p, Entity target) {
        boolean wantAxe = axeOnShield.get() && target instanceof LivingEntity le && le.isBlocking();
        int best = -1;
        double bestScore = score(p.getMainHandItem(), wantAxe);
        for (int i = 0; i < 9; i++) {
            double s = score(p.getInventory().getItem(i), wantAxe);
            if (s > bestScore + 0.01) { bestScore = s; best = i; }
        }
        if (best >= 0) Inv.select(p, best);
    }

    private double score(ItemStack s, boolean wantAxe) {
        if (s.isEmpty()) return 0;
        boolean sword = s.is(ItemTags.SWORDS), axe = s.is(ItemTags.AXES);
        if (!sword && !axe && !AutoHitModule.isWeapon(s)) return 0;
        double dmg = 0;
        ItemAttributeModifiers mods = s.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
                if (e.attribute() == Attributes.ATTACK_DAMAGE) dmg += e.modifier().amount();
            }
        }
        for (var entry : s.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.SHARPNESS)) dmg += 0.5 + 0.5 * entry.getIntValue();
        }
        if (wantAxe && axe) dmg += 100;
        if (prefer.is("Sword") && sword) dmg += 50;
        if (prefer.is("Axe") && axe) dmg += 50;
        return dmg + 1;
    }
}
