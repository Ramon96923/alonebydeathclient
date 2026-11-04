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
    private final BoolSetting openInv = bool("Open inventory", true)
            .desc("Legit: with no totem in the hotbar, open your inventory, move to the totem, press F and close it again")
            .visible(() -> method.is("Legit"));
    private final NumberSetting cursorSpeed = num("Cursor speed", 1.0, 0.4, 2.5, 0.05).unit("x")
            .desc("How fast the inventory cursor moves (on top of your mouse sensitivity)").visible(() -> method.is("Legit"));
    private final BoolSetting showCursor = bool("Show cursor", true).desc("Draw the moving cursor in the inventory")
            .visible(() -> method.is("Legit"));

    // inventory cursor: 0 idle, 1 moving to the slot, 2 swapped, waiting to close
    private int cStage;
    private int cSlot = -1;
    private Item cItem;
    private boolean openedByUs, cPlanned;
    private long cStart, cDur, cNext;
    private float sx, sy, arc;
    private net.minecraft.client.gui.screens.Screen cScreen;

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
        if (p.isSpectator() || p.isCreative()) { resetCursor(); return; }
        if (cStage != 0) { cursorTick(mc); return; }
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
        boolean invOpen = mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        if (method.is("Legit") && invOpen) {
            // inventory open: move a cursor to the item and press F on it, like a person
            startCursor(mc, slot, p.inventoryMenu.getSlot(slot).getItem().getItem(), false);
            return;
        }
        if (method.is("Legit")) {
            // inventory closed: only a hotbar item can be swapped, the way pressing F does it
            int hb = Inv.hotbar(p, want);
            if (hb < 0 && want != Items.TOTEM_OF_UNDYING) hb = Inv.hotbar(p, Items.TOTEM_OF_UNDYING);
            if (hb < 0 && want == Items.GOLDEN_APPLE) hb = Inv.hotbar(p, Items.ENCHANTED_GOLDEN_APPLE);
            if (hb < 0) {
                if (openInv.get() && mc.gui.screen() == null) {
                    // open the inventory ourselves and fetch it
                    var inv = new net.minecraft.client.gui.screens.inventory.InventoryScreen(p);
                    mc.gui.setScreen(inv);
                    startCursor(mc, slot, p.inventoryMenu.getSlot(slot).getItem().getItem(), true);
                }
                return;
            }
            fPrev = Inv.selected(p);
            Inv.select(p, hb);
            fStage = 1;
            return;
        }
        Inv.swapWithOffhand(mc, slot);
        wait = delay.getInt();
    }

    // ---- inventory cursor --------------------------------------------------------------------------

    private void startCursor(Minecraft mc, int slot, Item item, boolean opened) {
        cStage = 1;
        cSlot = slot;
        cItem = item;
        openedByUs = opened;
        cPlanned = false;   // start point and duration are worked out on the first frame (screen size known)
        cScreen = mc.gui.screen();
        // a moment to "find" the item before the hand starts moving
        cStart = System.currentTimeMillis() + dev.utilclient.util.Human.delay(opened ? 140 : 70, 30, 40);
        cDur = 0;
    }

    private void resetCursor() {
        cStage = 0;
        cSlot = -1;
        cScreen = null;
        openedByUs = false;
        cPlanned = false;
    }

    /** Called from the screen hook every frame and from onTick: plans the path once the screen exists. */
    private float[] slotCentre(net.minecraft.client.gui.screens.Screen screen) {
        int left, top;
        if (screen instanceof dev.utilclient.mixin.AbstractContainerScreenAccessor acc) {
            left = acc.dw$left();
            top = acc.dw$top();
        } else {
            left = (screen.width - 176) / 2;
            top = (screen.height - 166) / 2;
        }
        var sl = Minecraft.getInstance().player.inventoryMenu.getSlot(cSlot);
        return new float[] {left + sl.x + 8, top + sl.y + 8};
    }

    private void plan(Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
        if (cPlanned) return;
        cPlanned = true;
        if (openedByUs) {
            sx = screen.width / 2f;   // the cursor starts in the middle when the inventory opens
            sy = screen.height / 2f;
        } else {
            sx = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
            sy = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
        }
        float[] t = slotCentre(screen);
        double d = Math.hypot(t[0] - sx, t[1] - sy);
        // Fitts' law: bigger and farther = slower; faster at high sensitivity
        double ms = (90 + 95 * (Math.log(d / 16.0 + 1) / Math.log(2))) / dev.utilclient.util.Human.sensScale() / cursorSpeed.get();
        cDur = dev.utilclient.util.Human.delay(ms, ms * 0.12, ms * 0.6);
        arc = (float) ((Math.random() - 0.5) * 0.25 * d);
    }

    private void cursorTick(Minecraft mc) {
        var screen = mc.gui.screen();
        if (screen != cScreen || !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
            resetCursor();   // closed or replaced by the player: give up quietly
            return;
        }
        long now = System.currentTimeMillis();
        if (cStage == 1) {
            if (!cPlanned) plan(mc, screen);
            if (now < cStart + cDur) return;
            if (now < cStart + cDur + 40) return;   // settle on the slot before pressing
            var st = mc.player.inventoryMenu.getSlot(cSlot).getItem();
            if (!st.isEmpty() && st.is(cItem) && Inv.canClick(mc)) Inv.swapWithOffhand(mc, cSlot);   // "F" on the slot
            cStage = 2;
            cNext = now + dev.utilclient.util.Human.delay(openedByUs ? 130 : 60, 35, 50);
        } else if (cStage == 2) {
            if (now < cNext) return;
            if (openedByUs) screen.onClose();
            resetCursor();
            wait = delay.getInt() + 1;
        }
    }

    /** From UtilClient AFTER_INIT for inventory screens: draws the moving cursor. */
    public void attach(net.minecraft.client.gui.screens.Screen screen) {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register((s, g, mx, my, tick) -> {
            if (cStage == 0 || s != cScreen || !showCursor.get()) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (!cPlanned) plan(mc, s);
                float[] t = slotCentre(s);
                long now = System.currentTimeMillis();
                double k = cStage == 2 ? 1 : cDur <= 0 ? 0 : Math.max(0, Math.min(1, (now - cStart) / (double) cDur));
                double e = k * k * k * (10 - 15 * k + 6 * k * k);   // minimum-jerk curve, like a real hand
                double dx = t[0] - sx, dy = t[1] - sy, len = Math.max(1, Math.hypot(dx, dy));
                double bow = Math.sin(Math.PI * e) * arc;
                int cx = (int) Math.round(sx + dx * e - dy / len * bow), cy = (int) Math.round(sy + dy * e + dx / len * bow);
                g.nextStratum();
                int acc = ModuleManager.CLICK_GUI.accent(0f);
                if (k >= 1) dev.utilclient.util.Draw.round(g, (int) t[0] - 9, (int) t[1] - 9, (int) t[0] + 9, (int) t[1] + 9, 99,
                        dev.utilclient.util.Draw.withAlpha(acc, 0.35f));
                dev.utilclient.util.Draw.round(g, cx - 4, cy - 4, cx + 4, cy + 4, 99, 0xFFFFFFFF);
                dev.utilclient.util.Draw.round(g, cx - 3, cy - 3, cx + 3, cy + 3, 99, acc);
            } catch (Throwable ignored) {
                // cosmetic
            }
        });
    }

    @Override
    protected void onDisable() {
        if (openedByUs && cScreen != null && Minecraft.getInstance().gui.screen() == cScreen) cScreen.onClose();
        resetCursor();
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
