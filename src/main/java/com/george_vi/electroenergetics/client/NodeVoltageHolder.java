package com.george_vi.electroenergetics.client;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class NodeVoltageHolder {

    public static Map<InWorldNode, VoltageEntry> NODE_VOLTAGES = new HashMap<>();

    public static Map<InWorldNode, VoltageEntry> getAllVoltages() {
        return NODE_VOLTAGES;
    }

    public static VoltageEntry getVoltageEntry(InWorldNode node) {
        VoltageEntry e = NODE_VOLTAGES.get(node);
        if (e == null)
            return NodeVoltageHolder.VoltageEntry.ZERO;
        if (e.invalid()) {
            NODE_VOLTAGES.remove(node);
            return NodeVoltageHolder.VoltageEntry.ZERO;
        }
        return e;
    }

    public static VoltageEntry getVoltageEntryOrNull(InWorldNode node) {
        VoltageEntry e = NODE_VOLTAGES.get(node);
        if (e == null)
            return null;
        if (e.invalid()) {
            NODE_VOLTAGES.remove(node);
            return null;
        }
        return e;
    }

    public static void addVoltageData(InWorldNode node, VoltageEntry e) {
        NODE_VOLTAGES.put(node, e);
    }

    public static double getVoltageBetween(InWorldNode node1, InWorldNode node2) {
        VoltageEntry e1 = getVoltageEntry(node1);
        VoltageEntry e2 = getVoltageEntry(node2);
        double max = 0, min = 0, sum = 0;
        if (e1.voltages.length != e2.voltages.length)
            return 0;
        for (int i = 0; i < e1.voltages.length; i++) {
            double v = (e1.voltages[i] - e2.voltages[i]);
            sum += v * v;

            if (i == 0) {
                max = min = v;
                continue;
            }

            if (v > max) max = v;
            if (v < min) min = v;

        }

        double rmsVoltage = Math.sqrt(sum / e1.voltages.length);
        // Flip RMS so that for mostly-DC negative voltages, the RMS is also negative.
        if (min < 0 && max < 0)
            rmsVoltage = -rmsVoltage;
        else if (min < 0 && max < min * -0.1)
            rmsVoltage = -rmsVoltage;

        return rmsVoltage;
    }

    public static class VoltageEntry {
        public static final VoltageEntry ZERO;
        public double rmsVoltage;
        public double rmsPeakClamped;
        public double[] voltages;
        // frequency >= 5 ? AC : DC
        public float frequency;
        public double sentTick;

        // For Frequency calculation:
        int ticks = 0;
        double maxVoltageThisPeriod = 0;
        double maxVoltageLastPeriod = 0;
        double prevPeriod = 0;
        double prevCross = 0;
        double prevV = 0;

        boolean isAC() {
            return frequency >= 5;
        }

        public void recompute() {
            sentTick = AnimationTickHolder.getTicks();

            double max = 0, min = 0, sum = 0;
            int maxIndex = 0, minIndex = 0;
            for (int i = 0; i < voltages.length; i++) {
                double v = voltages[i];
                sum += v * v;

                if (i == 0) {
                    max = min = v;
                    continue;
                }

                if (v > max) {
                    max = v;
                    maxIndex = i;
                }
                if (v < min) {
                    min = v;
                    minIndex = i;
                }
            }

            rmsVoltage = Math.sqrt(sum / voltages.length);

            sum = 0;
            int samples = 0;
            for (int i = Math.min(minIndex, maxIndex); i < Math.max(minIndex, maxIndex); i++) {
                double v = voltages[i];
                sum += v * v;
                samples++;
            }

            rmsPeakClamped = samples == 0 ? rmsVoltage : Math.sqrt(sum / samples);

            // Flip RMS so that for mostly-DC negative voltages, the RMS is also negative.
            if (min < 0 && max < 0) {
                rmsVoltage = -rmsVoltage;
                rmsPeakClamped = -rmsPeakClamped;
            } else if (min < 0 && max < min * -0.1) {
                rmsVoltage = -rmsVoltage;
                rmsPeakClamped = -rmsPeakClamped;
            }

            frequency = calculateFrequency(voltages);
        }


        private float calculateFrequency(double[] vs) {

            for (double v : vs) {
                ticks++;
                if (Math.abs(v) < 1e-6d)
                    v = 0;

                maxVoltageThisPeriod = Math.max(maxVoltageThisPeriod, Math.abs(v));

                if (v > 0 && prevV <= 0) {
                    double interpolated = ticks + (-prevV / (v - prevV));
                    prevPeriod = interpolated - prevCross;
                    prevCross = interpolated;
                    maxVoltageLastPeriod = maxVoltageThisPeriod;
                    maxVoltageThisPeriod = 0;
                }

                prevV = v;
            }

            if (maxVoltageLastPeriod < 8)
                return 0;

            double actualPeriod = Math.max(prevPeriod, ticks - prevCross);
            if (prevPeriod == 1 && prevCross == 1)
                return 0;

            double frequency = Math.abs(actualPeriod) < 1e-3d ? 0 : 1 / actualPeriod;
            frequency *= vs.length * 20;

            return (float) frequency;
        }

        public boolean invalid() {
            return AnimationTickHolder.getTicks() - sentTick > 4 || AnimationTickHolder.getTicks() < sentTick;
        }

        static {
            ZERO = new VoltageEntry();
            ZERO.voltages = new double[1];
        }
    }
}
