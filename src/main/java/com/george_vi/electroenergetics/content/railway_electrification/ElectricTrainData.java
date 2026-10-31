package com.george_vi.electroenergetics.content.railway_electrification;

import com.george_vi.electroenergetics.content.railway_electrification.pantograph.TrainPantographEntry;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ElectricTrainData {
    public List<TrainPantographEntry> pantographs = new ArrayList<>();
    public int accumulators = 0;
    public double accumulatorCharge = 0d;
    public boolean hasCreativeSource = false;
    public double lastVoltage = 0d;

    public double lastSpeed;
    public AttachedNode trainNode;
    public AttachedNode groundNode;
    public WireSimulationState connectedWireState;
    public WireSimulationState.WireCutHandle wireCutHandle;

    // Total current draw for display on ammeters attached to train contraptions
    public double displayCurrent = 0;

    // Packet throttling for gauge data sync
    public double lastSyncedVoltage = 0;
    public double lastSyncedCurrent = 0;
    public int ticksSinceGaugeSync = 0;

    // ------------------------------------------------------------------
    // Structural facts, written by the electrical solve each tick
    // ------------------------------------------------------------------

    /** The consist carries at least one traction motor AND one real pantograph. */
    public boolean hasTractionMotors = false;
    /** At least one real pantograph is currently raised (rail shoes excluded). */
    public boolean hasWorkingPantograph = false;
    /** The electrical solve found the train energised this tick. */
    public boolean isPowered = false;
    /** Modelled speed ceiling from the last solve [Blocks/Second]. */
    public float maxSpeed = 0f;
    /** Track grade along the consist, rise over run towards +x (leading anchor). */
    public double trackGrade = 0d;
    /** Supply-derived power scale of the last solve (0..1). */
    public double powerScale = 1d;
    /** Power being drawn from the catenary [W] (positive draw). */
    public double realPowerW = 0d;
    /** Power the motors feed back into the line right now [W]. */
    public double regenPowerW = 0d;
    /** The motors are retarding the train (dynamic brake or reverse hold). */
    public boolean braking = false;
    /** The motors are being driven in reverse against the motion (emergency brake). */
    public boolean motorBraking = false;
    /** Motor (regenerative) deceleration actually applied by the last gear-law step [m/s2]. */
    public double regenerativeBrakeAccel = 0d;

    // ------------------------------------------------------------------
    // Driver state
    // ------------------------------------------------------------------

    /** True once handed to Create: nothing of this mod's driving applies (D-05). */
    public boolean createOwned = false;

    /** Current lever position. */
    public TrainGear gear = TrainGear.COAST;
    /** Speed the CRUISE gear holds (m/s). */
    public double cruiseTargetSpeed = 0;

    /** Resolved control mode for this tick. */
    public TrainControlMode controlMode = TrainControlMode.CREATE;
    /** True when the panel is authoritative (ELECTRIC or DEAD_COAST). */
    public boolean electricControl() {
        return controlMode == TrainControlMode.ELECTRIC || controlMode == TrainControlMode.DEAD_COAST;
    }

    /** True while this mod owns the lever (a driver is present, or the train is latched unmanned). */
    public boolean underGearControl() {
        if (createOwned)
            return false;
        return isDriven() || unmanned;
    }

    /** Whether a player is currently at the controls (within the grace window). */
    public boolean isDriven() {
        return driverTicks > 0;
    }

    /** Traction positions may be selected only with a supply (C-10, E-05). */
    public boolean gearAvailable(TrainGear gear) {
        if (gear.appliesTraction())
            return isPowered || hasCreativeSource;
        return true;
    }

    /** Whether the emergency brake may be TRIGGERED (needs the supply; runs without it). */
    public boolean emergencyBrakeAvailable() {
        return isPowered || hasCreativeSource;
    }

    /** UUID of the player currently considered the driver, or null. */
    public UUID driverId = null;
    /** Driver is controlling from the opposite cab (train reverse relative to assembly). */
    public boolean driverCabInverted = false;

    // ------------------------------------------------------------------
    // Vigilance (deadman) device
    // ------------------------------------------------------------------

    public int confirmWaiting = 0;
    public boolean warnAmber = false;
    public boolean warnRed = false;
    public boolean vigilanceTripped = false;
    public int tripTicks = 0;

    // Driver presence
    public boolean unmanned = false;
    public int unmannedTicks = 0;
    public int driverTicks = 0;
    public boolean wasDriven = false;
    public int stalledTicks = 0;

    // Emergency brake
    public boolean emergencyArmed = false;
    public boolean emergencyBrakeActive = false;
    public int emergencyTicks = 0;
    public boolean emergencyPenalty = false;
    public boolean penaltyStationReached = false;
    public double penaltyDistance = 0d;
    public boolean controlsLocked = false;
    public int lockoutTailTicks = 0;

    // 40 km/h penalty until the next station
    public boolean penaltyActive = false;

    // Power-loss / blocked-parked one-shot latches (state reports only)
    public boolean powerLossCoasted = false;
    public boolean blockedParked = false;
    public boolean outOfTrack = false;

    // Overload seat gating
    public boolean overloaded = false;
    public boolean overloadBraking = false;
    public boolean riderBrakeNotified = false;
    public TrainGear gearBeforeOverloadDrop = null;

    // Create's hold-space auto arrival, adapted to the lever panel
    public boolean autoArrive = false;
    public TrainGear autoArriveGear = null;
    public int spaceTicks = 0;

    // Display helpers (transient)
    public double gradePercent = 0;
    public double smoothDisplaySpeed = 0;
    public TrainTractionModel.CruiseState cruiseState = TrainTractionModel.CruiseState.STOPPED;
}
