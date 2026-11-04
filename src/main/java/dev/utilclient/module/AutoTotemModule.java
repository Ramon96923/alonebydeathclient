package dev.utilclient.module;

import dev.utilclient.util.DamageCalc;
import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Keeps a totem in your offhand. Smart mode only holds the totem when you're in danger
 * (low health, a crystal that could kill you, a big fall, elytra) and otherwise holds your chosen item.
 */
public final class AutoTotemModule extends Module {
    private final ModeSetting mode = mode("Mode", "Strict", "Strict", "Smart")
            .desc("Strict: always a totem. Smart: totem only when in danger");
    private final NumberSetting health = num("Health", 12, 1, 36, 1).unit("hp")
            .desc("Smart: hold a totem at or below this (incl. absorption)").visible(() -> mode.is("Smart"));
    private final ModeSetting safeItem = mode("Safe item", "Crystal", "Crystal", "Gapple", "Shield", "None")
            .desc("Smart: what to hold in the offhand when safe").visible(() -> mode.is("Smart"));
    private final BoolSetting crystalCheck = bool("Crystal check", true)
            .desc("Smart: switch to a totem if a nearby crystal could kill you").visible(() -> mode.is("Smart"));
    private final BoolSetting fallCheck = bool("Fall/elytra check", true).visible(() -> mode.is("Smart"));
    private final NumberSetting delay = num("Delay", 0, 0, 10, 1).unit("t");
    private final BoolSetting inInventory = bool("Work in inventory", true).desc("Also swap while your inventory is open");
    private final ModeSetting method = mode("Method", "Legit", "Legit", "Blatant")
            .desc("Legit: hotbar totems are swapped like pressing F, and inventory clicks only happen with your "
                    + "inventory open (what anticheats expect). Blatant: clicks the inventory anytime (instant, easy to flag)");

    // legit F-swap: 1 = slot selected, waiting a tick; 2 = swapped, switch back next tick
    private int fStage, fPrev = -1;

    private int wait;
    private long reactAt;   // humanized: when we may react to a missing totem
    private int lastCount;

    public AutoTotemModule() { super("AutoTotem", "Keep a totem in your offhand", Category.COMBAT); }

    @Override public String info() { return lastCount > 0 ? Integer.toString(lastCount) : null; }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        lastCount = Inv.count(p, Items.TOTEM_OF_UNDYING);
        if (p.isSpectator() || p.isCreative()) return;
        if (fStage == 1) {
            if (!dev.utilclient.util.Human.swapSettled()) return;   // the slot change gets its own tick (or two)
            p.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN, 0));
            fStage = 2;
            return;
        }
        if (fStage == 2) {
            // switching back isn't instant for a person either
            if (dev.utilclient.util.Human.on() && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < 40) return;
            if (fPrev >= 0) Inv.select(p, fPrev);
            fStage = 0;
            fPrev = -1;
            reactAt = 0;
            wait = delay.getInt() + 1 + (dev.utilclient.util.Human.on() && java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? 1 : 0);
            return;
        }
        if (wait > 0) { wait--; return; }
        if (!Inv.canClick(mc)) return;
        if (mc.gui.screen() != null && !inInventory.get()) return;

        Item want = danger(mc, p) ? Items.TOTEM_OF_UNDYING : safeItem();
        if (want == null || p.getOffhandItem().is(want)) { reactAt = 0; return; }
        if (method.is("Legit") && dev.utilclient.util.Human.on()) {
            // nobody re-totems on the exact tick of a pop: wait a (short) human reaction first
            long now = System.currentTimeMillis();
            if (reactAt == 0) reactAt = now + dev.utilclient.util.Human.delay(dev.utilclient.module.ModuleManager.HUMANIZER.reactionMs() * 0.8,
                    35, 60);
            if (now < reactAt) return;
        }
        int slot = find(p, want);
        if (slot < 0 && want != Items.TOTEM_OF_UNDYING) {
            // no safe item left: fall back to a totem rather than an empty hand
            if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
            slot = find(p, Items.TOTEM_OF_UNDYING);
        }
        if (slot < 0) return;
        if (method.is("Legit") && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
            // inventory closed: only a hotbar item can be swapped, the way pressing F does it
            int hb = Inv.hotbar(p, want);
            if (hb < 0 && want != Items.TOTEM_OF_UNDYING) hb = Inv.hotbar(p, Items.TOTEM_OF_UNDYING);
            if (hb < 0 && want == Items.GOLDEN_APPLE) hb = Inv.hotbar(p, Items.ENCHANTED_GOLDEN_APPLE);
            if (hb < 0) return;
            fPrev = Inv.selected(p);
            Inv.select(p, hb);
            fStage = 1;
            return;
        }
        Inv.swapWithOffhand(mc, slot);
        wait = delay.getInt();
    }

    private static int find(LocalPlayer p, Item item) {
        if (item == Items.GOLDEN_APPLE) {
            int s = Inv.menuSlot(p, st -> st.is(Items.ENCHANTED_GOLDEN_APPLE), true);
            if (s >= 0) return s;
        }
        return Inv.menuSlot(p, st -> st.is(item), true);
    }

    private Item safeItem() {
        return switch (safeItem.get()) {
            case "Crystal" -> Items.END_CRYSTAL;
            case "Gapple" -> Items.GOLDEN_APPLE;
            case "Shield" -> Items.SHIELD;
            default -> null;
        };
    }

    private boolean danger(Minecraft mc, LocalPlayer p) {
        if (mode.is("Strict")) return true;
        float hp = DamageCalc.totalHealth(p);
        if (hp <= health.get()) return true;
        if (fallCheck.get() && (p.isFallFlying() || (p.fallDistance > 3 && !p.onGround()))) return true;
        if (crystalCheck.get()) {
            for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(12), x -> x instanceof EndCrystal)) {
                if (DamageCalc.crystal(p, e.position()) >= hp - 1f) return true;
            }
        }
        // keep the totem while a gapple/shield is in use, so it isn't swapped mid-eat
        ItemStack off = p.getOffhandItem();
        return p.isUsingItem() && off.is(Items.TOTEM_OF_UNDYING);
    }
}
