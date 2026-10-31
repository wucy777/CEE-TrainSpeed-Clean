package com.george_vi.electroenergetics.simulation.electrical_properties;

import java.util.Arrays;

public class ParallelDissolvedProperties extends ElectricalProperties implements IDissolvedProperties {
    public final ElectricalProperties[] originalProperties;
    public final int node1;
    public final int node2;
    private final double resistance;

    public ParallelDissolvedProperties(ElectricalProperties[] originalProperties,
                                       int node1, int node2) {
        this.node1 = node1;
        this.node2 = node2;
        double conductance = Arrays.stream(originalProperties).mapToDouble(ElectricalProperties::conductance).sum();
        this.resistance = conductance == 0 ? 1e+11d : 1 / conductance;
        this.originalProperties = originalProperties;
    }

    @Override
    public void getVoltages(double v1, double v2, double[] toFill, int microTick, int totalMicroTicks) {

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
