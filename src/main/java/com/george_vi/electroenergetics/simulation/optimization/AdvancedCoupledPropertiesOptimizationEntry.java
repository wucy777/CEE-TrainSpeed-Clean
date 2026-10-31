package com.george_vi.electroenergetics.simulation.optimization;

import com.george_vi.electroenergetics.simulation.electrical_properties.AdvancedCoupledDissolvedProperties;

public record AdvancedCoupledPropertiesOptimizationEntry(byte mode, int leftNode, int node,
                                                         int rightNode, int leftPrimary,
                                                         int rightPrimary, AdvancedCoupledDissolvedProperties properties,
                                                         double ratio) implements TopologyOptimizationEntry {
}
