package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.foundation.redstone.DirectionalAnalogOutputBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ComparatorBlock.class)
public class ComparatorBlockMixin {
    @WrapOperation(method = "getInputSignal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getAnalogOutputSignal(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I"))
    private int getDirectionalSignal(BlockState instance, Level level, BlockPos pos, Operation<Integer> original, @Local Direction direction) {
        if (instance.getBlock() instanceof DirectionalAnalogOutputBlock b) {
            return b.getAnalogOutputSignal(instance, level, pos, direction);
        }
        return original.call(instance, level, pos);
    }
}
