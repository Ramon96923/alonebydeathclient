package dev.utilclient.module;

import dev.utilclient.util.Human;
import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Hold right-click on obsidian: places a crystal where you look and hits it once it's up. Everything goes
 * through your real crosshair (no rotations), which is how DonutSMP "legit" crystal macros work.
 * <p>
 * Humanized: random place/break gaps, a reaction delay before hitting a fresh crystal, the shared CPS limit,
 * and never swapping to crystals and clicking in the same tick. Use AutoCrystal for full automation.
 */
public final class CrystalMacroModule extends Module {
    private final NumberSetting placeDelay = num("Place interval", 110, 0, 400, 10).unit("ms")
            .desc("Average time between placements");
    private final NumberSetting breakDelay = num("Break interval", 90, 0, 400, 10).unit("ms");
    private final NumberSetting crystalAge = num("Crystal age", 70, 0, 300, 10).unit("ms")
            .desc("Wait this long after a crystal appears before hitting it");
    private final BoolSetting autoSwitch = bool("Switch to crystals", true).desc("Hold crystals while looking at obsidian");

    private long nextPlace, nextBreak;
    private int lastTick = -1;
    private final Human.Reaction seen = new Human.Reaction();

    public CrystalMacroModule() { super("CrystalMacro", "Fast crystal place + break on right-click", Category.COMBAT); }

    @Override protected void onDisable() { seen.clear(); }

    private long gap(NumberSetting s) {
        double m = s.get();
        return Human.on() ? Human.delay(m, m * 0.28, m * 0.4) : (long) m;
    }

    private boolean mayClick() { return lastTick != Human.tick() && Human.canClick(); }

    private void clicked() {
        lastTick = Human.tick();
        if (Human.on()) Human.clicked();
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null || !mc.options.keyUse.isDown()) return;
        long now = System.currentTimeMillis();

        if (mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal crystal) {
            long age = Human.on() ? Human.delay(crystalAge.get(), crystalAge.get() * 0.3, crystalAge.get() * 0.5) : 0;
            if (now >= nextBreak && seen.ready(crystal.getId(), age) && mayClick()) {
                mc.gameMode.attack(p, crystal);
                p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
                clicked();
                nextBreak = now + gap(breakDelay);
            }
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult bh) || now < nextPlace) return;
        BlockPos pos = bh.getBlockPos();
        var st = mc.level.getBlockState(pos);
        if (!(st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK)) || !mc.level.getBlockState(pos.above()).isAir()) return;
        InteractionHand hand = p.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (hand == InteractionHand.MAIN_HAND && !p.getMainHandItem().is(Items.END_CRYSTAL)) {
            int slot = autoSwitch.get() ? Inv.hotbar(p, Items.END_CRYSTAL) : -1;
            if (slot < 0) return;
            Inv.select(p, slot);
            if (Human.on()) return;   // swap now, place on a later tick like a real hand
        }
        if (!Human.swapSettled() || !mayClick()) return;
        mc.gameMode.useItemOn(p, hand, bh);
        p.swing(hand, p.getItemInHand(hand).getInteractAnimation(), false);
        clicked();
        nextPlace = now + gap(placeDelay);
    }
}
