package com.george_vi.electroenergetics.content.electric_fan;

import com.george_vi.electroenergetics.CEEBlockEntityTypes;
import com.george_vi.electroenergetics.CEENodeConfigurations;
import com.george_vi.electroenergetics.CEEShapes;
import com.george_vi.electroenergetics.CEESimulatedDevices;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.levelWrappers.WrappedLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ElectricFanBlock extends DirectionalRolledDeviceBlock<ElectricFanDevice> implements IBE<ElectricFanBlockEntity> {
    public static final BooleanProperty FORWARD = BooleanProperty.create("forward");

    public ElectricFanBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FORWARD, false));
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(FORWARD))
            return CEENodeConfigurations.ELECTRIC_FAN_FORWARD.getNodes(state.getValue(FACING), state.getValue(ROLL));
        return CEENodeConfigurations.ELECTRIC_FAN.getNodes(state.getValue(FACING), state.getValue(ROLL));
    }

    @Override
    public @Nullable Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        if (state.getValue(FORWARD))
            return CEENodeConfigurations.ELECTRIC_FAN_FORWARD.getNodePos(state.getValue(FACING), state.getValue(ROLL), id);
        return CEENodeConfigurations.ELECTRIC_FAN.getNodePos(state.getValue(FACING), state.getValue(ROLL), id);
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 2/16f;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FORWARD))
            return CEEShapes.ELECTRIC_FAN_FORWARD.get(state.getValue(FACING));
        return CEEShapes.ELECTRIC_FAN.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getNearestLookingDirection();
        direction = context.getPlayer() == null || context.getPlayer().isShiftKeyDown() ? direction.getOpposite() : direction;
        if (direction.getAxis().isVertical()) {
            return defaultBlockState().setValue(FACING, direction)
                    .setValue(ROLL, context.getHorizontalDirection().getAxis() == Direction.Axis.X);
        }
        return defaultBlockState().setValue(FACING,
                direction);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        if (targetedFace.getAxis() != originalState.getValue(FACING).getAxis())
            return originalState.cycle(FORWARD);
        return super.getRotatedBlockState(originalState, targetedFace);
    }

    @Override
    public void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, worldIn, pos, oldState, isMoving);
        blockUpdate(worldIn, pos);
    }

    @Override
    public void updateIndirectNeighbourShapes(BlockState stateIn, LevelAccessor worldIn, BlockPos pos, int flags, int count) {
        super.updateIndirectNeighbourShapes(stateIn, worldIn, pos, flags, count);
        blockUpdate(worldIn, pos);
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos,
                                boolean isMoving) {
        blockUpdate(worldIn, pos);
    }

    protected void blockUpdate(LevelAccessor worldIn, BlockPos pos) {
        if (worldIn instanceof WrappedLevel)
            return;
        notifyFanBlockEntity(worldIn, pos);
    }

    protected void notifyFanBlockEntity(LevelAccessor world, BlockPos pos) {
        withBlockEntityDo(world, pos, ElectricFanBlockEntity::blockInFrontChanged);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORWARD);
    }

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        blockUpdate(context.getLevel(), context.getClickedPos());
        return newState;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public SimulatedDeviceType<ElectricFanDevice> getDevice() {
        return CEESimulatedDevices.ELECTRIC_FAN.get();
    }

    @Override
    public Class<ElectricFanBlockEntity> getBlockEntityClass() {
        return ElectricFanBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectricFanBlockEntity> getBlockEntityType() {
        return CEEBlockEntityTypes.ELECTRIC_FAN.get();
    }
}
