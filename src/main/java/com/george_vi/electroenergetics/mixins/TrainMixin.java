package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.CEEElectricTrainSoundTypes;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.railway_electrification.ClientTrainPanel;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricManualSpeed;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricTrainData;
import com.george_vi.electroenergetics.content.railway_electrification.TrainControlMode;
import com.george_vi.electroenergetics.content.railway_electrification.TrainControlModeLogic;
import com.george_vi.electroenergetics.content.railway_electrification.TrainDriverState;
import com.george_vi.electroenergetics.content.railway_electrification.TrainGear;
import com.george_vi.electroenergetics.content.railway_electrification.TrainTractionModel;
import com.george_vi.electroenergetics.content.railway_electrification.sound_effects.TrainSoundModifier;
import com.george_vi.electroenergetics.content.railway_electrification.sound_effects.sound_types.ElectricTrainSoundType;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mixin(Train.class)
public class TrainMixin implements ICEETrainExtension {
    @Unique
    ElectricTrainSoundType electroenergetics$soundType;

    @Unique
    ElectricTrainData electroenergetics$electricTrainData = new ElectricTrainData();

    @Unique
    public Set<TrainSoundModifier> electroEnergetics$soundModifyingBlocks = new HashSet<>();

    @Override
    public ElectricTrainSoundType getSoundType() {
        return electroenergetics$soundType;
    }

    @Override
    public void setSoundType(ElectricTrainSoundType type) {
        electroenergetics$soundType = type;
    }

    @Override
    public Set<TrainSoundModifier> getSoundModifyingBlocks() {
        return electroEnergetics$soundModifyingBlocks;
    }

    @Override
    public ElectricTrainData getElectricTrainData() {
        return electroenergetics$electricTrainData;
    }

    // Wrap method so its compatible with Create: Power Loader
    @WrapMethod(method = "write")
    public CompoundTag electroEnergetics$write(DimensionPalette dimensions, HolderLookup.Provider registries, @NotNull Operation<CompoundTag> original) {
        CompoundTag tag = original.call(dimensions, registries);
        ResourceLocation id = CEERegistries.ELECTRIC_TRAIN_SOUND_TYPE.getKey(electroenergetics$soundType);
        if (id != null)
            tag.putString("CEETrainSoundType", id.toString());
        tag.putInt("CEEAccumulators", electroenergetics$electricTrainData.accumulators);
        tag.putDouble("CEEAccumulatorCharge", electroenergetics$electricTrainData.accumulatorCharge);
        tag.putBoolean("CEECreativeSource", electroenergetics$electricTrainData.hasCreativeSource);
        tag.putDouble("CEELastVoltage", electroenergetics$electricTrainData.lastVoltage);

        // Five-gear panel & penalties survive save/load (C-08).
        tag.putString("CEEGear", electroenergetics$electricTrainData.gear.name());
        tag.putDouble("CEECruiseTarget", electroenergetics$electricTrainData.cruiseTargetSpeed);
        tag.putBoolean("CEEPenalty", electroenergetics$electricTrainData.emergencyPenalty);
        tag.putInt("CEEEmergencyPenaltyDistance", (int) electroenergetics$electricTrainData.penaltyDistance);
        tag.putBoolean("CEEEmergencyArmed", electroenergetics$electricTrainData.emergencyArmed);
        return tag;
    }

