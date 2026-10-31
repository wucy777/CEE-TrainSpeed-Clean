package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricManualSpeed;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.entity.Carriage;
import net.createmod.catnip.config.ConfigBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Electric trains: the HUD speed bar divides by the design speed without Create's 0.75
 * manual factor (G-03), and the throttle scroll wheel does nothing on the panel (B-01).
 */
@Mixin(TrainHUD.class)
public class TrainHUDMixin {

    @Shadow
    private static Carriage getCarriage() {
        throw new AssertionError();
    }

    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/config/ConfigBase$ConfigFloat;getF()F", remap = false))
    private float electroEnergetics$barScale(ConfigBase.ConfigFloat instance, Operation<Float> original) {
        Carriage carriage = getCarriage();
        if (carriage != null && carriage.train != null)
            return ElectricManualSpeed.speedBarFactor(carriage.train, original.call(instance));
        return original.call(instance);
    }

    @WrapMethod(method = "onScroll")
    private static boolean electroEnergetics$noThrottleScroll(double delta, Operation<Boolean> original) {
        Carriage carriage = getCarriage();
        if (carriage != null && carriage.train != null
                && ElectricManualSpeed.speedBarFactor(carriage.train, com.simibubi.create.infrastructure.config.AllConfigs.server().trains.manualTrainSpeedModifier.getF()) == 1.0f)
            return false;
        return original.call(delta);
    }
}
