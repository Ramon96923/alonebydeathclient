package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
    // If the game fails to start with a mixin error here, check Block.shouldRenderFace's
    // parameter list in the 26.3 sources and match it exactly.
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void utilclient$xray(BlockState state, BlockGetter level, BlockPos pos,
                                        Direction face, BlockPos neighborPos,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.XRAY.isEnabled()) {
            cir.setReturnValue(ModuleManager.XRAY.isTarget(state));
        }
    }
}
