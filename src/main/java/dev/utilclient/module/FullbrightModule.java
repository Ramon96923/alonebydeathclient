package dev.utilclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Gamma: paints the lightmap fully bright (also used by Xray). Night vision: client-side effect. */
public final class FullbrightModule extends Module {
    private final ModeSetting mode = mode("Mode", "Gamma", "Gamma", "Night vision")
            .desc("Gamma lights everything evenly, Night vision uses the potion effect");
    private static boolean applied;

    public FullbrightModule() { super("Fullbright", "See in the dark", Category.RENDER); }

    @Override public String info() { return mode.get(); }

    /** Read by LightmapMixin every frame. */
    public boolean gamma() { return isEnabled() && mode.is("Gamma"); }

    @Override public void onTick(Minecraft mc) { update(mc); }

    @Override protected void onDisable() { update(Minecraft.getInstance()); }

    static void update(Minecraft mc) {
        if (mc.player == null) { applied = false; return; }
        FullbrightModule f = ModuleManager.FULLBRIGHT;
        boolean want = f.isEnabled() && f.mode.is("Night vision");
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
