package com.george_vi.electroenergetics.mixins;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the server's "a teleport has been sent but not yet acknowledged" field.
 *
 * teleport() applies the destination immediately; what makes the field worth reading is
 * the SECOND application - when the client acknowledges, handleAcceptTeleportPacket calls
 * absMoveTo again with the stored destination, overwriting anything in between. A player
 * teleported between two seats on a moving train who is seated during that window gets
 * dragged back to the static destination coordinate, two to five blocks behind the train.
 * The seat-gate skips players whose teleport is still pending instead.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface TeleportPendingAccessor {

    /** The destination of an unacknowledged teleport, or null when none is in flight. */
    @Accessor("awaitingPositionFromClient")
    Vec3 electroEnergetics$awaitingPositionFromClient();
}
