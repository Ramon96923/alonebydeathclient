package dev.utilclient.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** StorageESP: the block entities (chests, barrels...) in a loaded chunk. Same accessor as Meteor's. */
@Mixin(ChunkAccess.class)
public interface ChunkAccessAccessor {
    @Accessor("blockEntities")
    Map<BlockPos, BlockEntity> abd$getBlockEntities();
}
