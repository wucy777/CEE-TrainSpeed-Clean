package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.content.railway_electrification.ElectricTrainData;
import com.george_vi.electroenergetics.content.railway_electrification.TrainControlMode;
import com.george_vi.electroenergetics.content.railway_electrification.TrainDriverState;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;

/**
 * W/S and the scroll wheel no longer drive an electric panel train (B-01); A/D steering
 * keeps working (B-09) and the space key reports itself to the driver state so the
 * automatic-arrival transitions can run in the tick (B-13).
 */
@Mixin(CarriageContraptionEntity.class)
public class CarriageContraptionEntityMixin {

    // Key indexes used by Create's ControlsHandler: 0=W 1=S 2=A 3=D 4=space 5=shift
    private static final int KEY_LEFT = 2;
    private static final int KEY_RIGHT = 3;
    private static final int KEY_SPACE = 4;

    @WrapMethod(method = "control")
    public boolean electroEnergetics$control(BlockPos controlsLocalPos, Collection<Integer> heldControls, Player player, Operation<Boolean> original) {
        CarriageContraptionEntity self = (CarriageContraptionEntity) (Object) this;
        if (self.getCarriage() == null || self.getCarriage().train == null)
            return original.call(controlsLocalPos, heldControls, player);
        ElectricTrainData data = ((ICEETrainExtension) self.getCarriage().train).getElectricTrainData();

        if (!(data.hasTractionMotors && data.electricControl() && data.controlMode != TrainControlMode.CREATE))
            return original.call(controlsLocalPos, heldControls, player);

        boolean left = heldControls.contains(KEY_LEFT);
        boolean right = heldControls.contains(KEY_RIGHT);

        // A/D still steer through junctions (B-09).
        self.getCarriage().train.manualSteer = right ? TravellingPoint.SteerDirection.RIGHT
                : left ? TravellingPoint.SteerDirection.LEFT : TravellingPoint.SteerDirection.NONE;

        if (heldControls.contains(KEY_SPACE))
            TrainDriverState.setSpaceHeld(data);

        // W/S intentionally do nothing on the electric panel; the gear law owns speed.
        return true;
    }
}
