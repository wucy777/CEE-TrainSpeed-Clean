package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.content.railway_electrification.ClientTrainPanel;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricTrainData;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a player standing inside a fast electric carriage the same footing they have on
 * solid ground (E-07: walk, sprint and jump on the train like anywhere else).
 *
 * Measured behaviour without this: the horizontal carry is correct - the player moves
 * with the carriage - but the player is in permanent free fall, because vanilla's
 * grounding test looks for blocks beneath the entity and finds none. Create's collider
 * only sets onGround when the floor's friction is at most 0.6, which carriage flooring
 * never is. The player walks on a floor the game does not acknowledge and sinks through
 * it while the carry keeps sliding them along - the "穿地板" reports.
 *
 * What this does: for a player in the floor band of an electric carriage, restore
 * onGround (gravity stops accumulating, jumping works) and drop downward velocity
 * (no inherited sink). The horizontal position is NOT touched - the carry owns it and
 * is already correct.
 */
@Mixin(value = Entity.class, remap = false)
public class CarriageFloorMixin {

    /** Height above the carriage floor at which the player stops counting as grounded. */
    @Unique
    private static final double electroEnergetics$FLOOR_BAND = 0.75d;

    @Unique
    private static final double electroEnergetics$SEAT_TO_FLOOR = 0.125d;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void electroEnergetics$restoreFooting(CallbackInfo ci) {
        try {
            if (!((Object) this instanceof Player player))
                return;
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof CarriageContraptionEntity carriage) {
                electroEnergetics$standOnCarriageFloor(player, carriage);
                return;
            }
            if (player.isPassenger())
                return;
            // Not riding: the player may be walking inside the carriage they just left
            // the seat of. The nearest electric carriage within reach is the candidate.
            electroEnergetics$standOnNearestFloor(player);
        } catch (Throwable ignored) {
            // A footing fix must never be the thing that breaks movement.
        }
    }

    @Unique
    private static void electroEnergetics$standOnNearestFloor(Player player) {
        double best = Double.MAX_VALUE;
        CarriageContraptionEntity found = null;
        for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(6))) {
            if (!(e instanceof CarriageContraptionEntity carriage))
                continue;
            if (!electroEnergetics$isElectric(carriage))
                continue;
            double d = carriage.position().distanceTo(player.position());
            if (d < best) {
                best = d;
                found = carriage;
            }
        }
        if (found != null)
            electroEnergetics$standOnCarriageFloor(player, found);
    }

    @Unique
    private static void electroEnergetics$standOnCarriageFloor(Player player, CarriageContraptionEntity carriage) {
        if (!electroEnergetics$isElectric(carriage))
            return;
        Vec3 seat = carriage.getPassengerPosition(player, 1.0f);
        if (seat == null)
            return;
        double aboveFloor = player.getY() - (seat.y - electroEnergetics$SEAT_TO_FLOOR);
        if (aboveFloor < -0.5 || aboveFloor > electroEnergetics$FLOOR_BAND)
            return;
        electroEnergetics$ground(player);
    }

    @Unique
    private static void electroEnergetics$ground(Player player) {
        player.setOnGround(true);
        // Only cancel downward motion that came from the floor being ignored; an upward
        // velocity is a jump in progress and must be left alone.
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < 0)
            player.setDeltaMovement(motion.x, 0, motion.z);
        player.fallDistance = 0;
    }

    /**
     * Electric-train test that works on BOTH sides: the server has the electrical solve's
     * structural flag, the client consults the synced panel (the simulation never runs
     * client-side, so the local flag would be dead there).
     */
    @Unique
    private static boolean electroEnergetics$isElectric(CarriageContraptionEntity carriage) {
        var c = carriage.getCarriage();
        if (c == null || c.train == null)
            return false;
        ElectricTrainData data = ((ICEETrainExtension) c.train).getElectricTrainData();
        if (data.hasTractionMotors)
            return true;
        var panel = ClientTrainPanel.get(c.train.id);
        return panel != null && panel.motorCarriages() > 0;
    }
}
