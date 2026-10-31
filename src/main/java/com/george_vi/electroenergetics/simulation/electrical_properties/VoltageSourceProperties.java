package com.george_vi.electroenergetics.simulation.electrical_properties;

public class VoltageSourceProperties extends ElectricalProperties {
    private final double voltageSource;

    public VoltageSourceProperties(double voltageSource) {
        this.voltageSource = voltageSource;
    }

    @Override
    public double resistance() {
        return 1e+11d;
    }

    @Override
    public double voltageSource() {
        return voltageSource;
    }

    @Override
    public boolean isVoltageSource() {
        return true;
    }

    @Override
    public ElectricalProperties invert() {
        return new VoltageSourceProperties(-voltageSource);
    }
}
