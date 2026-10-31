package com.george_vi.electroenergetics.content.railway_electrification;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket;
import com.george_vi.electroenergetics.content.railway_electrification.riders.RiderSeatGate;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.george_vi.electroenergetics.mixin_interfaces.IPantographList;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The per-tick driver state machine for electric trains: control mode, driver presence,
 * vigilance, emergency brake lifecycle, penalties, overload, parking and panel sync.
 *
 * The speed integration itself lives in {@code TrainMixin.applyGearLaw}, which owns
 * {@code train.speed} once per tick; everything here decides WHAT the law is told.
 *
 * Runs once per server tick per train (see GameEvents) - a wall-clock countdown must
 * fire exactly once per tick, not once per dimension.
 */
public class TrainDriverState {

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public static void tick(Train train) {
        ElectricTrainData data = ((ICEETrainExtension) train).getElectricTrainData();

        // Structural facts are written by the electrical solve; fall back to a cheap
        // structural test when the solve has not spoken yet (freshly loaded world).
        TrainControlModeLogic.updateStructuralFallback(train, data);

        TrainControlModeLogic.update(data, Math.abs(train.speed) * 20d);

        double speed = Math.abs(train.speed) * 20;

        // ---------------------- driver presence (B-14) ----------------------
        UUID atControls = controllingPlayer(train);
        if (atControls == null)
            // A driver seated in a conductor seat but not currently holding a key still
            // counts: Create only records a controlling player while keys arrive.
            atControls = seatedConductor(train);
        setDriverPresent(data, atControls);
        tickDriverPresence(data);
        if (atControls != null)
            data.driverCabInverted = isDriverCabInverted(train, atControls);

        // A running schedule hands control back to Create (B-15): paused == false means
        // the schedule is executing. A paused schedule leaves the lever in charge.
        if (train.runtime != null && !train.runtime.paused)
            releaseUnmanned(data);

        // ---------------------- overload / seat gate (E) ----------------------
        if (data.underGearControl() && data.electricControl()) {
            tickSeatGate(train, data);
        } else {
            // Handed to Create: the seat limit must not outlive the mode that owns it.
            data.overloaded = false;
            data.overloadBraking = false;
            data.riderBrakeNotified = false;
        }

        // ---------------------- vigilance (C-01..C-03, B-14) ----------------------
        tickVigilance(train, data);

        // ---------------------- power loss latch (B-10/M100) ----------------------
        boolean hasSupply = data.isPowered || data.hasCreativeSource;
        if (data.underGearControl() && data.electricControl()) {
            if (!hasSupply && !data.powerLossCoasted) {
                // Drop the lever to COAST once, and only from a position that commands
                // traction: overwriting the BRAKE would release a deliberately held train.
                if (data.gear.appliesTraction())
                    data.gear = TrainGear.COAST;
                data.powerLossCoasted = true;
            } else if (hasSupply) {
                data.powerLossCoasted = false;
            }
        }

        // ---------------------- emergency brake lifecycle (C-04..C-09) ----------------------
        tickEmergencyBrake(train, data);

        // ---------------------- panel lock tail (C-09) ----------------------
        if (data.controlsLocked && !data.emergencyArmed) {
            if (++data.lockoutTailTicks >= LOCKOUT_TAIL_TICKS)
                releaseLockout(data);
        } else if (!data.controlsLocked) {
            data.lockoutTailTicks = 0;
        }

        // ---------------------- penalty clearance (C-08) ----------------------
        tickPenalty(train, data);

        // ---------------------- out of track parking (B-11) ----------------------
        boolean outOfTrack = Math.abs(train.speed) < 1e-6d && isBlocked(train) && data.underGearControl();
        if (outOfTrack && !data.blockedParked) {
            failSafe(data);
            data.blockedParked = true;
        } else if (!outOfTrack && Math.abs(train.speed) > 1e-4d) {
            data.blockedParked = false;
        }
        data.outOfTrack = outOfTrack;

        // ---------------------- hold-space arrival transitions (B-13) ----------------------
        tickSpace(data);
        boolean arriving = data.spaceTicks > 0 && train.navigation != null && train.navigation.destination != null;
        if (arriving && !data.autoArrive) {
            data.autoArrive = true;
            data.autoArriveGear = data.gear;
        } else if (data.autoArrive && !arriving) {
            boolean arrived = train.getCurrentStation() != null;
            data.autoArrive = false;
            if (arrived) {
                failSafe(data);
            } else if (data.autoArriveGear != null && data.gearAvailable(data.autoArriveGear)) {
                data.gear = data.autoArriveGear;
            } else {
                data.gear = TrainGear.COAST;
            }
        }

        // ---------------------- riders ----------------------
        RiderSeatGate.tick(train, data);

        sendPanelData(train, data);
    }

