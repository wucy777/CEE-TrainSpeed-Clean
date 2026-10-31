package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.content.railway_electrification.riders.RiderSeatGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The second guard on a player leaving a seat at speed (E-08).
 *
 * wantsToStopRiding is the precise question - "does this player want to get off" - and it
 * lives in {@link RiderDismountLockMixin}. It was not enough on its own: a dismount can
 * also arrive through stopRiding from elsewhere, and in testing the player still left the
 * seat at speed.
 *
 * This must target Entity, not Player: on Player the method is only inherited, Mixin
 * finds no target and refuses the whole mixin.
 *
 * Teleports and dimension changes are let through deliberately - they dismount through
 * this same method, and refusing them would strand the player or leave a ghost passenger.
 */
@Mixin(value = Entity.class, remap = false)
public class RiderStopRidingLockMixin {

    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true, remap = false)
    private void electroEnergetics$lockStopRiding(CallbackInfo ci) {
        try {
            if (!((Object) this instanceof ServerPlayer player))
                return;
            if (player.isChangingDimension())
                return;
            if (player.isDeadOrDying() || player.isRemoved())
                return; // dying must always be allowed to dismount
            if (((TeleportPendingAccessor) player.connection)
                    .electroEnergetics$awaitingPositionFromClient() != null)
                return;
            if (!RiderSeatGate.shouldLockDismount(player))
                return;
            ci.cancel();
            RiderSeatGate.refuseDismount(player);
        } catch (Throwable ignored) {
            // Letting the dismount through is the safe failure.
        }
    }
}
