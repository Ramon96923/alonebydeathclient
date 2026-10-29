package dev.utilclient.module;

import dev.utilclient.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;

/** Removes the delay between right-clicks (placing blocks, using items). */
public final class FastPlaceModule extends Module {
    private final NumberSetting delay = num("Delay", 0, 0, 3, 1).unit("t").desc("Vanilla is 4");
    private final BoolSetting onlyBlocks = bool("Only blocks", true).desc("Only speed up block placing");

    public FastPlaceModule() { super("FastPlace", "Place blocks faster", Category.PLAYER); }

    @Override
    public void onTick(Minecraft mc) {
        if (onlyBlocks.get() && !(mc.player.getMainHandItem().getItem() instanceof BlockItem)
                && !(mc.player.getOffhandItem().getItem() instanceof BlockItem)) return;
        MinecraftAccessor acc = (MinecraftAccessor) (Object) mc;
        if (acc.abd$getRightClickDelay() > delay.getInt()) acc.abd$setRightClickDelay(delay.getInt());
    }
}
