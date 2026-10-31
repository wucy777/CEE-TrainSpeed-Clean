package com.george_vi.electroenergetics.content.railway_electrification;

/**
 * Who is currently driving the train.
 */
public enum TrainControlMode {
    /** Create's own mechanics (W/S, throttle, schedules) - no pantograph working, or handed over below the speed gate. */
    CREATE,
    /** The electric control panel is in charge and the train is powered. */
    ELECTRIC,
    /** The electric panel is still in charge but there is no supply: only COAST/BRAKE, vigilance and EB keep working. */
    DEAD_COAST
}
