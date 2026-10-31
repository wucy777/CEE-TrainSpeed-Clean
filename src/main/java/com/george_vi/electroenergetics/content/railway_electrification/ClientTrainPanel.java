package com.george_vi.electroenergetics.content.railway_electrification;

import com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side cache of the per-train panel state, fed by {@link SyncTrainPanelPacket}.
 * Entries expire after 5 seconds without an update.
 */
public class ClientTrainPanel {

    private static final long EXPIRE_MS = 5000;
    private static final Map<UUID, Entry> PANELS = new HashMap<>();

    private record Entry(SyncTrainPanelPacket packet, long time) {
    }

    public static void update(SyncTrainPanelPacket packet) {
        PANELS.put(packet.trainId(), new Entry(packet, System.currentTimeMillis()));
    }

    public static SyncTrainPanelPacket get(UUID trainId) {
        Entry e = PANELS.get(trainId);
        if (e == null)
            return null;
        if (System.currentTimeMillis() - e.time() > EXPIRE_MS)
            return null;
        return e.packet();
    }

    /** The panel of the train the local player is currently driving, if any. */
    public static SyncTrainPanelPacket currentDrivingPanel() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce))
            return null;
        if (cce.getCarriage() == null || cce.getCarriage().train == null)
            return null;
        return get(cce.getCarriage().train.id);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        Iterator<Entry> it = PANELS.values().iterator();
        while (it.hasNext())
            if (now - it.next().time() > EXPIRE_MS)
                it.remove();
    }
}