    // ------------------------------------------------------------------
    // Driver presence
    // ------------------------------------------------------------------

    public static void setDriverPresent(ElectricTrainData data, UUID id) {
        data.driverId = id;
        if (id != null) {
            data.driverTicks = 5;
            data.wasDriven = true;
            data.unmanned = false;
            data.unmannedTicks = 0;
        }
    }

    public static void tickDriverPresence(ElectricTrainData data) {
        if (data.driverTicks > 0) {
            data.driverTicks--;
            if (data.driverTicks == 0) {
                data.driverId = null;
                // Only on the TRANSITION: re-latching every tick would make
                // releaseUnmanned a no-op for scheduled trains.
                if (data.wasDriven)
                    data.unmanned = true;
            }
        }
    }

    public static void releaseUnmanned(ElectricTrainData data) {
        data.unmanned = false;
        data.unmannedTicks = 0;
    }

    /** Hand the train to Create on the one-way transition: nothing of this mode survives. */
    public static void releaseToCreate(ElectricTrainData data) {
        data.createOwned = true;
        data.unmanned = false;
        data.wasDriven = false;
        data.confirmWaiting = 0;
        data.vigilanceTripped = false;
        data.tripTicks = 0;
        // emergencyArmed is deliberately NOT cleared: the standstill test owns it, and
        // clearing here would race the pantograph lowering on the same transition.
        data.autoArrive = false;
        data.spaceTicks = 0;
        data.powerLossCoasted = false;
        data.blockedParked = false;
    }

    /** Take the train back: the lever resumes in COAST (D-05). */
    public static void retakeFromCreate(ElectricTrainData data) {
        data.createOwned = false;
        data.gear = TrainGear.COAST;
        data.confirmWaiting = 0;
        data.vigilanceTripped = false;
        data.tripTicks = 0;
        data.emergencyArmed = false;
        data.wasDriven = false;
        data.unmanned = false;
    }

    /** Create's own record of who is holding the controls, any carriage. */
    public static UUID controllingPlayer(Train train) {
        for (Carriage carriage : train.carriages) {
            UUID[] found = new UUID[1];
            carriage.forEachPresentEntity(e -> e.getControllingPlayer().ifPresent(id -> {
                if (found[0] == null)
                    found[0] = id;
            }));
            if (found[0] != null)
                return found[0];
        }
        return null;
    }

    /** A player sitting in a conductor seat of this train, if any. */
    public static UUID seatedConductor(Train train) {
        for (Carriage carriage : train.carriages) {
            var dims = carriage.getPresentDimensions();
            if (dims.isEmpty())
                continue;
            var dce = carriage.getDimensionalIfPresent(dims.getFirst());
            if (dce == null || dce.entity == null || dce.entity.get() == null)
                continue;
            var entity = dce.entity.get();
            if (!(entity.getContraption() instanceof com.simibubi.create.content.trains.entity.CarriageContraption cc))
                continue;
            var mapping = entity.getContraption().getSeatMapping();
            for (var e : mapping.entrySet()) {
                var seatPos = entity.getContraption().getSeatOf(e.getKey());
                if (seatPos == null)
                    continue;
                if (cc.conductorSeats.containsKey(seatPos)) {
                    ServerPlayer player = playerByUuid(e.getKey());
                    if (player != null && player.isAlive() && !player.isSpectator())
                        return e.getKey();
                }
            }
        }
        return null;
    }

    private static ServerPlayer playerByUuid(UUID id) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    /** True when the driver sits in a cab facing against the assembly direction. */
    public static boolean isDriverCabInverted(Train train, UUID driverId) {
        for (int i = 0; i < train.carriages.size(); i++) {
            Carriage carriage = train.carriages.get(i);
            var dims = carriage.getPresentDimensions();
            if (dims.isEmpty())
                continue;
            var dce = carriage.getDimensionalIfPresent(dims.getFirst());
            if (dce == null || dce.entity == null || dce.entity.get() == null
                    || !(dce.entity.get().getContraption() instanceof com.simibubi.create.content.trains.entity.CarriageContraption cc))
                continue;
            var seatPos = dce.entity.get().getContraption().getSeatOf(driverId);
            if (seatPos == null)
                continue;
            var flags = cc.conductorSeats.get(seatPos);
            if (flags == null)
                continue;
            boolean forward = flags.getFirst();
            return i == 0 ? !forward : forward;
        }
        return false;
    }

