package com.george_vi.electroenergetics.mixins.compat;

import com.george_vi.electroenergetics.content.electric_fan.ElectricFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(ElectricFanBlockEntity.class)
public class ElectricFanBlockEntityMixin extends SmartBlockEntity implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller {
    @Shadow
    private float actualSpeed;

    @Unique
    private boolean electroenergetics$blocked;

    public ElectricFanBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }

    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        final BlockPos frontPos = this.getBlockPos().relative(this.getBlockState().getValue(EncasedFanBlock.FACING));
        this.electroenergetics$blocked = !this.level.getBlockState(frontPos).isAir();
    }


    @Override
    public Direction getBlockDirection() {
        return this.getBlockState().getValue(EncasedFanBlock.FACING);
    }

    @Override
    public boolean isActive() {
        return !this.electroenergetics$blocked && Math.abs(this.sable$getPropSpeed()) > 0.01f;
    }

    protected float sable$getPropSpeed() {
        final float rotationSpeed = actualSpeed * 8;
        return this.getBlockDirection().getAxisDirection().getStep() * rotationSpeed * (10 / 3f);
    }

    @Override
    public double getAirflow() {
        return 0.1f * this.sable$getPropSpeed();
    }

    @Override
    public double getThrust() {
        return 0.3f * this.sable$getPropSpeed();
    }

    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }
}
