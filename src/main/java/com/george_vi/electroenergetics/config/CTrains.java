package com.george_vi.electroenergetics.config;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CTrains extends ConfigBase {
    public final ConfigInt ticksPerAccumulatorOnTrain = i(1200, 1, "trainAccumulatorTime", "[in Ticks / one Accumulator]");
    public final ConfigInt ticksPerAccumulatorChargeOnTrain = i(1200, 1, "trainAccumulatorChargeTime", "[in Ticks / one Accumulator]");

    // --- Electro-Energetics TrainSpeed: realistic traction (A) & control (B) ---
    public final ConfigFloat electricTrainMaxSpeed = f(100f, 1f, "electricTrainMaxSpeed", "Maximum running speed of an electric train on straight track. [in Blocks / Second]");
    public final ConfigDouble electricTrainPowerPerCarriage = d(1_375_000d, 1d, "electricTrainPowerPerCarriage", "Rated traction power per carriage. [in Watts]");
    public final ConfigFloat electricTrainMaxAcceleration = f(2.0f, 0.1f, "electricTrainMaxAcceleration", "Starting (constant effort) acceleration of an electric train. [in Blocks / Second²]");
    public final ConfigBool electricTrainManualFullSpeed = b(true, "electricTrainManualFullSpeed", "Let a manually driven electric train reach the speed its own traction model allows, ignoring Create's 0.75 manual-driving handicap.");
    public final ConfigFloat electricTrainDynamicBrakeMinSpeed = f(5f, 0f, "electricTrainDynamicBrakeMinSpeed", "Speed at which the motor (regenerative) brake reaches full effect, fading linearly to nothing below it. [in Blocks / Second]");
    public final ConfigBool electricTrainRegenerativeBraking = b(true, "electricTrainRegenerativeBraking", "Motors act as generators while braking and push power back into the catenary.");
    public final ConfigFloat electricTrainRegenerativeFraction = f(0.7f, 0f, 1f, "electricTrainRegenerativeFraction", "Share of the motor brake's mechanical power the motors actually recover. [0-1]");
    public final ConfigBool electricTrainGradeResistance = b(true, "electricTrainGradeResistance", "Apply gradient resistance: uphill costs more, downhill assists.");
    public final ConfigFloat electricTrainBrakeDeceleration = f(0.8f, 0.1f, "electricTrainBrakeDeceleration", "Friction service brake deceleration. [in Blocks / Second²]");
    public final ConfigFloat electricTrainDynamicBrakeMax = f(0.5f, 0f, "electricTrainDynamicBrakeMax", "Maximum regenerative (dynamic) brake deceleration; the stronger of friction/dynamic applies, never both stacked. [in Blocks / Second²]");
    public final ConfigFloat electricTrainReverseMaxSpeed = f(10f, 0.1f, "electricTrainReverseMaxSpeed", "Maximum reverse gear speed. [in Blocks / Second]");
    public final ConfigFloat electricTrainHandoverSpeed = f(28f, 0.1f, "electricTrainHandoverSpeed", "Below this speed an unpowered electric train is handed back to Create control. [in Blocks / Second]");
    public final ConfigFloat electricTrainSeatGateSpeed = f(28f, 0.1f, "electricTrainSeatGateSpeed", "Above this speed standing riders are force-seated and dismounting is locked. [in Blocks / Second]");
    public final ConfigFloat electricTrainPenaltySpeed = f(40f, 0.1f, "electricTrainPenaltySpeed", "Speed limit after an emergency brake until the next station. [in km / h]");
    public final ConfigInt electricTrainPenaltyBackstopBlocks = i(10_000, 1, "electricTrainPenaltyBackstopBlocks", "Distance travelled after an emergency brake that also clears the penalty, as a backstop for missing stations. [in Blocks]");
    public final ConfigInt electricTrainVigilanceInterval = i(600, 20, "electricTrainVigilanceInterval", "Driver vigilance (deadman) confirmation interval. [in Ticks]");
    public final ConfigInt electricTrainVigilanceWarn = i(400, 20, "electricTrainVigilanceWarn", "Ticks after which the vigilance warning turns amber. [in Ticks]");
    public final ConfigInt electricTrainVigilanceRed = i(600, 20, "electricTrainVigilanceRed", "Ticks after which the vigilance warning turns red. [in Ticks]");
    public final ConfigInt electricTrainVigilanceTrip = i(720, 20, "electricTrainVigilanceTrip", "Ticks after which the vigilance device applies the service brake. [in Ticks]");
    public final ConfigFloat electricTrainMotorBrakeDeceleration = f(1.6f, 0.1f, "electricTrainMotorBrakeDeceleration", "Extra deceleration of full-power motor reverse braking (emergency brake). [in Blocks / Second²]");

    public final ConfigBool winterPantographSparks = b(true, "winterPantographSparks", "Sparks appear on electric trains in cold conditions.");
    public final ConfigFloat winterPantographSparkChance = f(0.01f, 0f, 1f, "winterPantographSparkChance", "Chance of a spark appearing in cold conditions.");
    public final ConfigFloat highSpeedPantographSparks = f(1.44f, 0f, "speedPantographSparkThreshold", "Trains faster than this value will have sparks on pantographs. Set to 0 to disable. [in Blocks / Tick]");
    public final ConfigFloat highSpeedPantographSparkChance = f(0.01f, 0f, 1f, "highSpeedPantographSparkChance", "Chance of a spark appearing at high speeds.");
    public final ConfigBool rainPantographSparks = b(true, "rainPantographSparks", "Sparks appear on electric trains in rainy conditions.");
    public final ConfigFloat rainPantographSparkChance = f(0.01f, 0f, 1f, "rainPantographSparkChance", "Chance of a spark appearing in raniy conditions.");

    @Override
    public String getName() {
        return "trains";
    }

    private ConfigDouble d(double current, double min, String name, String... comments) {
        return new ConfigDouble(name, current, min, Double.MAX_VALUE, comments);
    }

    public class ConfigDouble extends CValue<Double, ModConfigSpec.DoubleValue> {

        public ConfigDouble(String name, double current, double min, double max, String... comment) {
            super(name, builder -> builder.defineInRange(name, current, min, max), comment);
        }
    }
}
