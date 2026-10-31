package com.george_vi.electroenergetics.content.railway_electrification.riders;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.mixins.TeleportPendingAccessor;
import com.george_vi.electroenergetics.content.railway_electrification.ElectricTrainData;
import com.george_vi.electroenergetics.content.railway_electrification.TrainControlModeLogic;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.george_vi.electroenergetics.mixin_interfaces.ICEETrainExtension;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Forces standing riders of fast electric trains into the nearest empty seat of the
 * very same train, and tells the drive state when the train is over capacity.
 *
 * Hard rules from the requirement review:
 * - Whether a player counts as "on the train" is decided exclusively by converting the
 *   player's position into the carriage's local space and checking whether that block
 *   (or the block below it, at three heights up the body) is part of the contraption.
 *   World AABB tests are banned (E-15/L-01): the axis-aligned box does not turn with
 *   the contraption and would swallow players standing on the platform beside it.
 * - Forced seating never picks seats of other trains or of the world, and has no
 *   distance limit (P-01 answer A): it always uses the nearest empty seat of the same
 *   train, compared in the carriage's own local coordinates (E-02).
 * - Below the gate speed standing is legal: no checks, no prompts, no caps (E-07).
 */
public class RiderSeatGate {

    /** Create's own ceiling, in Blocks/Second: the speed the gate lives above. */
    public static final double CREATE_CEILING_BPS = 28d;

    private static final Map<UUID, Integer> lastNotify = new HashMap<>();
    private static final Map<UUID, Integer> lastDismountDenial = new HashMap<>();

    /** Per-tick upkeep only; the capacity decisions live in TrainDriverState.tickSeatGate. */
    public static void tick(Train train, ElectricTrainData data) {
        pruneGhostSeats(train);
    }

    // ------------------------------------------------------------------
    // Rider queries
    // ------------------------------------------------------------------

    public static boolean atOrAboveCeiling(Train train) {
        return Math.abs(train.speed) >= CREATE_CEILING_BPS / 20d - 1.0e-4d;
    }

    public static boolean anyUnseated(Train train) {
        return !unseatedPlayers(train).isEmpty();
    }

