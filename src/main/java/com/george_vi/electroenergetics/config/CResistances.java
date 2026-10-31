package com.george_vi.electroenergetics.config;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CResistances extends ConfigBase {
    public final ConfigDouble motorResistance = d(30d, 0.1d, "motorResistance", "[in Ohms]");
    public final ConfigDouble pumpResistance = d(200d, 0.1d, "pumpResistance", "[in Ohms]");
    public final ConfigDouble bulbResistance = d(1000d, 0.1d, "bulbResistance", "[in Ohms]");
    public final ConfigDouble resistiveHeaterResistance = d(45d, 0.1d, "resistiveHeaterResistance", "[in Ohms]");
    public final ConfigDouble wireResistance = d(0.0002d, 0.00001d, "wireResistance", "Copper conductor resistance (95% copper alloy, rated 1000 A). [in Ohms / Meter]");
    public final ConfigDouble electrumWireResistance = d(0.005d, 0.0001d, "electrumWireResistance", "[in Ohms / Meter]");
    public final ConfigDouble ironWireResistance = d(0.00116d, 0.00001d, "ironWireResistance", "Iron conductor resistance, 5.78x copper resistivity. [in Ohms / Meter]");
    public final ConfigDouble ironBusWireResistance = d(0.00029d, 0.00001d, "ironBusWireResistance", "Thick iron busbar conductor resistance. [in Ohms / Meter]");
    public final ConfigDouble ironRailResistance = d(0.0000723d, 0.000001d, "ironRailResistance", "Iron rail (third rail / return path) resistance. [in Ohms / Meter]");
    public final ConfigDouble indicatorBulbResistance = d(1000, 0.0001d, "indicatorBulbResistance", "[in Ohms]");
    public final ConfigDouble electricFanResistance = d(200, 0.0001d, "electricFanResistance", "[in Ohms]");
    public final ConfigDouble converterMinResistance = d(20, 0.0001d, "converterMinResistance", "[in Ohms]");

    // --- Electro-Energetics TrainSpeed: realistic train resistance model (A) ---
    public final ConfigDouble electricTrainMassPerCarriage = d(50_000d, 1d, "electricTrainMassPerCarriage", "Mass per carriage. [in kg]");
    public final ConfigDouble electricTrainBasicResistanceA = d(1.0d, 0d, "electricTrainBasicResistanceA", "Davis basic resistance coefficient A (rolling).");
    public final ConfigDouble electricTrainBasicResistanceB = d(0.02d, 0d, "electricTrainBasicResistanceB", "Davis basic resistance coefficient B [per m/s].");
    public final ConfigDouble electricTrainBasicResistanceC = d(0.0014d, 0d, "electricTrainBasicResistanceC", "Davis basic resistance coefficient C, the aerodynamic term [per (m/s)^2].");
    public final ConfigDouble electricTrainRotatingMassFactor = d(0.06d, 0d, "electricTrainRotatingMassFactor", "Rotating mass factor for acceleration effort.");
    public final ConfigDouble electricTrainDriveEfficiency = d(0.9d, 0.01d, "electricTrainDriveEfficiency", "Drive efficiency between catenary and rail.");
    public final ConfigDouble electricTrainAuxiliaryLoadFactor = d(1.0d, 0.01d, "electricTrainAuxiliaryLoadFactor", "Auxiliary load factor K_aux for lights, HVAC and controls; divides the useful traction force.");
    public final ConfigDouble electricTrainMarginFactor = d(1.0d, 0.01d, "electricTrainMarginFactor", "Design margin factor K_margin applied on top of the demanded power.");

    @Override
    public String getName() {
        return "Resistances";
    }

    private ConfigDouble d(double current, double min, String name, String... comments) {
        return new ConfigDouble(name, current, min,  Double.MAX_VALUE, comments);
    }

    public class ConfigDouble extends CValue<Double, ModConfigSpec.DoubleValue> {

        public ConfigDouble(String name, double current, double min, double max, String... comment) {
            super(name, builder -> builder.defineInRange(name, current, min, max), comment);
        }
    }
}
