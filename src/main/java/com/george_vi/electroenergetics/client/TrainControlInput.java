package com.george_vi.electroenergetics.client;

import com.george_vi.electroenergetics.content.railway_electrification.ClientTrainPanel;
import com.george_vi.electroenergetics.content.railway_electrification.TrainControlMode;
import com.george_vi.electroenergetics.content.railway_electrification.TrainGear;
import com.george_vi.electroenergetics.content.railway_electrification.packets.ReportSeatIntentPacket;
import com.george_vi.electroenergetics.content.railway_electrification.packets.SetAutoRaisePacket;
import com.george_vi.electroenergetics.content.railway_electrification.packets.SetPantographPacket;
import com.george_vi.electroenergetics.content.railway_electrification.packets.SetTrainGearPacket;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Turns panel key presses into packets. Gear selection is a two-step flow (B-08):
 * up/down choose, Enter engages. J acknowledges vigilance, K pulls the emergency brake.
 * Pantograph keys work in BOTH control modes (D-07).
 *
 * The cab reads its keys directly from GLFW (consumeClick on the KeyMappings), which is
 * what lets KeyboardHandlerMixin eat them for the rest of the game while the lever still
 * sees them.
 */
public class TrainControlInput {

    /** Pending gear selection on the lever display; cleared on engage. */
    public static TrainGear pendingSelection = null;
    /** Client-side selected pantograph index into the synced pantograph list (-1 = none). */
    public static int selectedPantograph = -1;

