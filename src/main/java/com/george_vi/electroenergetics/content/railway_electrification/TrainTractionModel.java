package com.george_vi.electroenergetics.content.railway_electrification;

import com.george_vi.electroenergetics.config.CEEConfigs;

/**
 * Realistic traction physics for electric trains (A-01..A-10, B-03..B-07, C-04..C-06).
 *
 * Conventions:
 * - Speeds in Blocks/Second (= m/s); Create's Train.speed is Blocks/Tick (m/s / 20).
 * - gearAcceleration returns a WORLD-frame acceleration towards the consist's +x
 *   (the leading anchor). A world-frame figure never flips sign at zero speed, so a
 *   held brake cannot start pushing the train backwards; the cab mirroring happens in
 *   the caller (TrainMixin.applyGearLaw).
 * - gradeToPlusX is rise-over-run towards +x; positive = uphill towards +x.
 *
 * Model:
 * - Constant tractive effort below the base speed, constant power above it.
 * - Davis running resistance (A + B·v + C·v²)·m·g·1e-3.
 * - Gravity on the REAL mass; inertia on the effective (1+γ)·m - the rotating parts
 *   resist being accelerated but do not weigh more on a slope.
 * - Service brake: friction, blended with the regenerative motor brake via max(),
 *   the motor share fading to zero at low speed (back-EMF collapse).
 * - Emergency brake = motors at full power in reverse + friction; it REPLACES the
 *   lever's own braking instead of stacking on it, and needs the supply.
 * - No speed cap lives here: caps are enforced on Train.maxSpeed(), which both
 *   control schemes consult.
 */
public class TrainTractionModel {

    public static final double G = 9.81;

    /** Below this speed the constant-power law is not evaluated (avoids division by zero). */
    private static final double MIN_SPEED_FOR_POWER = 1.0d;

    /** Smallest demand ever presented to the electrical simulation [W]. */
    public static final double MIN_DEMAND = 1.0d;

    /** Speed below which cruise has nothing meaningful to hold [m/s]. */
    public static final double CRUISE_MIN_SPEED = 0.5d;

    /** Speed band around the cruise setting where neither traction nor brake is applied [m/s]. */
    private static final double CRUISE_DEADBAND = 0.5d;

    /** How far below the design ceiling still counts as having reached it [m/s]. */
    private static final double CEILING_HANDOVER_BAND = 0.5d;

    private TrainTractionModel() {
    }

    // ------------------------------------------------------------------
    // Consist parameters
    // ------------------------------------------------------------------

    /** Rated electrical power of the whole consist [W]. */
    public static double ratedElectricalPower(int carriages) {
        return Math.max(1, carriages) * CEEConfigs.server().trainValues.electricTrainPowerPerCarriage.get();
    }

    /** Mechanical power available at the rail after drive losses [W]. */
    public static double ratedMechanicalPower(int carriages) {
        return ratedElectricalPower(carriages) * driveEfficiency();
    }

    public static double driveEfficiency() {
        return Math.max(CEEConfigs.server().resistanceValues.electricTrainDriveEfficiency.get(), 1e-4d);
    }

    public static double totalMass(int carriages) {
        return Math.max(1, carriages) * CEEConfigs.server().resistanceValues.electricTrainMassPerCarriage.get();
    }

    /** Effective mass including rotating masses (drive line, wheels) [kg]. */
    public static double effectiveMass(int carriages) {
        double gamma = CEEConfigs.server().resistanceValues.electricTrainRotatingMassFactor.get();
        return (1d + gamma) * totalMass(carriages);
    }

    /** Baseline auxiliary draw (lights, HVAC, controls), always present while powered [W]. */
    public static double auxiliaryPower(int carriages) {
        return Math.max(1, carriages) * 2_000d;
    }

    public static double designMaxSpeed() {
        return Math.max(CEEConfigs.server().trainValues.electricTrainMaxSpeed.getF(), 0.1d);
    }

    /** Whether ACCELERATE should hand over to CRUISE at this speed. */
    public static boolean atDesignCeiling(double speedMs) {
        return speedMs >= designMaxSpeed() - CEILING_HANDOVER_BAND;
    }

