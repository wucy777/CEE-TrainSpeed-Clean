package com.george_vi.electroenergetics.content.transmission_distribution.transformer;

import com.george_vi.electroenergetics.CEEPackets;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelDevice;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.BaseEnergyMeterAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.TransformerAttachment;
import com.george_vi.electroenergetics.content.energy_meter.EnergyMeterBlockEntity;
import com.george_vi.electroenergetics.content.energy_meter.EnergyMeterDevice;
import com.george_vi.electroenergetics.content.energy_meter.TriPolarEnergyMeterDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.simibubi.create.AllSoundEvents;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

public record ConfigureTransformerAttachmentPacket(int primaryTurns, int secondaryTurns, BlockPos pos, int panelSlot, boolean invert) implements ServerboundPacketPayload {
    public static final StreamCodec<ByteBuf, ConfigureTransformerAttachmentPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfigureTransformerAttachmentPacket::primaryTurns,
            ByteBufCodecs.VAR_INT, ConfigureTransformerAttachmentPacket::secondaryTurns,
            BlockPos.STREAM_CODEC, ConfigureTransformerAttachmentPacket::pos,
            ByteBufCodecs.VAR_INT, ConfigureTransformerAttachmentPacket::panelSlot,
            ByteBufCodecs.BOOL, ConfigureTransformerAttachmentPacket::invert,
            ConfigureTransformerAttachmentPacket::new
    );

    @Override
    public void handle(ServerPlayer player) {

        if (player.distanceToSqr(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f) > 30 * 30)
            return;

        if (player.level().getBlockEntity(pos) instanceof ElectricalPanelBlockEntity be) {
            PanelAttachment[] attachments = be.getAttachments();
            if (panelSlot < 0 || panelSlot >= attachments.length)
                return;
            if (attachments[panelSlot] instanceof TransformerAttachment attachment) {
                attachment.inverted = invert;
                attachment.primaryTurns = Mth.clamp(primaryTurns, 1, 240);
                attachment.secondaryTurns = Mth.clamp(secondaryTurns, 1, 240);
            }
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CEEPackets.CONFIGURE_TRANSFORMER_ATTACHMENT;
    }
}