    public static boolean isDriver(ElectricTrainData data, UUID id) {
        return id != null && id.equals(data.driverId);
    }

    // ------------------------------------------------------------------
    // Hold-space arrival
    // ------------------------------------------------------------------

    /** Record that the space key is held, refreshing the grace. Called from control(). */
    public static void setSpaceHeld(ElectricTrainData data) {
        data.spaceTicks = 5;
    }

    public static void tickSpace(ElectricTrainData data) {
        if (data.spaceTicks > 0)
            data.spaceTicks--;
    }

    // ------------------------------------------------------------------
    // Vigilance (C-01..C-03, B-14)
    // ------------------------------------------------------------------

    private static void tickVigilance(Train train, ElectricTrainData data) {
        int warn = CEEConfigs.server().trainValues.electricTrainVigilanceWarn.get();
        int red = CEEConfigs.server().trainValues.electricTrainVigilanceRed.get();
        int trip = CEEConfigs.server().trainValues.electricTrainVigilanceTrip.get();

        boolean moving = Math.abs(train.speed) > 1e-4d;
        // The clock runs while the train is this mode's to drive - driven or unmanned
        // alike (B-14: a driver who walks away is exactly as unresponsive as one who
        // fell asleep). It keeps running through an unpowered stretch: the operator's
        // rule is that braking AND the vigilance clock stay with the electric panel
        // during a neutral section (M100).
        boolean owned = data.underGearControl() && data.electricControl();

        // Releasing a trip is NOT gated on moving: once the train stands the trip has
        // succeeded and must let go, or the brake it applied would hold forever.
        if (data.vigilanceTripped && owned) {
            if (!moving || ++data.tripTicks > TRIP_BRAKE_TICKS) {
                clearTrip(data);
                data.tripTicks = 0;
            }
        } else if (owned && moving) {
            if (data.confirmWaiting < trip) {
                data.confirmWaiting++;
                data.warnAmber = data.confirmWaiting >= warn && data.confirmWaiting < red;
                data.warnRed = data.confirmWaiting >= red;
            } else {
                trip(data);
                data.tripTicks = 0;
            }
        } else if (owned) {
            // Owned but STOPPED: the clock resets every tick (C-03).
            data.confirmWaiting = 0;
            data.warnAmber = false;
            data.warnRed = false;
            data.tripTicks = 0;
        } else {
            // Not our train: drop the clock entirely.
            data.confirmWaiting = 0;
            data.warnAmber = false;
            data.warnRed = false;
            clearTrip(data);
            data.tripTicks = 0;
            data.unmannedTicks = 0;
        }
    }

    public static void confirm(ElectricTrainData data) {
        data.confirmWaiting = 0;
        data.warnAmber = false;
        data.warnRed = false;
    }

    /** The vigilance device tripping: brake to a stand, recoverable by acknowledging. */
    public static void trip(ElectricTrainData data) {
        failSafe(data);
        data.vigilanceTripped = true;
    }

    public static void clearTrip(ElectricTrainData data) {
        data.vigilanceTripped = false;
    }

    /** Drop the lever to the brake and cancel everything EXCEPT the emergency brake. */
    public static void failSafe(ElectricTrainData data) {
        data.gear = TrainGear.BRAKE;
    }

    public static int warningStage(ElectricTrainData data) {
        if (data.vigilanceTripped)
            return 2;
        int warn = CEEConfigs.server().trainValues.electricTrainVigilanceWarn.get();
        int red = CEEConfigs.server().trainValues.electricTrainVigilanceRed.get();
        if (data.confirmWaiting < warn)
            return 0;
        if (data.confirmWaiting < red)
            return 1;
        return 2;
    }

    // ------------------------------------------------------------------
    // Emergency brake lifecycle (C-04..C-09)
    // ------------------------------------------------------------------

    private static void tickEmergencyBrake(Train train, ElectricTrainData data) {
        if (data.emergencyArmed) {
            data.emergencyBrakeActive = true;
            boolean atStand = Math.abs(train.speed) < 1e-4d;
            if (atStand || ++data.emergencyTicks > EMERGENCY_BACKSTOP_TICKS) {
                // The stop is finished: the pantographs come down, once, on this edge.
                data.emergencyArmed = false;
                data.emergencyTicks = 0;
                lowerAllPantographs(train);
            }
            return;
        }
        data.emergencyBrakeActive = false;
    }

