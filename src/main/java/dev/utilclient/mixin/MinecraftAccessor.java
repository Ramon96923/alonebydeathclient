package dev.utilclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** FastPlace: the delay between right-click uses. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    int abd$getRightClickDelay();

    @Accessor("rightClickDelay")
    void abd$setRightClickDelay(int delay);
}
