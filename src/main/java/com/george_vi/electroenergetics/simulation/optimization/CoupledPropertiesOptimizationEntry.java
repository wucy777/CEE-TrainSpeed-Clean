package com.george_vi.electroenergetics.simulation.optimization;

public record CoupledPropertiesOptimizationEntry(byte mode, int leftNode, int node,
                                                 int rightNode, int leftPrimary,
                                                 int rightPrimary, double replacementResistance,
                                                 double leftResistance, double rightResistance, double ratio) implements TopologyOptimizationEntry {
    public static final byte MODE_NO_BRANCH = 0;
    public static final byte MODE_LEFT_BRANCH = 1;
    public static final byte MODE_CENTER_BRANCH = 2;
    public static final byte MODE_RIGHT_BRANCH = 3;
}
