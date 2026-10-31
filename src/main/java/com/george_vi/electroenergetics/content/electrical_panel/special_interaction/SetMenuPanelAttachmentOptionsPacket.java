package com.george_vi.electroenergetics.content.electrical_panel.special_interaction;

import com.george_vi.electroenergetics.CEEPackets;
import com.george_vi.electroenergetics.content.electrical_panel.link.ElectricalPanelLink;
import com.george_vi.electroenergetics.content.electrical_panel.link.IPanelAttachmentMenu;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record SetMenuPanelAttachmentOptionsPacket(byte option, boolean value) implements ServerboundPacketPayload {
    public static final StreamCodec<ByteBuf, SetMenuPanelAttachmentOptionsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, SetMenuPanelAttachmentOptionsPacket::option,
            ByteBufCodecs.BOOL, SetMenuPanelAttachmentOptionsPacket::value,
            SetMenuPanelAttachmentOptionsPacket::new
    );

    public static final byte RETURN_TO_ORIGINAL = 0;

    @Override
    public void handle(ServerPlayer player) {
        if (!(player.containerMenu instanceof IPanelAttachmentMenu menu))
            return;

        if (option == RETURN_TO_ORIGINAL) {
            if (menu.getPanelAttachment() instanceof ElectricalPanelLink attachment &&
                    attachment.hasReturnToZeroOption()) {
                attachment.setReturnToZero(value);
            }
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CEEPackets.SET_MENU_PANEL_ATTACHMENT_OPTIONS;
    }
}
