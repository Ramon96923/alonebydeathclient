package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/** Throws XP bottles at your feet to repair Mending armour, without moving your view. */
public final class AutoMendModule extends Module {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final NumberSetting threshold = num("Mend below", 80, 10, 100, 5).unit("%").desc("Repair pieces under this durability");
    private final NumberSetting delay = num("Delay", 2, 0, 20, 1).unit("t").desc("Ticks between bottles");
    private final BoolSetting onlyMending = bool("Only Mending armour", true);
    private final BoolSetting onlyStill = bool("Only when still", false).desc("Pause while walking");

    private int timer;

    public AutoMendModule() { super("AutoMend", "Repair armour with XP bottles", Category.PLAYER); }

    private boolean needsMend(ItemStack s) {
        if (s.isEmpty() || !s.isDamageableItem() || s.getMaxDamage() <= 0) return false;
        double left = 100.0 * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage();
        if (left >= threshold.get()) return false;
        if (!onlyMending.get()) return true;
        for (var e : s.getEnchantments().entrySet()) if (e.getKey().is(Enchantments.MENDING)) return true;
        return false;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (timer-- > 0) return;
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null || p.isUsingItem()) return;
        if (onlyStill.get() && dev.utilclient.util.Moves.moving(mc)) return;
        boolean any = false;
        for (EquipmentSlot slot : ARMOR) if (needsMend(p.getItemBySlot(slot))) { any = true; break; }
        if (!any) return;
        int bottle = Inv.hotbar(p, Items.EXPERIENCE_BOTTLE);
        if (bottle < 0) return;

        int prev = Inv.selected(p);
        Inv.select(p, bottle);
        // Look straight down just for the throw (the use packet carries your rotation), then put the
        // camera back in the same tick so your view never moves. The bottle smashes at your feet.
        float pitch = p.getXRot();
        p.setXRot(90f);
        p.connection.send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), 90f, p.onGround(), p.horizontalCollision));
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        p.setXRot(pitch);
        Inv.select(p, prev);
        timer = delay.getInt();
    }
}
