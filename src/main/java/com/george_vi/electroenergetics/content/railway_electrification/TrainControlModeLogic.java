package com.george_vi.electroenergetics.content.railway_electrification;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographType;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.TrainPantographEntry;
import com.george_vi.electroenergetics.mixin_interfaces.IPantographList;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;

/**
 * Decides who is in charge of a train this tick, and drives the one-way transitions
 * between Create control and the electric lever panel.
 *
 * Rules (D-01..D-07, P-02 answer A, M100):
 * - A train only counts as electric with at least one motor AND at least one real
 *   pantograph (rail shoes excluded; shoe-only trains keep upstream behaviour).
 * - Pantograph up and supply sufficient: ELECTRIC at any speed, including standstill.
 * - An emergency brake in force always keeps the panel: the stop must run to
 *   completion even if the train slows past the handover speed (C-05).
 * - Once handed to Create the train STAYS Create's until a pantograph is raised:
 *   the speed gate is a one-way door for the electric-to-Create direction only (M100 Q1).
 * - No working power and not handed over: the panel stays as DEAD_COAST while faster
 *   than the handover speed; below it the train is handed back to Create.
 */
public final class TrainControlModeLogic {

    private TrainControlModeLogic() {
    }

    /** Resolve the mode and drive the release/retake edges. */
    public static void update(ElectricTrainData data, double trainSpeedBps) {
        TrainControlMode mode = decide(data, trainSpeedBps);
        if (mode == TrainControlMode.CREATE) {
            if (!data.createOwned)
                TrainDriverState.releaseToCreate(data);
        } else {
            if (data.createOwned)
                TrainDriverState.retakeFromCreate(data);
        }
        data.controlMode = mode;
    }

    private static TrainControlMode decide(ElectricTrainData data, double trainSpeedBps) {
        if (!data.hasTractionMotors)
            return TrainControlMode.CREATE;
        if (data.hasWorkingPantograph && powered(data))
            return TrainControlMode.ELECTRIC;
        if (data.emergencyArmed)
            return TrainControlMode.DEAD_COAST;
        // Sticky CREATE: once handed over, only a raised pantograph comes back.
        if (data.controlMode == TrainControlMode.CREATE)
            return TrainControlMode.CREATE;
        return trainSpeedBps <= handoverSpeedBps() ? TrainControlMode.CREATE : TrainControlMode.DEAD_COAST;
    }

    private static boolean powered(ElectricTrainData data) {
        return data.isPowered || data.hasCreativeSource;
    }

    /** Handover speed in Blocks/Second (the config is Blocks/Second; decide works in BPS). */
    public static double handoverSpeedBps() {
        return Math.max(0.05d, CEEConfigs.server().trainValues.electricTrainHandoverSpeed.getF());
    }

    // ------------------------------------------------------------------
    // Structural tests
    // ------------------------------------------------------------------

    /** The consist carries a motor and at least one real pantograph (shoes excluded). */
    public static boolean isElectricTrain(Train train) {
        boolean motor = false, panto = false;
        for (Carriage carriage : train.carriages) {
            IPantographList list = (IPantographList) carriage;
            if (list.electroEnergetics$hasElectricMotor())
                motor = true;
            if (hasRealPantograph(train))
                panto = true;
        }
        return motor && panto;
    }

    public static boolean hasRealPantograph(Train train) {
        for (Carriage carriage : train.carriages)
            for (TrainPantographEntry e : ((IPantographList) carriage).electroEnergetics$getPantographList())
                if (!isShoe(e))
                    return true;
        return false;
    }

    /** A consist that collects only through rail contact shoes: upstream behaviour (M118). */
    public static boolean railShoeOnly(Train train) {
        boolean motor = false, shoe = false, panto = false;
        for (Carriage carriage : train.carriages) {
            IPantographList list = (IPantographList) carriage;
            if (list.electroEnergetics$hasElectricMotor())
                motor = true;
            for (TrainPantographEntry e : ((IPantographList) carriage).electroEnergetics$getPantographList()) {
                if (isShoe(e))
                    shoe = true;
                else
                    panto = true;
            }
        }
        return motor && shoe && !panto;
    }

    public static boolean isShoe(TrainPantographEntry e) {
        return e.type == shoeType();
    }

    public static boolean anyPantographRaised(Train train) {
        for (Carriage carriage : train.carriages)
            for (TrainPantographEntry e : ((IPantographList) carriage).electroEnergetics$getPantographList())
                if (e.active && !isShoe(e))
                    return true;
        return false;
    }

    /**
     * Cheap structural refresh for the first ticks of a freshly loaded train, before
     * the electrical solve has spoken. After that the solve is the authority.
     */
    public static void updateStructuralFallback(Train train, ElectricTrainData data) {
        if (data.maxSpeed > 0f || data.hasTractionMotors)
            return;
        boolean motor = false, panto = false, shoe = false, raised = false;
        for (Carriage carriage : train.carriages) {
            IPantographList list = (IPantographList) carriage;
            motor |= list.electroEnergetics$hasElectricMotor();
            for (TrainPantographEntry e : list.electroEnergetics$getPantographList()) {
                if (isShoe(e)) {
                    shoe = true;
                } else {
                    panto = true;
                    raised |= e.active;
                }
            }
        }
        // Shoe-only trains keep upstream behaviour, so they are not "ours" (M118).
        data.hasTractionMotors = motor && panto && !shoe;
        data.hasWorkingPantograph = raised;
        data.isPowered = data.hasCreativeSource
                || (raised && Math.abs(data.lastVoltage) >= CEEConfigs.server().voltageValues.trainMinVoltage.get());
        data.powerScale = TrainTractionModel.powerScaleForVoltage(Math.abs(data.lastVoltage));
    }

    private static PantographType shoeType() {
        return com.george_vi.electroenergetics.CEEPantographTypes.RAIL_CONTACT_SHOE.get();
    }
}
