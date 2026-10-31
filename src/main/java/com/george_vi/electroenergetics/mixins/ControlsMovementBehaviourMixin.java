package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.content.railway_electrification.ClientTrainPanel;
import com.george_vi.electroenergetics.content.railway_electrification.TrainControlMode;
import com.george_vi.electroenergetics.content.railway_electrification.TrainGear;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The console lever animation follows the panel's gear instead of the W/S keys (B-09).
 * A/D steering animation keeps coming from the pressed keys.
 */
@Mixin(ControlsMovementBehaviour.class)
public class ControlsMovementBehaviourMixin {

    @WrapOperation(method = "renderInContraption",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;length()D", remap = true))
    private double electroEnergetics$leverFollowsGear(Vec3 motion, Operation<Double> original,
                                                      @Local(argsOnly = true) MovementContext context) {
        double motionValue = original.call(motion);
        if (!(context.contraption.entity instanceof CarriageContraptionEntity cce)
                || cce.getCarriage() == null || cce.getCarriage().train == null)
            return motionValue;
        var panel = ClientTrainPanel.get(cce.getCarriage().train.id);
        if (panel == null || panel.mode() == TrainControlMode.CREATE.ordinal())
            return motionValue;
        TrainGear gear = TrainGear.values()[Math.floorMod(panel.gear(), TrainGear.values().length)];
        double lever = Math.abs(gear.leverValue());
        if (panel.ebActive())
            lever = Math.abs(TrainGear.BRAKE.leverValue());
        return lever;
    }
}
