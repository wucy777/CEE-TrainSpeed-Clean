package com.george_vi.electroenergetics.foundation.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface DirectionalAnalogOutputBlock {
    /**
     * Returns the analog signal this block emits. This is the signal a comparator can read from it from a specific side.
     *
     */
    int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction dir);
}
