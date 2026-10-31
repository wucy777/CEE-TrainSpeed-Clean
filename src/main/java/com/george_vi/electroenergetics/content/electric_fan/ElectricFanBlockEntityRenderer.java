package com.george_vi.electroenergetics.content.electric_fan;

import com.george_vi.electroenergetics.CEEPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class ElectricFanBlockEntityRenderer extends SmartBlockEntityRenderer<ElectricFanBlockEntity> {
    public ElectricFanBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ElectricFanBlockEntity blockEntity, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = blockEntity.getBlockState();

        Direction facing = blockEntity.getBlockState().getValue(ElectricFanBlock.FACING);
        CachedBuffers.partial(CEEPartialModels.ELECTRIC_FAN_BLADE, state)
                .light(light)
                .rotateXCenteredDegrees(facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 270 : 0)
                .rotateZCenteredDegrees(facing.getAxis().isHorizontal() ? (int) -facing.toYRot() + 180 : 0)
                .rotateYCenteredDegrees(Mth.lerp(partialTicks, blockEntity.prevRotation, blockEntity.rotation))
                .translate(0, state.getValue(ElectricFanBlock.FORWARD) ? 6/16f : 0, 0)
                .renderInto(ms, buffer.getBuffer(RenderType.CUTOUT));
    }
}
