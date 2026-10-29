package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Client-side night vision. Xray can switch it on too. */
public final class FullbrightModule extends Module {
    private static boolean applied;

    public FullbrightModule() { super("Fullbright", "See in the dark", Category.RENDER); }

    @Override public void onTick(Minecraft mc) { update(mc); }

    @Override protected void onDisable() { update(Minecraft.getInstance()); }

    /** Adds or removes our night vision depending on Fullbright and Xray. */
    static void update(Minecraft mc) {
        if (mc.player == null) { applied = false; return; }
        boolean want = ModuleManager.FULLBRIGHT.isEnabled() || ModuleManager.XRAY.wantsFullbright();
        if (want) {
            if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            }
            applied = true;
        } else if (applied) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
            applied = false;
        }
    }
}
