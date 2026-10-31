package com.george_vi.electroenergetics.foundation.device;

import com.george_vi.electroenergetics.devices.device.SimulatedDevice;

public interface SimpleTempHandler {
    double maxValue();

    default float updateTemp(double value, float temp) {
        SimulatedDevice device = (SimulatedDevice) this;
        float normalized = (float) (value / maxValue());
        float newTemp = ElectricalDevice.updateTemp(temp, normalized);
        ElectricalDevice.handleTemp(device.level, device.pos, device.deviceSD, normalized * 1000, 28_000, 30_000);
        if (newTemp > 30_000)
            onOverheat();
        return newTemp;
    }

    default void onOverheat() {

    }
}
