package com.george_vi.electroenergetics.content.railway_electrification;

/**
 * The five lever positions of the electric control panel.
 */
public enum TrainGear {
    ACCELERATE,
    CRUISE,
    COAST,
    BRAKE,
    REVERSE;

    /** Lever deflection for the console animation, -0.5 (full back) to +0.5 (full forward). */
    public float leverValue() {
        return switch (this) {
            case ACCELERATE -> 0.5f;
            case CRUISE -> 0.25f;
            case COAST -> 0f;
            case BRAKE -> -0.25f;
            case REVERSE -> -0.5f;
        };
    }

    /** Whether the position commands traction (and therefore needs a supply). */
    public boolean appliesTraction() {
        return this == ACCELERATE || this == CRUISE || this == REVERSE;
    }

    public boolean isTraction() {
        return appliesTraction();
    }
}
