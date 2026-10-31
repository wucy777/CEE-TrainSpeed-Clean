package com.george_vi.electroenergetics.content.railway_electrification.packets;

import com.george_vi.electroenergetics.CEEPackets;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricTrainData;
import com.george_vi.electroenergetics.content.railway_electrification.TrainControlModeLogic;
import com.george_vi.electroenergetics.content.railway_electrification.TrainGear;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.TrainPantographEntry;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.george_vi.electroenergetics.mixin_interfaces.IPantographList;
import com.george_vi.electroenergetics.simulation.infrastructure.CatenaryModule;
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
 * Per-pantograph raise/lower (D-02/D-03/D-07/D-08/D-10/M172).
 */
public record SetPantographPacket(UUID trainId, int carriageIndex, int x, int y, int z, boolean raise)
        implements ServerboundPacketPayload {

    public static final StreamCodec<ByteBuf, SetPantographPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC), SetPantographPacket::trainId,
            ByteBufCodecs.VAR_INT, SetPantographPacket::carriageIndex,
            ByteBufCodecs.VAR_INT, SetPantographPacket::x,
            ByteBufCodecs.VAR_INT, SetPantographPacket::y,
            ByteBufCodecs.VAR_INT, SetPantographPacket::z,
            ByteBufCodecs.BOOL, SetPantographPacket::raise,
            SetPantographPacket::new
    );

    @Override
    public void handle(ServerPlayer player) {
        Train train = Create.RAILWAYS.trains.get(trainId);
        if (train == null || carriageIndex < 0 || carriageIndex >= train.carriages.size())
            return;
        Carriage carriage = train.carriages.get(carriageIndex);
        ElectricTrainData data = ((ICEETrainExtension) train).getElectricTrainData();

        BlockPos pos = new BlockPos(x, y, z);
        TrainPantographEntry entry = find(carriage, pos);
        if (entry == null)
            return;

        // While the emergency brake lock is on, the pantographs may not be touched (C-09).
        if (data.controlsLocked || data.emergencyPenalty) {
            if (raise)
                player.displayClientMessage(CEELang.translateDirect("pantograph.locked"), true);
            return;
        }

        // Auto-raise pantographs are not manually controllable (M172).
        if (entry.autoRaise) {
            player.displayClientMessage(CEELang.translateDirect("pantograph.auto_controlled"), true);
            return;
        }

        if (!raise) {
            // Lowering is always allowed; lowering the last live one shifts to COAST first (D-04).
            entry.active = false;
            if (!TrainControlModeLogic.anyPantographRaised(train) && data.gear.isTraction()
                    && data.gear != TrainGear.REVERSE)
                data.gear = TrainGear.COAST;
            return;
        }

        // Raising needs a wire within reach (D-03).
        if (!CatenaryModule.isPantographWithinReach(train, carriage, entry)) {
            entry.active = false;
            player.displayClientMessage(CEELang.translateDirect("pantograph.no_wire"), true);
            return;
        }
        entry.active = true;
    }

    private static TrainPantographEntry find(Carriage carriage, BlockPos pos) {
        for (TrainPantographEntry entry : ((IPantographList) carriage).electroEnergetics$getPantographList()) {
            if (entry.originalPos.equals(pos))
                return entry;
        }
        return null;
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CEEPackets.SET_PANTOGRAPH;
    }
}
