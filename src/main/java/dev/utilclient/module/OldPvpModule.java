package dev.utilclient.module;

import dev.utilclient.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;

/**
 * 1.8-style combat. Removes the attack-strength cooldown so every click lands at full damage like the old
 * days (via a non-required hook on Player.getCurrentItemAttackStrengthDelay), and clears the miss timer so a
 * whiffed swing doesn't lock you out. Pair with AutoClicker or just spam click.
 */
public final class OldPvpModule extends Module {
    private final BoolSetting noCooldown = bool("No cooldown", true).desc("Always full attack charge (1.8 damage)");
    private final BoolSetting noMissDelay = bool("No miss delay", true).desc("Missed swings don't pause your clicks");

    public OldPvpModule() { super("OldPvP", "1.8-style combat (no attack cooldown)", Category.COMBAT); }

    /** Read by PlayerMixin: when true, the attack cooldown is forced to zero ticks. */
    public boolean noCooldown() { return isEnabled() && noCooldown.get(); }

    @Override
    public void onTick(Minecraft mc) {
        if (noMissDelay.get() && mc instanceof MinecraftAccessor acc) acc.abd$setMissTime(0);
    }
}
