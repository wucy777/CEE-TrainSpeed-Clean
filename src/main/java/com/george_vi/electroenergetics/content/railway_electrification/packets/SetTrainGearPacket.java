package com.george_vi.electroenergetics.content.railway_electrification.packets;

import com.george_vi.electroenergetics.CEEPackets;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricTrainData;
import com.george_vi.electroenergetics.content.railway_electrification.TrainDriverState;
import com.george_vi.electroenergetics.content.railway_electrification.TrainGear;
import com.george_vi.electroenergetics.content.railway_electrification.TrainTractionModel;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Driver lever input: engage a gear, acknowledge vigilance, or pull the emergency brake.
 *
 * Only the person the server records at the controls may command the train; traction
 * positions are refused without a supply; reverse thrown while rolling IS the emergency
 * brake (K-06), with the whole penalty chain applied at pull time (C-08).
 */
public record SetTrainGearPacket(UUID trainId, int gear, int actionOrdinal) implements ServerboundPacketPayload {

    public enum Action {
        ENGAGE, CONFIRM_VIGILANCE, EMERGENCY_BRAKE
    }

    public SetTrainGearPacket(UUID trainId, int gear, Action action) {
        this(trainId, gear, action.ordinal());
    }

    private Action action() {
        return Action.values()[Math.floorMod(actionOrdinal, Action.values().length)];
    }

    public static final StreamCodec<ByteBuf, SetTrainGearPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC), SetTrainGearPacket::trainId,
            ByteBufCodecs.VAR_INT, SetTrainGearPacket::gear,
            ByteBufCodecs.VAR_INT, SetTrainGearPacket::actionOrdinal,
            SetTrainGearPacket::new
    );

    @Override
    public void handle(ServerPlayer player) {
        Train train = Create.RAILWAYS.trains.get(trainId);
        if (train == null || train.carriages.isEmpty())
            return;
        ElectricTrainData data = ((ICEETrainExtension) train).getElectricTrainData();

        // Only the person actually at the controls may command the train, checked
        // against Create's own server-side record - not against anything the client
        // claims.
        if (!TrainDriverState.isDriver(data, player.getUUID()))
            return;

        if (action() == Action.EMERGENCY_BRAKE) {
            pullEmergencyBrake(train, data);
            return;
        }

        // LOCKOUT: while the emergency application runs - and for the 2 s tail after it -
        // nothing else is accepted. Both flags, because they no longer expire together.
        if (data.emergencyArmed || data.controlsLocked)
            return;

        if (action() == Action.CONFIRM_VIGILANCE) {
            TrainDriverState.confirm(data);
            // Acknowledging also releases a trip once the train has stopped; while
            // moving it only resets the clock (the trip holds until a stand).
            if (data.vigilanceTripped && Math.abs(train.speed) < 1e-3d) {
                TrainDriverState.clearTrip(data);
                data.tripTicks = 0;
            }
            return;
        }

        // ENGAGE
        if (gear < 0 || gear >= TrainGear.values().length)
            return;
        TrainGear requested = TrainGear.values()[gear];

        // Traction positions need a supply (C-10); BRAKE and COAST always work. Checked
        // here even though the client checks too: this side is the authority.
        if (!data.gearAvailable(requested))
            return;

        // Reverse while already moving arms the emergency brake instead of commanding
        // reverse traction: a real handle cannot be thrown against the direction of
        // travel, and "stop hard" is what the driver means (K-06/C-04).
        if (requested == TrainGear.REVERSE && Math.abs(train.speed) > TrainTractionModel.CRUISE_MIN_SPEED) {
            data.gear = TrainGear.REVERSE;
            armEmergency(train, data);
            return;
        }

        data.gear = requested;
        if (requested == TrainGear.CRUISE)
            data.cruiseTargetSpeed = Math.abs(train.speed) * 20d;
    }

    private void pullEmergencyBrake(Train train, ElectricTrainData data) {
        // The emergency brake is PULLED, not requested: it latches and runs until the
        // train is at a stand. Triggering needs power (it is a motor brake); continuing
        // does not - a mid-stop supply failure finishes on friction.
        if (data.emergencyBrakeAvailable() && data.gear == TrainGear.REVERSE) {
            armEmergency(train, data);
        }
    }

    private void armEmergency(Train train, ElectricTrainData data) {
        data.emergencyArmed = true;
        data.emergencyTicks = 0;
        // The penalty is applied at pull time, and a fresh application earns a fresh leg
        // (the station-reached latch restarts, or the cap would clear on the next
        // platform touch).
        data.emergencyPenalty = true;
        data.penaltyActive = true;
        data.penaltyStationReached = false;
        data.penaltyDistance = 0d;
        data.controlsLocked = true;
        data.lockoutTailTicks = 0;
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CEEPackets.SET_TRAIN_GEAR;
    }
}
