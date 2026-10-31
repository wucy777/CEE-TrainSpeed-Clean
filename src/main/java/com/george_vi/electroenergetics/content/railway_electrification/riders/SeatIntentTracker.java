package com.george_vi.electroenergetics.content.railway_electrification.riders;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.railway_electrification.riders.RiderSeatGate.SeatRef;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side bookkeeping for client seat intents (E-16).
 *
 * The client (which owns its own position) reports which seat looked nearest to it;
 * the server validates the report against the authoritative state before honouring it.
 * Conflicts resolve by arrival order - the loser falls back to the server's own
 * nearest-seat computation in {@link RiderSeatGate#trySeat}.
 */
public class SeatIntentTracker {

    private static final long INTENT_TTL_MS = 2000;
    private static final double MAX_FORGE_DISTANCE_SQR = 16 * 16;

    private record Intent(UUID trainId, int carriageIndex, int seatIndex, long time) {
    }

    private static final Map<UUID, Intent> INTENTS = new ConcurrentHashMap<>();

    public static void report(ServerPlayer player, UUID trainId, int carriageIndex, int seatIndex) {
        INTENTS.put(player.getUUID(), new Intent(trainId, carriageIndex, seatIndex, System.currentTimeMillis()));
    }

    /**
     * Returns the reported seat as a server-side SeatRef if it is fresh, belongs to the
     * given train, is currently empty and the player is actually on the train.
     * Consumes the intent either way.
     */
    public static SeatRef consumeIntent(ServerPlayer player, Train train) {
        Intent intent = INTENTS.remove(player.getUUID());
        if (intent == null)
            return null;
        if (System.currentTimeMillis() - intent.time() > INTENT_TTL_MS)
            return null;
        if (!intent.trainId().equals(train.id))
            return null;
        if (intent.carriageIndex() < 0 || intent.carriageIndex() >= train.carriages.size())
            return null;

        Carriage carriage = train.carriages.get(intent.carriageIndex());
        var dims = carriage.getPresentDimensions();
        if (dims.isEmpty())
            return null;
        var dce = carriage.getDimensionalIfPresent(dims.getFirst());
        if (dce == null || dce.entity == null || dce.entity.get() == null
                || !(dce.entity.get() instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity entity))
            return null;

        // Anti-forgery: the reporting player must genuinely stand on this train.
        if (entity.level() == player.level()
                && !RiderSeatGate.isOnContraption(player, entity)
                && entity.position().distanceToSqr(player.position()) > MAX_FORGE_DISTANCE_SQR)
            return null;

        SeatRef ref = new SeatRef(carriage, entity, intent.seatIndex());
        if (!RiderSeatGate.isSeatEmpty(train, ref))
            return null;
        return ref;
    }

    public static void clear(UUID playerId) {
        INTENTS.remove(playerId);
    }
}
