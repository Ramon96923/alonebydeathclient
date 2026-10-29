package dev.utilclient.module;

import dev.utilclient.util.CombatState;
import dev.utilclient.util.DamageCalc;
import dev.utilclient.util.Inv;
import dev.utilclient.util.Placer;
import dev.utilclient.util.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Anchor aura: places a respawn anchor next to the target, charges it with glowstone and detonates it.
 * Damage is predicted for both of you. Anchors only explode outside the Nether, so it idles there.
 */
public final class AutoAnchorModule extends Module {
    private final NumberSetting targetRange = num("Target range", 10, 4, 16, 1).unit("m");
    private final BoolSetting players = bool("Players", true);
    private final BoolSetting hostile = bool("Hostile mobs", true);
    private final BoolSetting passive = bool("Passive mobs", false);
    private final NumberSetting placeRange = num("Place range", 4.5, 2, 6, 0.1).unit("m");
    private final NumberSetting minDamage = num("Min damage", 6, 0, 36, 0.5);
    private final NumberSetting maxSelf = num("Max self damage", 8, 0, 36, 0.5);
    private final BoolSetting antiSuicide = bool("Anti suicide", true);
    private final NumberSetting delay = num("Delay", 2, 0, 10, 1).unit("t").desc("Ticks between each step");
    private final BoolSetting swapBack = bool("Swap back", true).desc("Return to your previous hotbar slot afterwards");
    private final ModeSetting rotate = mode("Rotate", "Packet", "None", "Packet");
    private final BoolSetting pauseOnEat = bool("Pause while eating", true);

    private int wait;
    private int returnSlot = -1;

    public AutoAnchorModule() { super("AutoAnchor", "Place, charge & detonate respawn anchors", Category.COMBAT); }

    private boolean safe(LocalPlayer p, float self) {
        return self <= maxSelf.get() && (!antiSuicide.get() || self < DamageCalc.totalHealth(p) - 1f);
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || p.isSpectator() || mc.gui.screen() != null) return;
        if (mc.level.dimension() == Level.NETHER) return;
        if (pauseOnEat.get() && p.isUsingItem()) return;
        if (wait > 0) { wait--; return; }

        LivingEntity target = (LivingEntity) TargetFilter.best(mc, targetRange.get(),
                e -> TargetFilter.accept(e, p, players.get(), hostile.get(), passive.get(), true), "Distance");
        if (target == null) {
            restoreSlot(p);
            return;
        }
        CombatState.target(target);
        Vec3 eye = p.getEyePosition();

        // 1) an anchor already near the target?
        BlockPos anchor = null;
        float best = 0;
        BlockPos tp = target.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(tp.offset(-4, -3, -4), tp.offset(4, 3, 4))) {
            if (!mc.level.getBlockState(b).is(Blocks.RESPAWN_ANCHOR)) continue;
            if (Vec3.atCenterOf(b).distanceTo(eye) > placeRange.get()) continue;
            float dmg = DamageCalc.anchor(target, b);
            if (dmg > best) { best = dmg; anchor = b.immutable(); }
        }

        if (anchor != null) {
            BlockState st = mc.level.getBlockState(anchor);
            int charges = st.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
            if (charges == 0) {
                if (!hold(p, Items.GLOWSTONE)) return;
                Placer.interact(mc, anchor, Direction.UP, InteractionHand.MAIN_HAND, rotate.get());
            } else {
                if (!safe(p, DamageCalc.anchor(p, anchor))) return;
                // detonate with anything that isn't glowstone
                if (p.getMainHandItem().is(Items.GLOWSTONE)) {
                    int other = Inv.hotbar(p, s -> !s.is(Items.GLOWSTONE));
                    if (other < 0) return;
                    remember(p);
                    Inv.select(p, other);
                }
                Placer.interact(mc, anchor, Direction.UP, InteractionHand.MAIN_HAND, rotate.get());
            }
            wait = delay.getInt();
            return;
        }

        // 2) place a new anchor where it hurts the target most and us least
        if (Inv.hotbar(p, Items.RESPAWN_ANCHOR) < 0) return;
        BlockPos spot = null;
        float bestDmg = 0;
        for (BlockPos b : BlockPos.betweenClosed(tp.offset(-3, -1, -3), tp.offset(3, 2, 3))) {
            if (Vec3.atCenterOf(b).distanceTo(eye) > placeRange.get()) continue;
            if (!Placer.canPlaceAt(mc, b)) continue;
            float dmg = DamageCalc.anchor(target, b);
            if (dmg < minDamage.get() || dmg <= bestDmg) continue;
            if (!safe(p, DamageCalc.anchor(p, b))) continue;
            bestDmg = dmg;
            spot = b.immutable();
        }
        if (spot == null || !hold(p, Items.RESPAWN_ANCHOR)) return;
        Placer.place(mc, spot, InteractionHand.MAIN_HAND, rotate.get(), true);
        wait = delay.getInt();
    }

    private void remember(LocalPlayer p) {
        if (returnSlot < 0) returnSlot = Inv.selected(p);
    }

    private boolean hold(LocalPlayer p, net.minecraft.world.item.Item item) {
        if (p.getMainHandItem().is(item)) return true;
        int slot = Inv.hotbar(p, item);
        if (slot < 0) return false;
        remember(p);
        Inv.select(p, slot);
        return true;
    }

    private void restoreSlot(LocalPlayer p) {
        if (returnSlot >= 0 && swapBack.get()) Inv.select(p, returnSlot);
        returnSlot = -1;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) restoreSlot(mc.player);
        returnSlot = -1;
    }
}
