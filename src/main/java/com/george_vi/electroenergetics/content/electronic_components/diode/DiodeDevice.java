package com.george_vi.electroenergetics.content.electronic_components.diode;

import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.electrical_properties.DiodeProperties;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class DiodeDevice extends SimpleElectricalDevice {
    public double lastVoltage;
    public float temp;
    public DiodeProperties properties;

    public DiodeDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.builder(pos)
                .connect(1, 0, properties);
    }

    @Override
    public void read(CompoundTag tag) {
        lastVoltage = tag.getDouble("Voltage");
        temp = tag.getFloat("Temp");
        properties = new DiodeProperties(0.02585, 1e-12);
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("Voltage", lastVoltage);
        tag.putFloat("Temp", temp);
    }
}

