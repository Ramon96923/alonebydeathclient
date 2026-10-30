package dev.utilclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to Minecraft internals used by FastPlace, OldPvP and AutoClicker. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    int abd$getRightClickDelay();

    @Accessor("rightClickDelay")
    void abd$setRightClickDelay(int delay);

    /** Ticks left before the next left-click is allowed; OldPvP zeroes it so misses don't lock clicks. */
    @Accessor("missTime")
    void abd$setMissTime(int missTime);

    /** Runs one vanilla left-click (attack/break), exactly as clicking the mouse would; used by AutoClicker. */
    @Invoker("startAttack")
    boolean abd$startAttack();
}
