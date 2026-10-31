package com.george_vi.electroenergetics.content.electrical_panel.special_interaction;

import com.george_vi.electroenergetics.CEEPackets;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelDevice;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

public record SetPanelAttachmentOptionsPacket(BlockPos pos, int panelSlot, byte option, boolean value) implements ServerboundPacketPayload {
    public static final StreamCodec<ByteBuf, SetPanelAttachmentOptionsPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetPanelAttachmentOptionsPacket::pos,
            ByteBufCodecs.VAR_INT, SetPanelAttachmentOptionsPacket::panelSlot,
            ByteBufCodecs.BYTE, SetPanelAttachmentOptionsPacket::option,
            ByteBufCodecs.BOOL, SetPanelAttachmentOptionsPacket::value,
            SetPanelAttachmentOptionsPacket::new
    );

    public static final byte HOLD_STATUS = 0;

    @Override
    public void handle(ServerPlayer player) {
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        if (player.distanceToSqr(pos.getCenter()) > range * range)
            return;

        DevicesSavedData sd = DevicesSavedData.load((ServerLevel) player.level());

        ElectricalPanelDevice device = sd.getDevice(pos, ElectricalPanelDevice.class);
        if (device == null)
            return;

        if (panelSlot >= device.attachments.length || panelSlot < 0)
            return;

        PanelAttachment a = device.attachments[panelSlot];
        if (option == HOLD_STATUS) {
            if (a instanceof IHoldStatusPanelAttachment attachment) {
                attachment.setHoldStatus(value);
            }
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CEEPackets.SET_PANEL_ATTACHMENT_OPTIONS;
    }
}
