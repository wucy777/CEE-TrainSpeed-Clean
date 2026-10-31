package com.george_vi.electroenergetics.content.railway_electrification;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket;
import com.simibubi.create.content.trains.entity.Train;

/**
 * Create scales manually driven trains by 0.75 (manualTrainSpeedModifier).
 * Electric trains driven by the panel waive that factor (A-09/G-03), so the
 * 18-segment bar always shows current speed against the full design speed.
 *
 * On the CLIENT the electrical simulation never runs, so the local train data
 * cannot answer "is this train electric-controlled": the synced panel sample
 * (mode + manualFullSpeed flag) is consulted instead.
 */
public final class ElectricManualSpeed {

    private ElectricManualSpeed() {
    }

    /** Server-side config read: does this installation waive the manual factor? */
    public static boolean waivesManualFactor() {
        return CEEConfigs.server().trainValues.electricTrainManualFullSpeed.get();
    }

    /** The factor to divide the speed bar by: 1.0 for panel-driven electric trains. */
    public static float speedBarFactor(Train train, float original) {
        if (train == null)
            return original;
        ElectricTrainData data = ((com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension) train).getElectricTrainData();
        // Server side (or integrated server): the authoritative local state.
        if (TrainControlModeLogic.isElectricTrain(train) && data.electricControl())
            return waivesManualFactor() ? 1f : original;
        // Client side: the synced panel is the only source of the control mode.
        SyncTrainPanelPacket panel = com.george_vi.electroenergetics.content.railway_electrification.ClientTrainPanel.get(train.id);
        if (panel != null && panel.mode() != TrainControlMode.CREATE.ordinal())
            return panel.manualFullSpeed() ? 1f : original;
        return original;
    }
}
