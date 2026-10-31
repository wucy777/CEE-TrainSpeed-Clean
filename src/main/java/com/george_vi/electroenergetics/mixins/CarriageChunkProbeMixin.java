package com.george_vi.electroenergetics.mixins;

import com.simibubi.create.content.trains.entity.CarriageEntityHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Probes the carriage's own chunk instead of one 16 blocks ahead of it.
 *
 * Create's alignEntity refuses to place a carriage whose FORWARD point (anchor + 16
 * blocks of lookahead) is not in an entity-ticking chunk, and stalls the train's advance
 * for that tick while the client keeps moving - the two positions diverge and the rider
 * is later snapped back. At Create's own 1.4-2 blocks/tick the lookahead stays inside
 * loaded ground; at the speeds this mod permits it lands in territory nobody has ticked
 * yet, so the guard fires constantly.
 *
 * isActiveChunk loads nothing - it only asks whether a position is already ticking - so
 * the lookahead buys no loading and only makes the answer depend on something other than
 * "can this carriage be placed here". Asking about the carriage's own anchor is the
 * question that actually needs answering, and it costs less: no lookahead vector is
 * allocated at all.
 */
@Mixin(targets = "com.simibubi.create.content.trains.entity.Carriage$DimensionalCarriageEntity")
public class CarriageChunkProbeMixin {

    @Unique
    private Vec3 electroEnergetics$anchor() {
        return ((com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity)
                (Object) this).positionAnchor;
    }

    @Redirect(
            method = "alignEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/trains/entity/CarriageEntityHandler;"
                            + "isActiveChunk(Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/core/BlockPos;)Z"),
            remap = false)
    private boolean electroEnergetics$probeAtCarriage(Level level, BlockPos ignoredLookahead) {
        Vec3 anchor = electroEnergetics$anchor();
        // No anchor means alignEntity has nothing to place anyway; do not stall the train over it.
        if (anchor == null)
            return true;
        return CarriageEntityHandler.isActiveChunk(level, BlockPos.containing(anchor));
    }
}