    private static void lowerAllPantographs(Train train) {
        for (Carriage carriage : train.carriages)
            for (var entry : ((IPantographList) carriage).electroEnergetics$getPantographList())
                entry.active = false;
    }

    public static void releaseLockout(ElectricTrainData data) {
        data.controlsLocked = false;
        data.emergencyArmed = false;
        data.lockoutTailTicks = 0;
    }

    // ------------------------------------------------------------------
    // Penalties (C-08)
    // ------------------------------------------------------------------

    private static void tickPenalty(Train train, ElectricTrainData data) {
        if (!data.emergencyPenalty)
            return;
        // Latch the EVENT of reaching a station, never sample the state: a wrench
        // relocation clears currentStation for good, which made a sampled test
        // unendable.
        if (train.getCurrentStation() != null)
            data.penaltyStationReached = true;
        if (data.penaltyStationReached) {
            clearPenalty(data);
        } else if (Math.abs(train.speed) > 1e-4d) {
            // Distance backstop, charged only while moving: a parked train has not
            // travelled a leg.
            data.penaltyDistance += Math.abs(train.speed);
            if (data.penaltyDistance >= CEEConfigs.server().trainValues.electricTrainPenaltyBackstopBlocks.get())
                clearPenalty(data);
        }
    }

    public static void clearPenalty(ElectricTrainData data) {
        data.emergencyPenalty = false;
        data.emergencyTicks = 0;
        data.penaltyStationReached = false;
        data.penaltyDistance = 0d;
        data.penaltyActive = false;
    }

    // ------------------------------------------------------------------
    // Seat gate driving (E)
    // ------------------------------------------------------------------

