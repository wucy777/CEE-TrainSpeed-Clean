package com.george_vi.electroenergetics.content.railway_electrification.packets;

import com.george_vi.electroenergetics.CEEPackets;
import com.george_vi.electroenergetics.content.railway_electrification.riders.SeatIntentTracker;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Client-side seat intent (E-16): the client decides which seat looked nearest to it,
 * the server validates and resolves conflicts with its own authority.
 */
public record ReportSeatIntentPacket(UUID trainId, int carriageIndex, int seatIndex) implements ServerboundPacketPayload {

    public static final StreamCodec<ByteBuf, ReportSeatIntentPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC), ReportSeatIntentPacket::trainId,
            ByteBufCodecs.VAR_INT, ReportSeatIntentPacket::carriageIndex,
            ByteBufCodecs.VAR_INT, ReportSeatIntentPacket::seatIndex,
            ReportSeatIntentPacket::new
    );

    @Override
    public void handle(ServerPlayer player) {
        SeatIntentTracker.report(player, trainId, carriageIndex, seatIndex);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CEEPackets.REPORT_SEAT_INTENT;
    }
}
