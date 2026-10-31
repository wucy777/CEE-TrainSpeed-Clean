package com.george_vi.electroenergetics.simulation.electrical_properties;

import com.george_vi.electroenergetics.simulation.SimulationNode;

import java.util.Collection;

public class DissolvedProperties extends ElectricalProperties implements IDissolvedProperties {
    private final int[] originalNodeIDs;
    private final double[] originalResistances;
    private double resistance = 0;


    public DissolvedProperties(Collection<SimulationNode> originalNodes, Collection<ElectricalProperties> originalResistances) {
        this.originalNodeIDs = new int[originalNodes.size()];
        int i = 0;
        for (SimulationNode node : originalNodes)
            this.originalNodeIDs[i++] = node.ordinal;

        this.originalResistances = new double[originalResistances.size()];
        i = 0;
        for (ElectricalProperties properties : originalResistances) {
            this.originalResistances[i++] = properties.resistance();
            resistance += properties.resistance();
        }

    }

    @Override
    public void getVoltages(double v1, double v2, double[] toFill, int microTick, int totalMicroTicks) {
        double totalResistance = resistance;
        double current = (v1 - v2) / totalResistance;
        double currentVoltage = v1;
        for (int i = 0; i < originalResistances.length; i++) {
            int nextNodeID = originalNodeIDs[i + 1];

            double resistance = originalResistances[i];
            double voltageDrop = current * resistance;

            currentVoltage = currentVoltage - voltageDrop;

            toFill[nextNodeID * totalMicroTicks + microTick] = currentVoltage;
        }
    }

    @Override
    public double resistance() {
        return resistance;
    }

    @Override
    public boolean isSimpleResistor() {
        return true;
    }
}
