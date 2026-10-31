package com.george_vi.electroenergetics.content.railway_electrification.packets;

import com.george_vi.electroenergetics.CEEPackets;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricTrainData;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.TrainPantographEntry;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.george_vi.electroenergetics.mixin_interfaces.IPantographList;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Per-pantograph auto raise switch (M172: 逐弓独立，默认关，开启时禁用手动升降).
 */
public record SetAutoRaisePacket(UUID trainId, int carriageIndex, int x, int y, int z, boolean autoRaise)
        implements ServerboundPacketPayload {

    public static final StreamCodec<ByteBuf, SetAutoRaisePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC), SetAutoRaisePacket::trainId,
            ByteBufCodecs.VAR_INT, SetAutoRaisePacket::carriageIndex,
            ByteBufCodecs.VAR_INT, SetAutoRaisePacket::x,
            ByteBufCodecs.VAR_INT, SetAutoRaisePacket::y,
            ByteBufCodecs.VAR_INT, SetAutoRaisePacket::z,
            ByteBufCodecs.BOOL, SetAutoRaisePacket::autoRaise,
            SetAutoRaisePacket::new
    );

    @Override
    public void handle(ServerPlayer player) {
        Train train = Create.RAILWAYS.trains.get(trainId);
        if (train == null || carriageIndex < 0 || carriageIndex >= train.carriages.size())
            return;
        ElectricTrainData data = ((ICEETrainExtension) train).getElectricTrainData();
        if (data.controlsLocked || data.emergencyPenalty)
            return;

        Carriage carriage = train.carriages.get(carriageIndex);
        BlockPos pos = new BlockPos(x, y, z);
        for (TrainPantographEntry entry : ((IPantographList) carriage).electroEnergetics$getPantographList()) {
            if (entry.originalPos.equals(pos)) {
                entry.autoRaise = autoRaise;
                return;
            }
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CEEPackets.SET_AUTO_RAISE;
    }
}