    /**
     * All non-spectator players standing on this train (feet inside the contraption
     * blocks), minus those already seated anywhere on it (E-14).
     */
    public static List<ServerPlayer> unseatedPlayers(Train train) {
        Set<UUID> seated = seatedPassengerIds(train);
        List<ServerPlayer> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Carriage carriage : train.carriages) {
            var dims = carriage.getPresentDimensions();
            if (dims.isEmpty())
                continue;
            Carriage.DimensionalCarriageEntity dce = carriage.getDimensionalIfPresent(dims.getFirst());
            if (dce == null || dce.entity == null || dce.entity.get() == null)
                continue;
            if (!(dce.entity.get() instanceof CarriageContraptionEntity entity))
                continue;
            if (!(entity.level() instanceof ServerLevel level))
                continue;
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || seen.contains(player.getUUID()))
                    continue;
                seen.add(player.getUUID());
                if (seated.contains(player.getUUID()))
                    continue;
                if (isOnContraption(player, entity))
                    result.add(player);
            }
        }
        return result;
    }

    /** UUIDs of every entity seated anywhere on this train, across all carriages (E-14). */
    public static Set<UUID> seatedPassengerIds(Train train) {
        Set<UUID> seated = new HashSet<>();
        for (Carriage carriage : train.carriages) {
            var contraption = contraptionOf(carriage);
            if (contraption == null)
                continue;
            seated.addAll(contraption.getSeatMapping().keySet());
        }
        return seated;
    }

    /**
     * The ONLY allowed "is the entity on this contraption" test (E-15/L-01): position
     * -> carriage-local BlockPos -> contraption block table, sampled at the feet, the
     * waist and the head, each also checking the block below. Standing on a roof, on a
     * seat or inside a low corridor all resolve; a player on the platform never does.
     */
    public static boolean isOnContraption(Entity entity, AbstractContraptionEntity carriage) {
        if (entity == null || carriage == null)
            return false;
        Contraption contraption = carriage.getContraption();
        if (contraption == null)
            return false;
        var blocks = contraption.getBlocks();
        Vec3 base = entity.position();
        for (int up = 0; up <= 2; up++) {
            Vec3 local = carriage.toLocalVector(base.add(0, up, 0), 1.0f);
            BlockPos pos = BlockPos.containing(local);
            if (blocks.containsKey(pos) || blocks.containsKey(pos.below()))
                return true;
        }
        return false;
    }

    /** The index of the carriage this entity is standing in, or -1 (E-16 validation). */
    public static int carriageIndexOf(Train train, Entity entity) {
        for (int i = 0; i < train.carriages.size(); i++) {
            var dims = train.carriages.get(i).getPresentDimensions();
            if (dims.isEmpty())
                continue;
            var dce = train.carriages.get(i).getDimensionalIfPresent(dims.getFirst());
            if (dce == null || dce.entity == null || dce.entity.get() == null)
                continue;
            if (isOnContraption(entity, dce.entity.get()))
                return i;
        }
        return -1;
    }

    /** The contraption of a carriage via its present entity, or null. */
    public static Contraption contraptionOf(Carriage carriage) {
        var dims = carriage.getPresentDimensions();
        if (dims.isEmpty())
            return null;
        Carriage.DimensionalCarriageEntity dce = carriage.getDimensionalIfPresent(dims.getFirst());
        if (dce == null || dce.entity == null || dce.entity.get() == null)
            return null;
        return dce.entity.get().getContraption();
    }

    // ------------------------------------------------------------------
    // Seating (P-01 answer A: nearest empty seat of the same train)
    // ------------------------------------------------------------------

    /**
     * Seats every standing player of this train, honouring client seat intents where
     * they are still valid (E-16). Returns how many players were seated.
     */
    public static int trySeatAll(Train train) {
        int seated = 0;
        for (ServerPlayer player : unseatedPlayers(train)) {
            // A teleport whose acknowledgement is still in flight would be dragged back
            // by the ack's second absMoveTo - seating during that window produces a
            // rubber-band snap. The next pass seats them instead.
            if (player.connection != null
                    && ((TeleportPendingAccessor) player.connection).electroEnergetics$awaitingPositionFromClient() != null)
                continue;
            if (trySeat(player, train)) {
                seated++;
                notifyPlayer(player, CEELang.translateDirect("riders.seated_forced").withStyle(net.minecraft.ChatFormatting.GRAY), 100);
            }
        }
        return seated;
    }

    public static boolean trySeat(ServerPlayer player, Train train) {
        SeatRef intended = SeatIntentTracker.consumeIntent(player, train);
        if (intended != null && isSeatEmpty(train, intended))
            return sitDown(player, intended);

        SeatRef best = null;
        double bestDist = Double.MAX_VALUE;
        for (Carriage carriage : train.carriages) {
            var dims = carriage.getPresentDimensions();
            if (dims.isEmpty())
                continue;
            Carriage.DimensionalCarriageEntity dce = carriage.getDimensionalIfPresent(dims.getFirst());
            if (dce == null || dce.entity == null || dce.entity.get() == null)
                continue;
            if (!(dce.entity.get() instanceof CarriageContraptionEntity entity))
                continue;
            if (!(entity.level() instanceof ServerLevel))
                continue;

            List<Integer> free = emptySeatIndexes(carriage);
            if (free.isEmpty())
                continue;
            var seats = entity.getContraption().getSeats();
            Vec3 localPlayer = entity.toLocalVector(player.position(), 1.0f);
            for (int index : free) {
                // Compared in the carriage's own local coordinates (E-02).
                double d = Vec3.atCenterOf(seats.get(index)).distanceToSqr(localPlayer);
                if (d < bestDist) {
                    bestDist = d;
                    best = new SeatRef(carriage, entity, index);
                }
            }
        }
        if (best == null)
            return false;
        return sitDown(player, best);
    }

    public static boolean isSeatEmpty(Train train, SeatRef ref) {
        Carriage carriage = ref.carriage();
        if (!train.carriages.contains(carriage) || contraptionOf(carriage) == null)
            return false;
        return emptySeatIndexes(carriage).contains(ref.seatIndex());
    }

    /** Seat indexes not claimed by any entity - players and non-players both block a seat (E-03). */
    public static List<Integer> emptySeatIndexes(Carriage carriage) {
        List<Integer> free = new ArrayList<>();
        var contraption = contraptionOf(carriage);
        if (contraption == null)
            return free;
        var seats = contraption.getSeats();
        Set<Integer> claimed = new HashSet<>(contraption.getSeatMapping().values());
        for (int i = 0; i < seats.size(); i++)
            if (!claimed.contains(i))
                free.add(i);
        return free;
    }

    /** Seats the player onto the given carriage seat; the vehicle snap moves them there. */
    public static boolean sitDown(ServerPlayer player, SeatRef ref) {
        if (!ref.entity().isAlive())
            return false;
        if (ref.entity().hasPassenger(player))
            return true;
        ref.entity().addSittingPassenger(player, ref.seatIndex());
        return ref.entity().hasPassenger(player);
    }

    // ------------------------------------------------------------------
    // Ghost seats (E-13)
    // ------------------------------------------------------------------

    /** Removes seat claims whose entity is no longer riding the contraption. */
    public static void pruneGhostSeats(Train train) {
        for (Carriage carriage : train.carriages) {
            var dims = carriage.getPresentDimensions();
            if (dims.isEmpty())
                continue;
            Carriage.DimensionalCarriageEntity dce = carriage.getDimensionalIfPresent(dims.getFirst());
            if (dce == null || dce.entity == null || dce.entity.get() == null
                    || !(dce.entity.get() instanceof CarriageContraptionEntity entity))
                continue;
            if (!(entity.level() instanceof ServerLevel level))
                continue;
            var mapping = entity.getContraption().getSeatMapping();
            Set<UUID> passengers = new HashSet<>();
            entity.getPassengers().forEach(e -> passengers.add(e.getUUID()));
            boolean changed = false;
            for (UUID id : List.copyOf(mapping.keySet())) {
                if (passengers.contains(id))
                    continue;
                Entity e = level.getEntity(id);
                if (e == null)
                    continue; // not loaded: keep the claim
                if (e instanceof Player || e.isRemoved()) {
                    mapping.remove(id); // players never keep stale claims
                    changed = true;
                }
            }
            if (changed)
                entity.getContraption().setSeatMapping(mapping);
        }
    }

    // ------------------------------------------------------------------
    // Notifications (E-09/E-10/E-17)
    // ------------------------------------------------------------------

    /** Sends an overlay message to everybody aboard, standing passengers included. */
    public static void notifyRiders(Train train, String langKey) {
        Set<UUID> sent = new HashSet<>();
        for (var carriage : train.carriages) {
            var entity = carriage.anyAvailableEntity();
            if (entity == null)
                continue;
            for (var passenger : entity.getIndirectPassengers())
                notifyOne(passenger, langKey, sent);
            for (Entity nearby : entity.level().players())
                if (isOnContraption(nearby, entity))
                    notifyOne(nearby, langKey, sent);
        }
    }

    private static void notifyOne(Entity entity, String langKey, Set<UUID> sent) {
        if (!(entity instanceof ServerPlayer player))
            return;
        if (!sent.add(player.getUUID()))
            return;
        player.displayClientMessage(CEELang.translateDirect(langKey), true);
    }

    public static void notifyPlayer(ServerPlayer player, Component message, int throttleTicks) {
        if (player.getServer() == null)
            return;
        int now = player.getServer().getTickCount();
        Integer last = lastNotify.get(player.getUUID());
        if (last != null && now - last < throttleTicks)
            return;
        lastNotify.put(player.getUUID(), now);
        player.displayClientMessage(message, true);
    }

    /** Action bar for a denied dismount (E-08), throttled. */
    public static void refuseDismount(ServerPlayer player) {
        if (player.getServer() == null)
            return;
        int now = player.getServer().getTickCount();
        Integer last = lastDismountDenial.get(player.getUUID());
        if (last != null && now - last < 20)
            return;
        lastDismountDenial.put(player.getUUID(), now);
        player.displayClientMessage(CEELang.translateDirect("riders.dismount_locked"), true);
    }

    // ------------------------------------------------------------------
    // Dismount lock (E-08)
    // ------------------------------------------------------------------

    /** True when the player may not leave their seat right now. */
    public static boolean shouldLockDismount(Player player) {
        Entity vehicle = player.getVehicle();
        AbstractContraptionEntity carriage;
        if (vehicle instanceof CarriageContraptionEntity cce)
            carriage = cce;
        else if (vehicle instanceof SeatEntity seat && seat.getRootVehicle() instanceof AbstractContraptionEntity root)
            carriage = root;
        else
            return false;
        Train train = carriage.getContraption() instanceof com.simibubi.create.content.trains.entity.CarriageContraption
                ? trainOf(carriage)
                : null;
        if (train == null)
            return false;
        ElectricTrainData data = ((ICEETrainExtension) train).getElectricTrainData();
        if (!TrainControlModeLogic.isElectricTrain(train))
            return false;
        // Seated on THIS train's own seat mapping counts even when the vehicle shape
        // does not say so.
        if (!train.carriages.isEmpty() && contraptionOf(train.carriages.get(0)) != null) {
            boolean seatedSomewhere = false;
            for (Carriage carriage2 : train.carriages) {
                var c = contraptionOf(carriage2);
                if (c != null && c.getSeatMapping().containsKey(player.getUUID()))
                    seatedSomewhere = true;
            }
            if (!seatedSomewhere && !(player.getVehicle() instanceof CarriageContraptionEntity))
                return false;
        }
        return Math.abs(train.speed) * 20 > CEEConfigs.server().trainValues.electricTrainSeatGateSpeed.getF();
    }

    private static Train trainOf(AbstractContraptionEntity entity) {
        if (entity instanceof CarriageContraptionEntity cce && cce.getCarriage() != null)
            return cce.getCarriage().train;
        return null;
    }

    // ------------------------------------------------------------------
    // Seat binding across relogs (E-11)
    // ------------------------------------------------------------------

    /** Remembers the seat a player logged out from and frees the claim (E-11/E-13). */
    public static void bindSeatOnLogout(ServerPlayer player) {
        if (!(player.getVehicle() instanceof CarriageContraptionEntity entity))
            return;
        Train train = entity.getCarriage() == null ? null : entity.getCarriage().train;
        if (train == null || train.carriages == null)
            return;
        var mapping = entity.getContraption().getSeatMapping();
        Integer index = mapping.get(player.getUUID());
        if (index == null)
            return;
        int carriageIndex = train.carriages.indexOf(entity.getCarriage());
        if (carriageIndex < 0)
            return;
        player.getPersistentData().putLong("CEESeatBindTrain", train.id.getMostSignificantBits());
        player.getPersistentData().putLong("CEESeatBindTrainLeast", train.id.getLeastSignificantBits());
        player.getPersistentData().putInt("CEESeatBindCarriage", carriageIndex);
        player.getPersistentData().putInt("CEESeatBindSeat", index);
        // Free the claim so nobody is blocked by a logged-out rider; the rebind path
        // re-seats the player if the train still exists.
        mapping.remove(player.getUUID());
        entity.getContraption().setSeatMapping(mapping);
    }

    /** Re-seats a returning player if their train still exists (E-11). */
    public static void reseatOnLogin(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.contains("CEESeatBindTrain"))
            return;
        UUID trainId = new UUID(data.getLong("CEESeatBindTrain"), data.getLong("CEESeatBindTrainLeast"));
        int carriageIndex = data.getInt("CEESeatBindCarriage");
        int seatIndex = data.getInt("CEESeatBindSeat");
        data.remove("CEESeatBindTrain");
        data.remove("CEESeatBindTrainLeast");
        data.remove("CEESeatBindCarriage");
        data.remove("CEESeatBindSeat");

        Train train = com.simibubi.create.Create.RAILWAYS.trains.get(trainId);
        if (train == null || carriageIndex >= train.carriages.size())
            return;
        Carriage carriage = train.carriages.get(carriageIndex);
        var dims = carriage.getPresentDimensions();
        if (dims.isEmpty())
            return;
        Carriage.DimensionalCarriageEntity dce = carriage.getDimensionalIfPresent(dims.getFirst());
        if (dce == null || dce.entity == null || dce.entity.get() == null
                || !(dce.entity.get() instanceof CarriageContraptionEntity entity))
            return;
        if (!emptySeatIndexes(carriage).contains(seatIndex))
            return;
        Vec3 pos = entity.toGlobalVector(Vec3.atLowerCornerOf(entity.getContraption().getSeats().get(seatIndex)).add(0.5, 0.5, 0.5), 1.0f);
        player.teleportTo((ServerLevel) entity.level(), pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        entity.addSittingPassenger(player, seatIndex);
    }

    /** Debug: run one gate pass regardless of speed (M200). */
    public static void testOnce(Train train, ElectricTrainData data) {
        pruneGhostSeats(train);
        trySeatAll(train);
        boolean still = anyUnseated(train);
        data.overloaded = still;
    }

    // ------------------------------------------------------------------

    public record SeatRef(Carriage carriage, CarriageContraptionEntity entity, int seatIndex) {
    }
}