    /** True while the local player drives an electric train (for the key consumption). */
    public static boolean isDrivingElectric() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return false;
        var panel = drivingPanel();
        return panel != null && panel.mode() != TrainControlMode.CREATE.ordinal();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null)
            return;

        var panel = drivingPanel();

        while (TrainControlKeys.VIGILANCE_CONFIRM.consumeClick()) {
            if (panel != null)
                CatnipServices.NETWORK.sendToServer(new SetTrainGearPacket(panel.trainId(), 0, SetTrainGearPacket.Action.CONFIRM_VIGILANCE));
        }
        while (TrainControlKeys.EMERGENCY_BRAKE.consumeClick()) {
            if (panel != null)
                CatnipServices.NETWORK.sendToServer(new SetTrainGearPacket(panel.trainId(), 0, SetTrainGearPacket.Action.EMERGENCY_BRAKE));
        }

        if (panel != null) {
            while (TrainControlKeys.GEAR_UP.consumeClick())
                pendingSelection = nextGear(panel.gear(), 1);
            while (TrainControlKeys.GEAR_DOWN.consumeClick())
                pendingSelection = nextGear(panel.gear(), -1);
            while (TrainControlKeys.ENGAGE.consumeClick()) {
                TrainGear gear = pendingSelection != null ? pendingSelection
                        : TrainGear.values()[Math.floorMod(panel.gear(), TrainGear.values().length)];
                CatnipServices.NETWORK.sendToServer(new SetTrainGearPacket(panel.trainId(), gear.ordinal(), SetTrainGearPacket.Action.ENGAGE));
                pendingSelection = null;
            }
        } else {
            pendingSelection = null;
        }

        tickPantographs(panel);
        tickSeatIntent(player);
    }

    private static TrainGear nextGear(int current, int dir) {
        // Lever order top-down: ACCELERATE, CRUISE, COAST, BRAKE, REVERSE.
        TrainGear[] order = TrainGear.values();
        int index = Math.floorMod(current, order.length);
        return order[Math.floorMod(index + dir, order.length)];
    }

    /** The panel of the train the local player is driving, if any (G-02). */
    public static com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket drivingPanel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof CarriageContraptionEntity cce))
            return null;
        if (cce.getCarriage() == null || cce.getCarriage().train == null)
            return null;
        return ClientTrainPanel.get(cce.getCarriage().train.id);
    }

    // ------------------------------------------------------------------
    // Pantographs (D-07/D-10/M172)
    // ------------------------------------------------------------------

    private static void tickPantographs(com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket panel) {
        while (TrainControlKeys.PANTOGRAPH_NEXT.consumeClick()) {
            if (panel != null && panel.pantographs().size() >= 2)
                selectedPantograph = Math.min(panel.pantographs().size() - 1, selectedPantograph + 1);
        }
        while (TrainControlKeys.PANTOGRAPH_PREV.consumeClick()) {
            if (panel != null && panel.pantographs().size() >= 2)
                selectedPantograph = Math.max(0, selectedPantograph - 1);
        }

        var target = resolveSelected(panel);
        if (target == null)
            return;

        while (TrainControlKeys.PANTOGRAPH_TOGGLE.consumeClick())
            CatnipServices.NETWORK.sendToServer(new SetPantographPacket(panel.trainId(),
                    target.carriageIndex(), target.localPos().getX(), target.localPos().getY(),
                    target.localPos().getZ(), !target.active()));
        while (TrainControlKeys.PANTOGRAPH_AUTO.consumeClick())
            CatnipServices.NETWORK.sendToServer(new SetAutoRaisePacket(panel.trainId(),
                    target.carriageIndex(), target.localPos().getX(), target.localPos().getY(),
                    target.localPos().getZ(), !target.autoRaise()));
    }

    public static TargetedPantograph resolveSelected(com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket panel) {
        if (panel == null || panel.pantographs().isEmpty())
            return null;
        int index = panel.pantographs().size() == 1 ? 0 : Math.floorMod(selectedPantograph, panel.pantographs().size());
        var state = panel.pantographs().get(index);
        if (state.railShoe())
            return null;
        return new TargetedPantograph(index, state.carriageIndex(),
                pantographLocalPos(panel, state), state.active(), state.autoRaise());
    }

    /** nth non-shoe entry of the carriage matches the nth synced non-shoe state. */
    private static net.minecraft.core.BlockPos pantographLocalPos(com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket panel,
                                                                  com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket.PantographState state) {
        Train train = Minecraft.getInstance().level == null ? null
                : com.simibubi.create.CreateClient.RAILWAYS.trains.get(panel.trainId());
        if (train == null || state.carriageIndex() >= train.carriages.size())
            return net.minecraft.core.BlockPos.ZERO;
        var carriage = train.carriages.get(state.carriageIndex());
        var entries = ((com.george_vi.electroenergetics.mixin_interfaces.IPantographList) carriage).electroEnergetics$getPantographList();
        int ordinal = 0;
        for (var s : panel.pantographs()) {
            if (s == state)
                break;
            if (s.carriageIndex() == state.carriageIndex() && !s.railShoe())
                ordinal++;
        }
        int seen = 0;
        for (var e : entries) {
            if (com.george_vi.electroenergetics.CEEPantographTypes.RAIL_CONTACT_SHOE.get().equals(e.type))
                continue;
            if (seen++ == ordinal)
                return e.originalPos;
        }
        return net.minecraft.core.BlockPos.ZERO;
    }

    public record TargetedPantograph(int listIndex, int carriageIndex, net.minecraft.core.BlockPos localPos,
                                     boolean active, boolean autoRaise) {
    }

    // ------------------------------------------------------------------
    // Seat intent (E-16): the client decides which seat looks nearest, the server
    // validates. Reported while standing on any moving train, deduplicated by change.
    // ------------------------------------------------------------------

    private static boolean intentSent = false;
    private static int lastIntentSeat = Integer.MIN_VALUE;
    private static int lastIntentCarriage = Integer.MIN_VALUE;

    private static void tickSeatIntent(LocalPlayer player) {
        if (player.isSpectator() || player.isPassenger()) {
            intentSent = false;
            return;
        }
        // Find a moving electric train the player stands on (client-side trains).
        Train found = null;
        int carriageIndex = -1;
        for (var train : com.simibubi.create.CreateClient.RAILWAYS.trains.values()) {
            if (Math.abs(train.speed) < 1.0d)
                continue;
            for (int i = 0; i < train.carriages.size(); i++) {
                var carriage = train.carriages.get(i);
                var dce = carriage.getDimensional(player.level());
                if (dce == null || dce.entity == null || dce.entity.get() == null)
                    continue;
                var entity = dce.entity.get();
                var local = entity.toLocalVector(player.position(), 1.0f);
                var blocks = entity.getContraption().getBlocks();
                var feet = net.minecraft.core.BlockPos.containing(local);
                if (!blocks.containsKey(feet) && !blocks.containsKey(feet.below()))
                    continue;
                found = train;
                carriageIndex = i;
                break;
            }
            if (found != null)
                break;
        }
        if (found == null) {
            intentSent = false;
            return;
        }

        int bestSeat = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < found.carriages.size(); i++) {
            var carriage = found.carriages.get(i);
            var dce = carriage.getDimensional(player.level());
            if (dce == null || dce.entity == null || dce.entity.get() == null)
                continue;
            var entity = dce.entity.get();
            var localPlayer = entity.toLocalVector(player.position(), 1.0f);
            var seats = entity.getContraption().getSeats();
            var claimed = entity.getContraption().getSeatMapping();
            for (int s = 0; s < seats.size(); s++) {
                if (claimed.containsValue(s))
                    continue;
                double d = net.minecraft.world.phys.Vec3.atCenterOf(seats.get(s)).distanceToSqr(localPlayer);
                if (d < bestDist) {
                    bestDist = d;
                    bestSeat = s;
                }
            }
        }
        if (bestSeat < 0) {
            intentSent = false;
            return;
        }
        if (intentSent && bestSeat == lastIntentSeat && carriageIndex == lastIntentCarriage)
            return; // nothing changed: no traffic
        CatnipServices.NETWORK.sendToServer(new ReportSeatIntentPacket(found.id, carriageIndex, bestSeat));
        intentSent = true;
        lastIntentSeat = bestSeat;
        lastIntentCarriage = carriageIndex;
    }
}
