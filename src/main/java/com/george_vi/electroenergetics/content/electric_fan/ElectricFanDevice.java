package com.george_vi.electroenergetics.content.electric_fan;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.device.SimpleTempHandler;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class ElectricFanDevice extends SimpleElectricalDevice implements SimpleTempHandler {
    float actualSpeed;
    float targetSpeed;
    float temp;
    ElectricFanBlockEntity be;

    public ElectricFanDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        double resistance = CEEConfigs.server().resistanceValues.electricFanResistance.get();

        bridges.builder(pos)
                .resistor(0, 1, resistance);
    }

    double[] v1;
    double[] v2;
    @Override
    public void postTick(SimulationResults results) {
        v1 = results.getVoltages(new InWorldNode(0, pos), v1);
        v2 = results.getVoltages(new InWorldNode(1, pos), v2);
        double resistance = CEEConfigs.server().resistanceValues.electricFanResistance.get();
        double power = 0;
        double signedPower = 0;

        int safeLength = Math.min(v1.length, v2.length);
        for (int i = 0; i < safeLength; i++) {
            double vd = v1[i] - v2[i];
            power += vd * vd / resistance;
            signedPower += Math.signum(vd) * vd * vd / resistance;
        }
        power /= safeLength;
        signedPower /= safeLength;

        if (Math.abs(signedPower) < 0.1)
            signedPower = 0;
        targetSpeed = (float) (signedPower / 50);
        actualSpeed = ElectricFanBlockEntity.calculateSpeedChange(actualSpeed, targetSpeed);

        updateTemp(power, temp);

        if (this.be == null && level.isLoaded(pos))
            if (level.getBlockEntity(pos) instanceof ElectricFanBlockEntity b)
                this.be = b;

        if (this.be != null) {
            if (this.be.isRemoved())
                this.be = null;
            else {
                be.setSpeed(targetSpeed);
            }
        }

    }

    @Override
    public double maxValue() {
        return CEEConfigs.server().powerValues.electricFanMaxPower.get();
    }

    @Override
    public void read(CompoundTag tag) {
        actualSpeed = tag.getFloat("Speed");
        targetSpeed = tag.getFloat("TargetSpeed");
        temp = tag.getFloat("Temp");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Speed", actualSpeed);
        tag.putFloat("TargetSpeed", targetSpeed);
        tag.putFloat("Temp", temp);
    }
}