    private static void tickSeatGate(Train train, ElectricTrainData data) {
        RiderSeatGate.pruneGhostSeats(train);

        boolean atCeiling = RiderSeatGate.atOrAboveCeiling(train);

        // The release happens OUTSIDE the at-ceiling gate: braking is what brings the
        // speed down, so waiting for the ceiling to re-evaluate would keep the limit on
        // a train that is no longer over capacity. Cleared only on the actual condition
        // (nobody standing), never because the speed dropped (E-06).
        if (!atCeiling && data.overloaded && !RiderSeatGate.anyUnseated(train)) {
            data.overloaded = false;
            data.riderBrakeNotified = false;
            data.overloadBraking = false;
            if (data.gearBeforeOverloadDrop != null) {
                if (data.gear == TrainGear.CRUISE)
                    data.gear = data.gearBeforeOverloadDrop;
                data.gearBeforeOverloadDrop = null;
            }
        }

        if (!atCeiling && data.overloadBraking) {
            data.overloadBraking = false;
            if (data.gear == TrainGear.BRAKE) {
                data.cruiseTargetSpeed = Math.abs(train.speed) * 20d;
                data.gear = TrainGear.CRUISE;
            }
        }

        if (!atCeiling)
            return;

        boolean unseated = RiderSeatGate.anyUnseated(train);
        if (unseated) {
            RiderSeatGate.trySeatAll(train);
            unseated = RiderSeatGate.anyUnseated(train);
        }

        data.overloaded = unseated;
        if (unseated) {
            double speed = Math.abs(train.speed);
            double ceilingBpt = RiderSeatGate.CREATE_CEILING_BPS / 20d;
            if (speed > ceilingBpt + 1.0e-4d) {
                // Above the ceiling: brake, and REMEMBER the brake is ours (E-04).
                data.overloadBraking = true;
                data.gear = TrainGear.BRAKE;
            } else {
                if (data.overloadBraking) {
                    data.overloadBraking = false;
                    if (data.gear == TrainGear.BRAKE) {
                        data.cruiseTargetSpeed = Math.abs(train.speed) * 20d;
                        data.gear = TrainGear.CRUISE;
                    }
                }
                if (data.gear == TrainGear.ACCELERATE) {
                    data.gearBeforeOverloadDrop = TrainGear.ACCELERATE;
                    data.cruiseTargetSpeed = Math.abs(train.speed) * 20d;
                    data.gear = TrainGear.CRUISE;
                }
            }
            if (!data.riderBrakeNotified) {
                data.riderBrakeNotified = true;
                RiderSeatGate.notifyRiders(train, "riders.overloaded");
            }
        } else {
            data.riderBrakeNotified = false;
            data.overloadBraking = false;
            if (data.gearBeforeOverloadDrop != null) {
                if (data.gear == TrainGear.CRUISE)
                    data.gear = data.gearBeforeOverloadDrop;
                data.gearBeforeOverloadDrop = null;
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Whether any carriage is against a buffer stop or incompatible track (Create's own flags). */
    public static boolean isBlocked(Train train) {
        for (Carriage carriage : train.carriages) {
            if (carriage.blocked || carriage.isOnIncompatibleTrack())
                return true;
        }
        return false;
    }

    private static final int LOCKOUT_TAIL_TICKS = 2 * 20;
    private static final int EMERGENCY_BACKSTOP_TICKS = 90 * 20;
    private static final int TRIP_BRAKE_TICKS = 15 * 20;

    // ------------------------------------------------------------------
    // Panel sync
    // ------------------------------------------------------------------

    private static void sendPanelData(Train train, ElectricTrainData data) {
        data.ticksSinceGaugeSync++;
        if (data.ticksSinceGaugeSync < 5)
            return;
        data.ticksSinceGaugeSync = 0;

        Carriage first = train.carriages.isEmpty() ? null : train.carriages.get(0);
        if (first == null)
            return;
        for (var dim : train.getPresentDimensions()) {
            var d = first.getDimensionalIfPresent(dim);
            if (d == null || d.entity == null || d.entity.get() == null)
                continue;
            if (!(d.entity.get().level() instanceof ServerLevel level))
                continue;
            CatnipServices.NETWORK.sendToClientsAround(level, d.entity.get().position(), 128,
                    new SyncTrainPanelPacket(train.id,
                            data.controlMode.ordinal(),
                            data.gear.ordinal(),
                            (float) data.cruiseTargetSpeed,
                            data.gearAvailable(TrainGear.ACCELERATE),
                            warningStage(data),
                            (float) Mth.clamp(data.confirmWaiting / (double) Math.max(1, CEEConfigs.server().trainValues.electricTrainVigilanceTrip.get()), 0, 1),
                            data.emergencyArmed,
                            data.emergencyBrakeActive,
                            data.controlsLocked,
                            data.emergencyPenalty,
                            data.emergencyPenalty || data.penaltyActive,
                            data.overloaded,
                            data.powerLossCoasted,
                            data.blockedParked,
                            data.autoArrive,
                            data.driverCabInverted,
                            data.unmanned,
                            train.carriages.size(),
                            pantographFlags(train),
                            (float) (Math.abs(train.speed) * 20),
                            (float) capSpeed(data) * 20f,
                            (float) (data.realPowerW / 1000.0),
                            (float) data.powerScale,
                            data.motorBraking,
                            (float) (Math.asin(Mth.clamp(data.trackGrade, -1, 1)) * 180 / Math.PI),
                            countMotors(train),
                            data.isPowered,
                            ElectricManualSpeed.waivesManualFactor(),
                            (float) TrainTractionModel.designMaxSpeed(),
                            data.cruiseState.ordinal(),
                            data.regenPowerW > 0d));
        }
    }

    /** Speed cap (Blocks/Second) from penalties, enforced on Train.maxSpeed(). */
    public static double capSpeed(ElectricTrainData data) {
        double cap = TrainTractionModel.designMaxSpeed();
        if (data.emergencyPenalty)
            cap = Math.min(cap, CEEConfigs.server().trainValues.electricTrainPenaltySpeed.getF() / 3.6);
        return cap;
    }

    private static List<SyncTrainPanelPacket.PantographState> pantographFlags(Train train) {
        var list = new ArrayList<SyncTrainPanelPacket.PantographState>();
        for (int i = 0; i < train.carriages.size(); i++) {
            Carriage carriage = train.carriages.get(i);
            var entries = ((IPantographList) carriage).electroEnergetics$getPantographList();
            for (var e : entries)
                list.add(new SyncTrainPanelPacket.PantographState(i, e.active, e.autoRaise,
                        e.type == com.george_vi.electroenergetics.CEEPantographTypes.RAIL_CONTACT_SHOE.get()));
        }
        return list;
    }

    private static int countMotors(Train train) {
        int n = 0;
        for (Carriage carriage : train.carriages)
            if (((IPantographList) carriage).electroEnergetics$hasElectricMotor())
                n++;
        return n;
    }
}
