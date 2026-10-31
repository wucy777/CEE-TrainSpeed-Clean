package com.george_vi.electroenergetics.content.transmission_distribution.transformer;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.device.SimpleTempHandler;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class TransformerDevice extends SimpleElectricalDevice implements SimpleTempHandler {
    public TransformerDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    public TransformerBehaviour.TransformerBehaviourDataHolder transformerData;
    public float temp;
    public double ratio;
    public TransformerBlockEntity be;

    @Override
    public void preTick(BridgeCollector bridges) {
        double ratio = this.ratio;
        if (ratio == 0)
            ratio = 1;

        TransformerBehaviour.preTick(TransformerBehaviour.setupStandardNodes(pos), ratio, pos, bridges, this.transformerData);
    }

    @Override
    public void postTick(SimulationResults results) {
        double power = TransformerBehaviour.postTick(TransformerBehaviour.setupStandardNodes(pos), results, this.transformerData);

        if (this.be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TransformerBlockEntity b)
            this.be = b;

        if (this.be != null) {
            if (this.be.isRemoved())
                this.be = null;
            else {
                this.be.power = Math.abs(power);
                this.be.primaryVoltage = this.transformerData.lastPrimaryVoltage;
                this.be.secondaryVoltage = this.transformerData.lastSecondaryVoltage;
            }
        }

        temp = updateTemp(Math.abs(power), temp);
    }

    @Override
    public void read(CompoundTag tag) {
        this.temp = tag.getFloat("Temp1");
        this.ratio = tag.getDouble("Ratio");
        this.transformerData = new TransformerBehaviour.TransformerBehaviourDataHolder(tag.getCompound("TransformerData"));
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Temp1", this.temp);
        tag.putDouble("Ratio", this.ratio);
        tag.put("TransformerData", this.transformerData.write());
    }

    @Override
    public double maxValue() {
        // Transformers are unlimited by design; the temp model simply never loads.
        return Double.MAX_VALUE;
    }
}


