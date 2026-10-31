package com.george_vi.electroenergetics.config;

import net.createmod.catnip.config.ConfigBase;
import org.jetbrains.annotations.NotNull;

public class CRotor extends ConfigBase {
    public final ConfigFloat rotorStressMultiplier = f(1, 0.0001f, "rotorStressMultiplier", "SUs of 1 Watt");
    public final ConfigFloat rotorPowerMultiplier = f(48, 0.0001f, "rotorPowerMultiplier", "Defines the output power of alternators through voltage");
    public final ConfigFloat rotorFullLoadCurrent = f(100, 0.0001f, "rotorFullLoadCurrent", "[in Amps]");
    public final ConfigFloat hertzPerRPM = f(6.4f, 0.0001f, "hertzPerRPM", "Defines how the speed relates to frequency");

    @Override
    public @NotNull String getName() {
        return "rotor";
    }
}