    /** Peak tractive effort in the constant-effort region, supply-scaled [N]. */
    public static double startingEffort(int carriages, double powerScale) {
        double gamma = CEEConfigs.server().resistanceValues.electricTrainRotatingMassFactor.get();
        double aMax = CEEConfigs.server().trainValues.electricTrainMaxAcceleration.getF();
        return (1d + gamma) * totalMass(carriages) * aMax * clampScale(powerScale);
    }

    public static double startingEffort(int carriages) {
        return startingEffort(carriages, 1d);
    }

    /** Supply voltage to power scale: 0 below the traction threshold, 1 at the catenary voltage. */
    public static double powerScaleForVoltage(double voltage) {
        int minV = CEEConfigs.server().voltageValues.trainMinVoltage.get();
        int maxV = CEEConfigs.server().voltageValues.trainMaxVoltage.get();
        if (maxV <= minV)
            return 1d;
        double scale = (Math.abs(voltage) - minV) / (maxV - minV);
        return clampScale(scale);
    }

    private static double clampScale(double scale) {
        if (Double.isNaN(scale) || scale <= 0d)
            return 0d;
        return Math.min(scale, 1d);
    }

    // ------------------------------------------------------------------
    // Forces
    // ------------------------------------------------------------------

    /** Davis running resistance [N]. F = (A + B·v + C·v²) · m · g · 1e-3 */
    public static double runningResistance(double speed, int carriages) {
        var r = CEEConfigs.server().resistanceValues;
        double v = Math.abs(speed);
        double coeff = r.electricTrainBasicResistanceA.get()
                + r.electricTrainBasicResistanceB.get() * v
                + r.electricTrainBasicResistanceC.get() * v * v;
        return coeff * totalMass(carriages) * G * 1e-3;
    }

    /** Gravity component along the track [N]. Positive grade = uphill towards +x. */
    public static double gradeResistance(double grade, int carriages) {
        if (!CEEConfigs.server().trainValues.electricTrainGradeResistance.get())
            return 0d;
        return totalMass(carriages) * G * grade;
    }

    /** Inertia force for the requested acceleration, including rotating mass [N]. */
    public static double inertiaForce(double acceleration, int carriages) {
        double gamma = CEEConfigs.server().resistanceValues.electricTrainRotatingMassFactor.get();
        return (1d + gamma) * totalMass(carriages) * acceleration;
    }

    // ------------------------------------------------------------------
    // Traction envelope
    // ------------------------------------------------------------------

    /** Tractive effort at the wheel, supply-scaled [N]. Constant effort below base speed, constant power above. */
    public static double availableEffort(double speed, int carriages, double powerScale) {
        double v = Math.abs(speed);
        double scale = clampScale(powerScale);
        double effortLimit = startingEffort(carriages, scale);
        double mechanical = ratedMechanicalPower(carriages) * scale;
        if (v < MIN_SPEED_FOR_POWER)
            return effortLimit;
        return Math.min(effortLimit, mechanical / v);
    }

    public static double availableEffort(double speed, int carriages) {
        return availableEffort(speed, carriages, 1d);
    }

    /** Base speed: where the constant-effort region ends [m/s]. */
    public static double baseSpeed(int carriages) {
        double effort = startingEffort(carriages);
        if (effort <= 0d)
            return designMaxSpeed();
        return ratedMechanicalPower(carriages) / effort;
    }

