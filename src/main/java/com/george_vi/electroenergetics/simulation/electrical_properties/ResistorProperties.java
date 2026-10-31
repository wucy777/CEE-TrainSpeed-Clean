package com.george_vi.electroenergetics.simulation.electrical_properties;

public final class ResistorProperties extends ElectricalProperties {
    public static final ElectricalProperties ZERO_CONDUCTANCE = new ResistorProperties(1e+11d);
    public static final ElectricalProperties MILlI = new ResistorProperties(0.001d);
    public static final ElectricalProperties TEN_MILlI = new ResistorProperties(0.01d);
    public static final ElectricalProperties HUNDRED_MILlI = new ResistorProperties(0.1d);

    private final double resistance;

    public ResistorProperties(double resistance) {
        this.resistance = resistance;
    }

    @Override
    public double resistance() {
        return resistance;
    }

    @Override
    public boolean isSimpleResistor() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResistorProperties that = (ResistorProperties) o;
        return Double.compare(resistance, that.resistance) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(resistance);
    }
}
