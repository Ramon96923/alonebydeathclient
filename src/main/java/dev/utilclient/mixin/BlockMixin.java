package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
    // Signature confirmed from the 26.3 crash report: (BlockState, BlockState, Direction)
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void utilclient$xray(BlockState state, BlockState neighborState, Direction face,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.XRAY.isEnabled()) {
            cir.setReturnValue(ModuleManager.XRAY.isTarget(state));
        }
    }
}
