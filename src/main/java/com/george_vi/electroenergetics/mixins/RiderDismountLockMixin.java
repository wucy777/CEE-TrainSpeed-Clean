package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.content.railway_electrification.riders.RiderSeatGate;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses a player-initiated dismount from a seat while the train is above the seat gate
 * (E-08). Teleports, dimension changes, death and removal dismount through
 * Entity.stopRiding directly and are never affected by this lock.
 */
@Mixin(value = Player.class, remap = false)
public class RiderDismountLockMixin {

    @Inject(method = "wantsToStopRiding", at = @At("RETURN"), cancellable = true, remap = false)
    private void electroEnergetics$lockSeatAboveCeiling(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!cir.getReturnValue())
                return;
            if (!((Object) this instanceof Player player))
                return;
            if (!RiderSeatGate.shouldLockDismount(player))
                return;
            cir.setReturnValue(false);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                RiderSeatGate.refuseDismount(serverPlayer);
        } catch (Throwable ignored) {
            // Falling through to the normal dismount is always safe.
        }
    }
}