    // Wrap method so its compatible with Create: Power Loader
    @WrapMethod(method = "read")
    private static Train electroEnergetics$read(CompoundTag tag, HolderLookup.Provider registries, Map<UUID, TrackGraph> trackNetworks, DimensionPalette dimensions, Operation<Train> original) {
        Train originalTrain = original.call(tag, registries, trackNetworks, dimensions);
        String id = tag.getString("CEETrainSoundType");
        ResourceLocation location = ResourceLocation.tryParse(id);
        ElectricTrainSoundType soundType = null;
        if (location != null)
            soundType = CEERegistries.ELECTRIC_TRAIN_SOUND_TYPE.get(location);
        if (soundType == null)
            soundType = CEEElectricTrainSoundTypes.MODERN.get();
        ICEETrainExtension train = (ICEETrainExtension) originalTrain;
        train.setSoundType(soundType);
        ElectricTrainData electricTrainData = train.getElectricTrainData();
        electricTrainData.accumulators = tag.getInt("CEEAccumulators");
        electricTrainData.accumulatorCharge = tag.getDouble("CEEAccumulatorCharge");
        electricTrainData.hasCreativeSource = tag.getBoolean("CEECreativeSource");
        electricTrainData.lastVoltage = tag.getDouble("CEELastVoltage");

        try {
            electricTrainData.gear = TrainGear.valueOf(tag.getString("CEEGear"));
        } catch (IllegalArgumentException ignored) {
            electricTrainData.gear = TrainGear.COAST;
        }
        electricTrainData.cruiseTargetSpeed = tag.getDouble("CEECruiseTarget");
        electricTrainData.emergencyPenalty = tag.getBoolean("CEEPenalty");
        electricTrainData.penaltyDistance = tag.getInt("CEEEmergencyPenaltyDistance");
        electricTrainData.emergencyArmed = tag.getBoolean("CEEEmergencyArmed");
        return originalTrain;
    }

    /**
     * The electric ceiling, in one place. Both control schemes ask maxSpeed(), so the
     * 40 km/h emergency cap survives the handover by living here (C-08), and the design
     * ceiling is what the panel-driven train is allowed.
     *
     * On the CLIENT the electrical simulation never runs, so the local control mode is
     * meaningless there - the synced panel sample answers instead, which is what keeps
     * the HUD speed bar on the right denominator (G-03).
     */
    @WrapMethod(method = "maxSpeed")
    public float electroEnergetics$maxSpeed(Operation<Float> original) {
        Train self = (Train) (Object) this;
        ElectricTrainData data = electroenergetics$electricTrainData;

        boolean electricHere = data.hasTractionMotors && data.electricControl();
        if (!electricHere) {
            // Client fallback: the synced panel knows what the server decided.
            var panel = ClientTrainPanel.get(self.id);
            if (panel != null && panel.mode() != TrainControlMode.CREATE.ordinal())
                return electroEnergetics$withPenalty(panel.capSpeed() / 20f, data);
            return electroEnergetics$withPenalty(original.call(), data);
        }

        // A train this mod does not currently drive (schedule running) ramps with
        // Create's own acceleration and must not receive the electric ceiling for free.
        boolean oursToDrive = data.isPowered || data.underGearControl();
        if (!oursToDrive)
            return electroEnergetics$withPenalty(original.call(), data);

        // The solve's own figure first; the configured design ceiling as the fallback
        // for the first ticks before the first circuit build.
        float ceiling = data.maxSpeed > 0f ? data.maxSpeed : (float) TrainTractionModel.designMaxSpeed();
        return electroEnergetics$withPenalty(ceiling / 20f, data);
    }

    /** No curve speed reduction for electric trains (A-06): a turn costs no ceiling. */
    @WrapMethod(method = "maxTurnSpeed")
    public float electroEnergetics$maxTurnSpeed(Operation<Float> original) {
        Train self = (Train) (Object) this;
        ElectricTrainData data = electroenergetics$electricTrainData;
        if (data.hasTractionMotors && data.electricControl())
            return electroEnergetics$maxSpeed(original);
        var panel = ClientTrainPanel.get(self.id);
        if (panel != null && panel.mode() != TrainControlMode.CREATE.ordinal())
            return electroEnergetics$maxSpeed(original);
        return original.call();
    }

