package com.george_vi.electroenergetics.content.clear_glass;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The frameless extra-clear glass (H-01): fully transparent with no border and no
 * pattern, just enough tint to read as glass instead of air.
 */
public class ClearGlassBlock extends TransparentBlock {

    public ClearGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
