package com.george_vi.electroenergetics.content.railway_electrification.packets;

import com.george_vi.electroenergetics.CEEPackets;
import com.george_vi.electroenergetics.content.railway_electrification.ClientTrainPanel;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything the lever panel and the client-side Train mixins need, throttled to every
 * few ticks (G-07/G-11). The client has no electrical simulation, so control mode,
 * ceiling, manual-factor waiver and powered state all travel here.
 */
public record SyncTrainPanelPacket(
        UUID trainId,
        int mode,
        int gear,
        float cruiseTarget,
        boolean gearAvailable,
        int confirmStage,
        float confirmProgress,
        boolean ebArmed,
        boolean ebActive,
        boolean controlsLocked,
        boolean emergencyPenalty,
        boolean penaltyActive,
        boolean overloaded,
        boolean powerLossCoasted,
        boolean blockedParked,
        boolean autoArrive,
        boolean cabInverted,
        boolean unmanned,
        int carriageCount,
        List<PantographState> pantographs,
        float speed,
        float capSpeed,
        float powerKW,
        float powerScale,
        boolean motorBraking,
        float grade,
        int motorCarriages,
        boolean powered,
        boolean manualFullSpeed,
        float designMaxSpeed,
        int cruiseState,
        boolean regenerating) implements ClientboundPacketPayload {

    public record PantographState(int carriageIndex, boolean active, boolean autoRaise, boolean railShoe) {
    }

    public static final StreamCodec<ByteBuf, SyncTrainPanelPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncTrainPanelPacket decode(ByteBuf buf) {
            UUID id = ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC).decode(buf);
            int mode = ByteBufCodecs.VAR_INT.decode(buf);
            int gear = ByteBufCodecs.VAR_INT.decode(buf);
            float cruise = buf.readFloat();
            boolean gearAvailable = buf.readBoolean();
            int stage = ByteBufCodecs.VAR_INT.decode(buf);
            float progress = buf.readFloat();
            boolean ebArmed = buf.readBoolean();
            boolean ebActive = buf.readBoolean();
            boolean locked = buf.readBoolean();
            boolean ebPenalty = buf.readBoolean();
            boolean penalty = buf.readBoolean();
            boolean overloaded = buf.readBoolean();
            boolean powerLoss = buf.readBoolean();
            boolean blocked = buf.readBoolean();
            boolean autoArrive = buf.readBoolean();
            boolean inverted = buf.readBoolean();
            boolean unmanned = buf.readBoolean();
            int carriages = ByteBufCodecs.VAR_INT.decode(buf);
            int pantoCount = ByteBufCodecs.VAR_INT.decode(buf);
            List<PantographState> pantos = new ArrayList<>(pantoCount);
            for (int i = 0; i < pantoCount; i++) {
                int ci = ByteBufCodecs.VAR_INT.decode(buf);
                boolean active = buf.readBoolean();
                boolean auto = buf.readBoolean();
                boolean shoe = buf.readBoolean();
                pantos.add(new PantographState(ci, active, auto, shoe));
            }
            float speed = buf.readFloat();
            float cap = buf.readFloat();
            float power = buf.readFloat();
            float scale = buf.readFloat();
            boolean braking = buf.readBoolean();
            float grade = buf.readFloat();
            int motors = ByteBufCodecs.VAR_INT.decode(buf);
            boolean powered = buf.readBoolean();
            boolean manualFull = buf.readBoolean();
            float designMax = buf.readFloat();
            int cruiseState = ByteBufCodecs.VAR_INT.decode(buf);
            boolean regen = buf.readBoolean();
            return new SyncTrainPanelPacket(id, mode, gear, cruise, gearAvailable, stage, progress, ebArmed, ebActive,
                    locked, ebPenalty, penalty, overloaded, powerLoss, blocked, autoArrive, inverted, unmanned,
                    carriages, pantos, speed, cap, power, scale, braking, grade, motors,
                    powered, manualFull, designMax, cruiseState, regen);
        }

        @Override
        public void encode(ByteBuf buf, SyncTrainPanelPacket p) {
            ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC).encode(buf, p.trainId());
            ByteBufCodecs.VAR_INT.encode(buf, p.mode());
            ByteBufCodecs.VAR_INT.encode(buf, p.gear());
            buf.writeFloat(p.cruiseTarget());
            buf.writeBoolean(p.gearAvailable());
            ByteBufCodecs.VAR_INT.encode(buf, p.confirmStage());
            buf.writeFloat(p.confirmProgress());
            buf.writeBoolean(p.ebArmed());
            buf.writeBoolean(p.ebActive());
            buf.writeBoolean(p.controlsLocked());
            buf.writeBoolean(p.emergencyPenalty());
            buf.writeBoolean(p.penaltyActive());
            buf.writeBoolean(p.overloaded());
            buf.writeBoolean(p.powerLossCoasted());
            buf.writeBoolean(p.blockedParked());
            buf.writeBoolean(p.autoArrive());
            buf.writeBoolean(p.cabInverted());
            buf.writeBoolean(p.unmanned());
            ByteBufCodecs.VAR_INT.encode(buf, p.carriageCount());
            ByteBufCodecs.VAR_INT.encode(buf, p.pantographs().size());
            for (PantographState ps : p.pantographs()) {
                ByteBufCodecs.VAR_INT.encode(buf, ps.carriageIndex());
                buf.writeBoolean(ps.active());
                buf.writeBoolean(ps.autoRaise());
                buf.writeBoolean(ps.railShoe());
            }
            buf.writeFloat(p.speed());
            buf.writeFloat(p.capSpeed());
            buf.writeFloat(p.powerKW());
            buf.writeFloat(p.powerScale());
            buf.writeBoolean(p.motorBraking());
            buf.writeFloat(p.grade());
            ByteBufCodecs.VAR_INT.encode(buf, p.motorCarriages());
            buf.writeBoolean(p.powered());
            buf.writeBoolean(p.manualFullSpeed());
            buf.writeFloat(p.designMaxSpeed());
            ByteBufCodecs.VAR_INT.encode(buf, p.cruiseState());
            buf.writeBoolean(p.regenerating());
        }
    };

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handle(LocalPlayer player) {
        ClientTrainPanel.update(this);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CEEPackets.SYNC_TRAIN_PANEL;
    }
}