    /**
     * Create uses acceleration() for ramping up AND for braking-distance maths, which
     * want different numbers. A power-limited train accelerates slowly at speed but
     * brakes roughly constantly, so this reports the blended service braking rate and
     * the traction taper lives in the ramping paths instead.
     */
    @WrapMethod(method = "acceleration")
    public float electroEnergetics$acceleration(Operation<Float> original) {
        ElectricTrainData data = electroenergetics$electricTrainData;
        if (data.hasTractionMotors && (data.isPowered || data.hasCreativeSource)) {
            return (float) ((TrainTractionModel.frictionBrake() + TrainTractionModel.dynamicBrake()) / 400d);
        }
        return original.call();
    }

    /**
     * The one place per tick that writes a gear-driven train's speed: Create's passive
     * slowdown hook, which runs exactly once inside Train.tick. Replacing it keeps a
     * single writer and stops Create braking a driverless electric train to a halt with
     * a friction stop that knows nothing about the catenary.
     */
    @WrapMethod(method = "tickPassiveSlowdown")
    private void electroEnergetics$tickPassiveSlowdown(Operation<Void> original) {
        Train self = (Train) (Object) this;
        ElectricTrainData data = electroenergetics$electricTrainData;
        if (data.hasTractionMotors && data.electricControl() && data.underGearControl()) {
            applyGearLaw(self, data);
            return;
        }
        original.call();
    }

    /**
     * Speed ramping for trains this mod does not gear-drive: schedules (and Create's
     * own controls handed back). A powered electric train ramps with the traction its
     * motors can actually deliver and brakes at the blended service rate with
     * regeneration; anything else falls through to Create unchanged (B-15).
     */
    @WrapMethod(method = "approachTargetSpeed")
    public void electroEnergetics$approachTargetSpeed(float accelerationMod, Operation<Void> original) {
        ElectricTrainData data = electroenergetics$electricTrainData;
        Train self = (Train) (Object) this;

        // A gear-driven train's speed is written by the gear law; both writers would
        // double the acceleration.
        if (data.hasTractionMotors && data.electricControl() && data.underGearControl())
            return;

        if (!data.hasTractionMotors || !data.electricControl() || !(data.isPowered || data.hasCreativeSource)) {
            original.call(accelerationMod);
            return;
        }

        data.braking = false;
        data.tractionEffortN = 0d;
        double actualTarget = self.targetSpeed;
        if (Mth.equal(actualTarget, self.speed))
            return;
        if (self.manualTick)
            self.leaveStation();

        float traction = data.maxSpeed > 0f
                ? (float) TrainTractionModel.availableAcceleration(Math.abs(self.speed) * 20d, 0d, self.carriages.size(), data.powerScale)
                : 0f;
        double up = Math.max(traction, 0f) / 400d;
        if (up <= 0d)
            up = CEEConfigs.server().trainValues.electricTrainMaxAcceleration.getF() / 400d;

        if (self.speed < actualTarget) {
            self.speed = Math.min(self.speed + up * accelerationMod, actualTarget);
            data.tractionEffortN = up * 400d
                    * TrainTractionModel.effectiveMass(self.carriages.size());
        } else {
            // Braking at the blended service rate for the current speed; the electric
            // share is reported so the next circuit build can regenerate it.
            double speedMs = Math.abs(self.speed) * 20d;
            double electric = TrainTractionModel.dynamicBrake() * TrainTractionModel.dynamicBrakeFade(speedMs);
            data.regenerativeBrakeAccel = electric;
            data.braking = true;
            double down = TrainTractionModel.serviceBrakeDeceleration(speedMs) / 400d;
            self.speed = Math.max(self.speed - down * accelerationMod, actualTarget);
        }
    }

    // ------------------------------------------------------------------
    // The gear law
    // ------------------------------------------------------------------

