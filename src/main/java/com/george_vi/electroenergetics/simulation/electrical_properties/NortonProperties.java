package com.george_vi.electroenergetics.simulation.electrical_properties;

public final class NortonProperties extends ElectricalProperties {
    private final double resistance;
    private final double currentSource;

    public NortonProperties(double resistance, double currentSource) {
        this.resistance = resistance;
        this.currentSource = currentSource;
    }

    @Override
    public double resistance() {
        return resistance;
    }

    @Override
    public double currentSource() {
        return currentSource;
    }

    @Override
    public boolean isCurrentSource() {
        return currentSource != 0;
    }

    @Override
    public ElectricalProperties invert() {
        return new NortonProperties(resistance, -currentSource);
    }
}
