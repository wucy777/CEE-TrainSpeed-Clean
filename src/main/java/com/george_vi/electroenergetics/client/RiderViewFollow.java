package com.george_vi.electroenergetics.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Turns a SEATED rider's view with the train in the two cases Create's own rotation does
 * not cover (G-08/K-17: seated riders keep view follow, standing riders deliberately do
 * not - Create has none and the owner decided not to add one).
 *
 * Case 1: a rider whose direct vehicle is a SeatEntity mounted on the contraption -
 * Create's hook only sees a direct AbstractContraptionEntity vehicle.
 *
 * Case 2: a rider whose carriage reports no changing view yaw at all (the reported
 * failure: the carriage swings around a world-fixed view while getViewYRot stands still).
 * The fallback derives the heading from the carriage's own displacement, expressed in the
 * same angle convention as getViewYRot.
 *
 * A rider Create is already turning is never touched: if the angle it reads is moving,
 * nothing is applied here, so the rotation can never be added twice.
 */
public final class RiderViewFollow {

    private RiderViewFollow() {
    }

    /** Angle below which a change is float noise rather than motion [degrees]. */
    private static final float EPSILON = 1.0e-4f;
    /** Displacement below which the direction of travel is noise [Blocks/tick]. */
    private static final double MIN_MOTION = 0.02;
    /** A step larger than this is a teleport of the carriage, not a turn. */
    private static final float MAX_STEP = 30.0f;

    private static int followedEntity = -1;
    private static float lastViewYaw = Float.NaN;
    private static float lastHeading = Float.NaN;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            forget();
            return;
        }

        Entity vehicle = player.getVehicle();
        boolean createCanSeeRider = vehicle instanceof AbstractContraptionEntity;

        AbstractContraptionEntity contraption;
        if (createCanSeeRider)
            contraption = (AbstractContraptionEntity) vehicle;
        else if (vehicle instanceof SeatEntity && vehicle.getRootVehicle() instanceof AbstractContraptionEntity root)
            contraption = root;
        else {
            // Standing players are deliberately NOT handled (K-17).
            forget();
            return;
        }

        float viewYaw = contraption instanceof CarriageContraptionEntity carriage
                ? carriage.getViewYRot(1.0f)
                : contraption.getRotationState().yRotation;

        // The contraption's direction of travel, in the same convention as the view angle.
        Vec3 motion = contraption.isPrevPosInvalid() ? Vec3.ZERO
                : contraption.position().subtract(contraption.getPrevPositionVec());
        boolean moving = motion.x * motion.x + motion.z * motion.z >= MIN_MOTION * MIN_MOTION;
        float heading = moving ? (float) (-Mth.atan2(motion.z, motion.x) * 180.0 / Math.PI) : Float.NaN;

        if (contraption.getId() != followedEntity || Float.isNaN(lastViewYaw)) {
            followedEntity = contraption.getId();
            lastViewYaw = viewYaw;
            lastHeading = heading;
            return;
        }

        float viewDelta = Mth.wrapDegrees(viewYaw - lastViewYaw);
        lastViewYaw = viewYaw;

        float headingDelta = 0.0f;
        if (moving) {
            if (!Float.isNaN(lastHeading))
                headingDelta = Mth.wrapDegrees(heading - lastHeading);
            lastHeading = heading;
        } else {
            lastHeading = Float.NaN;
        }

        // Create already turning this rider: adding anything here would turn them twice.
        if (createCanSeeRider && Math.abs(viewDelta) > EPSILON)
            return;

        float delta = Mth.clamp(headingDelta, -MAX_STEP, MAX_STEP);
        if (Math.abs(delta) < EPSILON)
            return;
        player.setYRot(player.getYRot() + delta);
    }

    private static void forget() {
        followedEntity = -1;
        lastViewYaw = Float.NaN;
        lastHeading = Float.NaN;
    }
}