    /**
     * Integrate one tick of gear driving. The law works in the DRIVER's frame (so both
     * cabs of a double-ended train behave identically, B-12) and the result is mirrored
     * back into world coordinates.
     */
    private void applyGearLaw(Train self, ElectricTrainData data) {
        if (self.derailed)
            return;

        int carriages = Math.max(1, self.carriages.size());

        // A tripped vigilance device overrides the lever: braked to a stand, and
        // re-engaging a gear without acknowledging does nothing. The lever position is
        // left alone so the driver can see where they put the handle.
        TrainGear effectiveGear = data.vigilanceTripped ? TrainGear.BRAKE : data.gear;

        // Automatic arrival: Create's navigation commands the speed, not the lever
        // (B-13). Routed through CRUISE, which already is an approach controller.
        double cruiseCommand = data.cruiseTargetSpeed;
        if (data.autoArrive && !data.vigilanceTripped) {
            effectiveGear = TrainGear.CRUISE;
            cruiseCommand = Math.abs(self.targetSpeed) * 20d;
        }

        // Release the station before commanding traction. Create does this inside
        // approachTargetSpeed (manualTick), which this mode bypasses - without this a
        // train would sit at the platform with currentStation set forever.
        if (!data.autoArrive && effectiveGear.appliesTraction() && self.getCurrentStation() != null)
            self.leaveStation();

        // Which way the driving cab faces (B-12): the whole calculation is mirrored
        // about x, so every position means the same thing in the driver's own frame.
        boolean inverted = data.driverCabInverted;
        double mirror = inverted ? -1d : 1d;
        double signedSpeedMs = self.speed * 20d * mirror;

        // The grade sign comes from the UNMIRRORED speed: this conversion is about the
        // world, not the cab frame. Doing it after the mirror would reverse gravity at
        // an inverted cab whenever the train was at rest.
        double gradeToPlusX = (self.speed < 0 ? -data.trackGrade : data.trackGrade) * mirror;

        double[] lawOut = new double[2];
        double a = TrainTractionModel.gearAcceleration(
                signedSpeedMs, gradeToPlusX, carriages, data.powerScale,
                effectiveGear, cruiseCommand,
                TrainTractionModel.frictionBrake(),
                data.emergencyArmed, lawOut) * mirror;

        double next = self.speed + a / 400d;

        // ACCELERATE hands over to CRUISE at the design ceiling (B-03), checked against
        // the speed just written so the switch happens on the tick the ceiling is reached.
        if (data.gear == TrainGear.ACCELERATE && !data.vigilanceTripped && !data.autoArrive
                && !data.emergencyArmed
                && TrainTractionModel.atDesignCeiling(Math.abs(next) * 20d)) {
            data.gear = TrainGear.CRUISE;
            data.cruiseTargetSpeed = Math.abs(next) * 20d;
        }

        // Bring a stop to rest instead of through it: any deceleration that would cross
        // zero stops exactly at zero rather than reversing the train.
        if (Math.abs(self.speed) > 1e-9d && Math.signum(a) != Math.signum(self.speed)
                && Math.signum(next) != Math.signum(self.speed))
            next = 0d;

        self.speed = next;

        // Regeneration is reported for the next circuit build.
        data.braking = lawOut[0] > 0d;
        data.regenerativeBrakeAccel = lawOut[0];
        data.motorBraking = data.emergencyArmed && Math.abs(self.speed) * 20d > 0.05d;
        data.tractionEffortN = Math.max(0d, lawOut[1]);

        // Create's own method clears this, and control() re-sets it while the driver
        // holds keys. The gear law leaves a clean slate each tick.
        self.manualTick = false;
    }

    /**
     * Applies the post-emergency speed cap on top of whichever ceiling was resolved.
     * A cap on the TARGET, not a clamp on the speed - a train above it is braked down,
     * never teleported.
     */
    @Unique
    private float electroEnergetics$withPenalty(float ceiling, ElectricTrainData data) {
        if (data.emergencyPenalty)
            ceiling = (float) Math.min(ceiling,
                    CEEConfigs.server().trainValues.electricTrainPenaltySpeed.getF() / 3.6 / 20f);
        return ceiling;
    }
}
