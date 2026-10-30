package dev.utilclient.module;

import dev.utilclient.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Throws splash potions at your feet: healing when you're low, and strength/speed/fire resistance whenever
 * they run out. The throw aims down for the server only, so your view doesn't move.
 */
public final class AutoPotModule extends Module {
    private final NumberSetting health = num("Heal below", 10, 1, 19, 1).unit("hp");
    private final BoolSetting strength = bool("Keep strength", true);
    private final BoolSetting speed = bool("Keep speed", true);
    private final BoolSetting fireRes = bool("Keep fire resistance", false);
    private final NumberSetting delay = num("Delay", 10, 2, 40, 1).unit("t").desc("Ticks between throws");
    private final BoolSetting onlyGround = bool("Only on ground", true).desc("Throwing mid-air often misses you");

    private int timer;

    public AutoPotModule() { super("AutoPot", "Throw healing and buff potions automatically", Category.COMBAT); }

    private static boolean gives(ItemStack s, Holder<MobEffect> effect) {
        if (!s.is(Items.SPLASH_POTION)) return false;
        for (MobEffectInstance e : s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects()) {
            if (e.getEffect().equals(effect)) return true;
        }
        return false;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (timer-- > 0) return;
        LocalPlayer p = mc.player;
        if (mc.gameMode == null || mc.gui.screen() != null || p.isUsingItem()) return;
        if (onlyGround.get() && !p.onGround()) return;

        Holder<MobEffect> want = null;
        if (p.getHealth() <= health.getInt()) want = MobEffects.INSTANT_HEALTH;
        else if (strength.get() && !p.hasEffect(MobEffects.STRENGTH)) want = MobEffects.STRENGTH;
        else if (speed.get() && !p.hasEffect(MobEffects.SPEED)) want = MobEffects.SPEED;
        else if (fireRes.get() && !p.hasEffect(MobEffects.FIRE_RESISTANCE)) want = MobEffects.FIRE_RESISTANCE;
        if (want == null) return;
        Holder<MobEffect> effect = want;
        int slot = Inv.hotbar(p, s -> gives(s, effect));
        if (slot < 0) return;

        int prev = Inv.selected(p);
        Inv.select(p, slot);
        float pitch = p.getXRot();
        p.setXRot(90f);
        p.connection.send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), 90f, p.onGround(), p.horizontalCollision));
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        p.setXRot(pitch);
        Inv.select(p, prev);
        timer = delay.getInt();
    }
}