    /**
     * Highest speed the consist can sustain against running resistance and the current
     * gradient [m/s] (bisection). Used by readouts.
     */
    public static double maxSustainableSpeed(double grade, int carriages, double powerScale) {
        double ceiling = designMaxSpeed();
        double lo = 0d, hi = ceiling;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) * 0.5d;
            double need = runningResistance(mid, carriages) + gradeResistance(grade, carriages);
            double have = availableEffort(mid, carriages, powerScale);
            if (have >= need)
                lo = mid;
            else
                hi = mid;
        }
        return Math.max(0d, lo);
    }

    /** Acceleration the consist can currently deliver [m/s²], power-limited. */
    public static double availableAcceleration(double speed, double grade, int carriages, double powerScale) {
        double v = Math.abs(speed);
        double net = availableEffort(v, carriages, powerScale)
                - runningResistance(v, carriages)
                - gradeResistance(grade, carriages);
        if (net <= 0d)
            return 0d;
        double gamma = CEEConfigs.server().resistanceValues.electricTrainRotatingMassFactor.get();
        return net / ((1d + gamma) * totalMass(carriages));
    }

    // ------------------------------------------------------------------
    // Electrical demand
    // ------------------------------------------------------------------

    /**
     * Electrical power the consist asks from the catenary [W]. The demanded effort is
     * what the train NEEDS (resistance + gradient + requested acceleration), capped by
     * what the motors can deliver; motor reverse braking adds real current draw.
     */
    public static double electricalDemand(double speed, double acceleration, double grade,
                                          int carriages, double powerScale, boolean motorBraking) {
        double v = Math.abs(speed);
        double needed = runningResistance(v, carriages)
                + gradeResistance(grade, carriages)
                + inertiaForce(acceleration, carriages);

        double motorBrakeEffort = 0d;
        if (motorBraking) {
            double gamma = CEEConfigs.server().resistanceValues.electricTrainRotatingMassFactor.get();
            double effective = (1d + gamma) * totalMass(carriages);
            motorBrakeEffort = motorReverseBrake(v, carriages, powerScale) * effective;
        }

        var res = CEEConfigs.server().resistanceValues;
        double margin = Math.max(res.electricTrainMarginFactor.get(), 1e-4d);
        double auxFactor = Math.max(res.electricTrainAuxiliaryLoadFactor.get(), 1e-4d);

        double demandForce = Math.max(needed, 0d) + motorBrakeEffort;

        double tractionPower;
        if (demandForce <= 0d) {
            tractionPower = 0d;
        } else {
            double deliverable = availableEffort(v, carriages, powerScale);
            double effort = Math.min(demandForce, deliverable);
            tractionPower = effort * Math.max(v, MIN_SPEED_FOR_POWER) / (driveEfficiency() * auxFactor);
        }

        double result = tractionPower * margin + auxiliaryPower(carriages);
        if (Double.isNaN(result) || result < MIN_DEMAND)
            return MIN_DEMAND;
        return result;
    }

    /**
     * Electrical power drawn for the traction the drive was ACTUALLY commanded to apply
     * [W], plus motor reverse braking, plus the auxiliary baseline.
     *
     * The older "needed" formulation (resistance + grade + inertia) overstates the draw
     * whenever the lever commands less than the resistance: a COAST position at a
     * standstill drew phantom traction power for a force the motors never produced.
     * This overload takes the applied effort from the gear law, which is the only
     * honest figure.
     */
    public static double electricalDemandApplied(double appliedEffortN, double speedMs,
                                                 int carriages, double powerScale, boolean motorBraking) {
        double v = Math.abs(speedMs);
        var res = CEEConfigs.server().resistanceValues;
        double margin = Math.max(res.electricTrainMarginFactor.get(), 1e-4d);
        double auxFactor = Math.max(res.electricTrainAuxiliaryLoadFactor.get(), 1e-4d);

        double deliverable = availableEffort(v, carriages, powerScale);
        double effort = Math.max(0d, Math.min(appliedEffortN, deliverable));
        double tractionPower = effort * Math.max(v, MIN_SPEED_FOR_POWER) / (driveEfficiency() * auxFactor);

        double motorPower = 0d;
        if (motorBraking) {
            double gamma = CEEConfigs.server().resistanceValues.electricTrainRotatingMassFactor.get();
            double effective = (1d + gamma) * totalMass(carriages);
            motorPower = motorReverseBrake(v, carriages, powerScale) * effective * Math.max(v, MIN_SPEED_FOR_POWER);
        }

        double result = tractionPower * margin + motorPower + auxiliaryPower(carriages);
        if (Double.isNaN(result) || result < MIN_DEMAND)
            return MIN_DEMAND;
        return result;
    }

    // ------------------------------------------------------------------
    // Cruise state (for the display screen, B-04)
    // ------------------------------------------------------------------

    public enum CruiseState {
        HOLDING, POWER_LIMITED, BRAKING, STOPPED
    }

    public static CruiseState cruiseState(double speedMs, double grade, int carriages, double powerScale) {
        double v = Math.abs(speedMs);
        if (v < CRUISE_MIN_SPEED)
            return CruiseState.STOPPED;
        double needed = runningResistance(v, carriages) + gradeResistance(grade, carriages);
        if (needed <= 0d)
            return CruiseState.BRAKING;
        double deliverable = availableEffort(v, carriages, powerScale);
        return needed > deliverable ? CruiseState.POWER_LIMITED : CruiseState.HOLDING;
    }

    // ------------------------------------------------------------------
    // Braking: friction and electric, blended
    // ------------------------------------------------------------------

    /** Friction (adhesion) service braking rate [m/s²]. The only part that holds at rest. */
    public static double frictionBrake() {
        return Math.max(CEEConfigs.server().trainValues.electricTrainBrakeDeceleration.getF(), 0d);
    }

    /** Electric (regenerative) braking rate at full effect [m/s²]. */
    public static double dynamicBrake() {
        return Math.max(CEEConfigs.server().trainValues.electricTrainDynamicBrakeMax.getF(), 0d);
    }

    /** Speed at which the electric brake reaches full effect [m/s]. */
    public static double dynamicBrakeMinSpeed() {
        return Math.max(CEEConfigs.server().trainValues.electricTrainDynamicBrakeMinSpeed.getF(), 0d);
    }

    /** Share of the electric brake available at this speed, 0..1 (back-EMF fade). */
    public static double dynamicBrakeFade(double speedMs) {
        double v = Math.abs(speedMs);
        double min = dynamicBrakeMinSpeed();
        if (min <= 0d)
            return v > 0d ? 1d : 0d;
        return Math.min(v / min, 1d);
    }

    /** Total service braking rate at this speed [m/s²]: friction plus the faded electric part. */
    public static double serviceBrakeDeceleration(double speedMs) {
        return frictionBrake() + dynamicBrake() * dynamicBrakeFade(speedMs);
    }

    /** Braking rate still available at a standstill [m/s²]: friction only. */
    public static double holdingBrakeDeceleration() {
        return frictionBrake();
    }

    /**
     * Retarding acceleration the motors provide by being driven in reverse [m/s²].
     * Uses the tractive-effort curve, so a powered motor holds torque at zero speed -
     * the one brake that can hold a train on a grade beyond friction's reach.
     */
    public static double motorReverseBrake(double speedMs, int carriages, double powerScale) {
        double gamma = CEEConfigs.server().resistanceValues.electricTrainRotatingMassFactor.get();
        double effective = (1d + gamma) * totalMass(carriages);
        if (effective <= 0d)
            return 0d;
        double effort = availableEffort(speedMs, carriages, powerScale);
        double a = effort / effective;
        return Double.isFinite(a) && a > 0d ? a : 0d;
    }

    /** Steepest gradient the train can be held on, as rise over run. */
    public static double maxHoldableGrade(double brakeMs2) {
        return brakeMs2 / G;
    }

    /** Fastest the train may be driven backwards from rest [m/s]. */
    public static double reverseMaxSpeed() {
        return Math.max(CEEConfigs.server().trainValues.electricTrainReverseMaxSpeed.getF(), 0d);
    }

    // ------------------------------------------------------------------
    // Gear-driven motion
    // ------------------------------------------------------------------

    /**
     * Acceleration in world coordinates (+x = towards the leading anchor) for the current
     * lever position [m/s²]. The cab mirroring is the caller's job.
     *
     * @param signedSpeedMs current speed, positive towards +x
     * @param gradeToPlusX  rise over run towards +x
     * @param cruiseSpeedMs speed the cruise position holds, as a magnitude
     * @param brakeMs2      configured FRICTION service braking rate
     * @param emergency     whether the emergency brake is being applied
     * @param out           length-1 array receiving the applied MOTOR (regenerative)
     *                      deceleration share; pass null when unwanted.
     */
    public static double gearAcceleration(double signedSpeedMs, double gradeToPlusX, int carriages,
                                          double powerScale, TrainGear gear, double cruiseSpeedMs,
                                          double brakeMs2, boolean emergency, double[] out) {
        if (out != null && out.length > 1) {
            out[0] = 0d;
            out[1] = 0d;
        }
        double v = signedSpeedMs;
        double speed = Math.abs(v);
        double gamma = CEEConfigs.server().resistanceValues.electricTrainRotatingMassFactor.get();
        double mass = (1d + gamma) * totalMass(carriages);
        if (mass <= 0d)
            return 0d;

        // Drag opposes motion and vanishes at a standstill.
        double force = -Math.signum(v) * runningResistance(speed, carriages);
        // Gravity pulls towards -x on a climb towards +x, on the REAL mass.
        force -= gradeResistance(gradeToPlusX, carriages);

        double effort = availableEffort(speed, carriages, powerScale);
        double ceiling = designMaxSpeed();
        double heading = speed < 1e-6d ? 1d : Math.signum(v);
        // The direction the BRAKE should push at a standstill: against the fall, not
        // against the (nonexistent) motion. On the level this is zero, so a stopped
        // train has no force on it and cannot be walked off by a rounding error.
        double brakeHeading = speed < 1e-6d ? -Math.signum(gradeToPlusX) : Math.signum(v);

        double dynamicPart = dynamicBrake() * dynamicBrakeFade(speed) * powerScale;
        // Blended, not stacked: the commanded brake is the floor, and the electric
        // brake may take over part of the work but never silently add to it (B-06).
        double brakeTotal = Math.max(brakeMs2, dynamicPart);
        double dynamicApplied = 0d;
        double tractionApplied = 0d;
        double leverBrakeForce = 0d;

        // An emergency brake takes the traction off (brake pipe application trips the
        // contactors); otherwise a lever position could out-pull the stop on a descent.
        boolean tractionCut = emergency && speed > 1e-6d;

        switch (gear) {
            case ACCELERATE -> {
                if (!tractionCut && speed < ceiling) {
                    force += effort;
                    tractionApplied = effort;
                }
                // At or above the ceiling: no traction AND no brake. Braking there
                // produced the accelerate/brake bounce ("蹦迪"); the ceiling is enforced
                // by simply stopping to pull.
            }
            case REVERSE -> {
                // Two positions in one: from rest, a reverse GEAR; rolling forward or
                // stopped with a downhill tendency, a brake with the motors added (M043).
                double gravityTowardForward = -gradeResistance(gradeToPlusX, carriages);
                boolean forwardTendency = v > 1e-6d || (speed < 1e-6d && gravityTowardForward > 0d);
                if (forwardTendency) {
                    if (speed > 1e-6d) {
                        // Moving: the motors are back-driven, so they REGENERATE.
                        leverBrakeForce = -brakeHeading * brakeTotal * mass;
                        force += leverBrakeForce;
                        dynamicApplied = dynamicPart;
                    } else {
                        // Stopped with a downhill tendency: the motors are POWERED and
                        // hold torque, which draws line current and recovers nothing.
                        double motorHold = motorReverseBrake(0d, carriages, powerScale);
                        leverBrakeForce = -brakeHeading * (brakeMs2 + motorHold) * mass;
                        force += leverBrakeForce;
                    }
                } else if (!tractionCut && speed < Math.min(ceiling, reverseMaxSpeed())) {
                    force -= effort;
                    tractionApplied = effort;
                }
            }
            case CRUISE -> {
                double target = Math.min(Math.max(cruiseSpeedMs, 0d), ceiling);
                if (target < CRUISE_DEADBAND) {
                    // Commanded to stop (an automatic arrival finishing): brake to a
                    // genuine stand and hold against the gradient.
                    if (speed > 1e-6d) {
                        leverBrakeForce = -brakeHeading * brakeTotal * mass;
                        force += leverBrakeForce;
                        dynamicApplied = dynamicPart;
                    } else {
                        force -= brakeHeading * frictionBrake() * mass;
                    }
                } else if (speed < target - CRUISE_DEADBAND) {
                    if (!tractionCut && speed < ceiling) {
                        force += heading * effort;
                        tractionApplied = effort;
                    }
                } else if (speed > target + CRUISE_DEADBAND || tractionCut) {
                    leverBrakeForce = -brakeHeading * brakeTotal * mass;
                    force += leverBrakeForce;
                    dynamicApplied = dynamicPart;
                } else {
                    // Hold exactly: meet drag+gravity with traction, take the surplus
                    // off with the motors (regen) first, then friction (B-04).
                    double needed = Math.signum(v) * runningResistance(speed, carriages)
                            + gradeResistance(gradeToPlusX, carriages);
                    if (needed >= 0d) {
                        double applied = Math.min(needed, effort);
                        force += applied;
                        tractionApplied = applied;
                    } else {
                        double brakeNeed = -needed;
                        double dynamicAvail = Math.min(dynamicPart * mass, effort);
                        double fromMotors = Math.min(brakeNeed, dynamicAvail);
                        double fromFriction = Math.min(brakeNeed - fromMotors, brakeMs2 * mass);
                        leverBrakeForce = -(fromMotors + fromFriction);
                        force += leverBrakeForce;
                        dynamicApplied = fromMotors / mass;
                    }
                }
            }
            case COAST -> {
                // Nothing but drag and gravity. The electric brake is deliberately NOT
                // applied: cutting the power means cutting it (B-05).
            }
            case BRAKE -> {
                leverBrakeForce = -brakeHeading * brakeTotal * mass;
                force += leverBrakeForce;
                dynamicApplied = dynamicPart;
            }
        }

        // The emergency brake REPLACES the lever's braking instead of adding to it,
        // and it is the motors at full reverse plus friction - never the regenerative
        // brake, which would feed the line during an emergency (C-06).
        if (emergency && speed > 1e-6d) {
            force -= leverBrakeForce;
            double motorEmergency = motorReverseBrake(speed, carriages, powerScale);
            force -= Math.signum(v) * (frictionBrake() + motorEmergency) * mass;
            dynamicApplied = 0d;
            tractionApplied = 0d;
        }

        double a = force / mass;

        // At a standstill, a brake that can cover the slope PINS the train instead of
        // leaving a residual creep from the gravity term.
        if (speed < 1e-6d && a != 0d) {
            double pull = Math.abs(G * gradeToPlusX) / (1d + gamma);
            double hold = 0d;
            boolean braked = false;
            if (gear == TrainGear.BRAKE) {
                hold = frictionBrake();
                braked = true;
            } else if (gear == TrainGear.REVERSE) {
                boolean gravityPullsForward = -gradeResistance(gradeToPlusX, carriages) > 0d;
                if (gravityPullsForward) {
                    hold = frictionBrake() + motorReverseBrake(0d, carriages, powerScale);
                    braked = true;
                }
            }
            if (emergency) {
                hold = Math.max(hold, frictionBrake() + motorReverseBrake(0d, carriages, powerScale));
                braked = true;
            }
            if (braked && hold >= pull) {
                return 0d;
            }
        }

        a = Double.isFinite(a) ? a : 0d;
        if (out != null && out.length > 1) {
            out[0] = dynamicApplied;
            out[1] = Math.max(0d, tractionApplied);
        }
        return a;
    }

    /**
     * Electrical power the motors can push back into the line while braking [W].
     * Capped by the consist's electrical rating; only the MOTOR share counts.
     */
    public static double regenerativePower(double speedMs, double electricDecelerationMs2,
                                           int carriages, double regenFraction) {
        double v = Math.abs(speedMs);
        if (v <= 0d || electricDecelerationMs2 <= 0d || regenFraction <= 0d)
            return 0d;
        double retardingForce = inertiaForce(electricDecelerationMs2, carriages);
        double mechanical = retardingForce * v;
        double rated = ratedElectricalPower(carriages);
        double power = Math.min(mechanical * Math.min(regenFraction, 1d), rated);
        return Double.isFinite(power) && power > 0d ? power : 0d;
    }
}
