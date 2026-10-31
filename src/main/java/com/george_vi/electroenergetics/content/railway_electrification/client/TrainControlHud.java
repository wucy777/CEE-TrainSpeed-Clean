package com.george_vi.electroenergetics.content.railway_electrification.client;

import com.george_vi.electroenergetics.content.railway_electrification.TrainControlMode;
import com.george_vi.electroenergetics.content.railway_electrification.TrainGear;
import com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The lever panel, bottom right (B-02/G-06/G-07/G-11):
 * a five-slot lever with a handle, the emergency brake and vigilance buttons,
 * a display screen, and the consist strip with pantograph positions and the head arrow.
 * Drawn procedurally - no texture assets, cached strings for performance (G-10).
 */
public class TrainControlHud {

    private static final int PANEL_W = 122;
    private static final int LEVER_W = 22;
    private static final int ROW = 10;

    private static final int BG = 0xC0101215;
    private static final int BG_LIGHT = 0x282E3642;
    private static final int EDGE = 0x503C4654;
    private static final int TEXT = 0xFFE6ECF2;
    private static final int TEXT_DIM = 0xFF8C97A3;
    private static final int ACCENT = 0xFF4FC3F7;
    private static final int GREEN = 0xFF66BB6A;
    private static final int AMBER = 0xFFFFC107;
    private static final int RED = 0xFFEF5350;
    private static final int SCREEN = 0xE00B1218;

    // Cached text so we only rebuild strings when values change (G-10).
    private static String cacheSpeed = "";
    private static String cacheKmh = "";
    private static String cachePower = "";
    private static String cacheVolt = "";

    public static void render(GuiGraphics gg, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.options.hideGui)
            return;
        SyncTrainPanelPacket panel = drivingPanel();
        if (panel == null)
            return;

        Font font = mc.font;
        int x = TrainControlLayout.rightPanelX(gg, PANEL_W);
        int y = gg.guiHeight() - panelHeight(panel) - TrainControlLayout.MARGIN;

        // Frame
        gg.fill(x, y, x + PANEL_W, y + panelHeight(panel), BG);
        gg.fill(x, y, x + PANEL_W, y + 1, EDGE);
        gg.fill(x, y + panelHeight(panel) - 1, x + PANEL_W, y + panelHeight(panel), EDGE);
        gg.fill(x, y, x + 1, y + panelHeight(panel), EDGE);
        gg.fill(x + PANEL_W - 1, y, x + PANEL_W, y + panelHeight(panel), EDGE);

        int cy = y + 4;

        // ---------------- mode & status line (G-11) ----------------
        Component modeLabel = modeLabel(panel);
        int statusColor = statusColor(panel);
        gg.fill(x + 4, cy, x + PANEL_W - 4, cy + ROW, BG_LIGHT);
        gg.drawString(font, modeLabel, x + 6, cy + 1, statusColor, false);
        cy += ROW + 2;

        // ---------------- lever + screen ----------------
        int leverTop = cy;
        int leverHeight = ROW * 5 + 8;
        drawLever(gg, font, panel, x + 4, leverTop, LEVER_W, leverHeight);

        int screenX = x + 4 + LEVER_W + 3;
        int screenW = PANEL_W - (LEVER_W + 11);
        gg.fill(screenX, leverTop, screenX + screenW, leverTop + leverHeight, SCREEN);

        String speed = String.format("%.1f", panel.speed());
        String kmh = String.format("%.0f km/h", panel.speed() * 3.6);
        if (!speed.equals(cacheSpeed)) {
            cacheSpeed = speed;
            cacheKmh = kmh;
        }
        gg.drawString(font, "速度", screenX + 4, leverTop + 3, TEXT_DIM, false);
        gg.drawString(font, speed, screenX + 4, leverTop + 12, ACCENT, false);
        gg.drawString(font, "m/s", screenX + 4 + font.width(speed) + 3, leverTop + 12 + 2, TEXT_DIM, false);
        gg.drawString(font, cacheKmh, screenX + 4, leverTop + 23, TEXT_DIM, false);

        // cap line (penalty / overload / overload cap)
        String cap = capText(panel);
        if (cap != null)
            gg.drawString(font, cap, screenX + 4, leverTop + 33, RED, false);

        String power = powerText(panel);
        if (!power.equals(cachePower)) {
            cachePower = power;
            cacheVolt = String.format("%.1f kV", Math.abs(panel.powerScale()) * 25.0f);
        }
        gg.drawString(font, cachePower, screenX + 4, leverTop + 43, panel.motorBraking() ? RED : GREEN, false);
        gg.drawString(font, cacheVolt, screenX + 4, leverTop + 53, TEXT_DIM, false);
        String consist = String.format("车厢 %d · 电机 %d", panel.carriageCount(), panel.motorCarriages());
        gg.drawString(font, consist, screenX + 4, leverTop + leverHeight - 10, TEXT_DIM, false);

        cy = leverTop + leverHeight + 3;

        // ---------------- buttons: K emergency / J vigilance (B-02) ----------------
        int btnH = 12;
        int btnW = (PANEL_W - 11) / 2;
        boolean ebHot = panel.ebArmed() || panel.ebActive();
        gg.fill(x + 5, cy, x + 5 + btnW, cy + btnH, ebHot ? RED : BG_LIGHT);
        gg.drawString(font, "K 紧急制动", x + 8, cy + 2, ebHot ? 0xFFFFFFFF : TEXT, false);

        int bx2 = x + 6 + btnW;
        gg.fill(bx2, cy, bx2 + btnW, cy + btnH, BG_LIGHT);
        int stageColor = panel.confirmStage() == 2 ? RED : panel.confirmStage() == 1 ? AMBER : TEXT_DIM;
        gg.drawString(font, "J 确认", bx2 + 8, cy + 2, stageColor, false);
        // vigilance progress underline (C-01)
        if (panel.confirmStage() != 0) {
            int barW = (int) ((btnW - 4) * Mth.clamp(panel.confirmProgress(), 0, 1));
            gg.fill(bx2 + 2, cy + btnH - 2, bx2 + 2 + barW, cy + btnH - 1, stageColor);
        }
        cy += btnH + 4;

        // ---------------- consist strip with pantographs (G-07) ----------------
        drawConsist(gg, panel, x + 5, cy, PANEL_W - 10, 16);
    }

    private static int panelHeight(SyncTrainPanelPacket panel) {
        return 4 + ROW + 2 + (ROW * 5 + 8) + 3 + 12 + 4 + 18 + 4;
    }

    private static void drawLever(GuiGraphics gg, Font font, SyncTrainPanelPacket panel, int x, int y, int w, int h) {
        TrainGear[] order = {TrainGear.ACCELERATE, TrainGear.CRUISE, TrainGear.COAST, TrainGear.BRAKE, TrainGear.REVERSE};
        String[] labels = {"加", "匀", "断", "制", "倒"};
        int slot = h / 5;
        int current = Math.floorMod(panel.gear(), TrainGear.values().length);

        for (int i = 0; i < order.length; i++) {
            int sy = y + i * slot;
            boolean active = order[i].ordinal() == current;
            boolean pending = com.george_vi.electroenergetics.client.TrainControlInput.pendingSelection == order[i];
            gg.fill(x, sy, x + w, sy + slot - 1, active ? BG_LIGHT : 0x00000000);
            gg.fill(x, sy, x + 2, sy + slot - 1, active ? ACCENT : 0x203C4654);
            int labelColor = active ? ACCENT : pending ? AMBER : TEXT_DIM;
            String label = labels[i] + (pending ? " *" : "");
            if (pending)
                labelColor = AMBER;
            gg.drawString(font, label, x + 5, sy + slot / 2 - 4, labelColor, false);
        }
        // handle
        int handleY = y + current * slot + slot / 2 - 2;
        gg.fill(x + w - 6, handleY - 3, x + w - 1, handleY + 3, ACCENT);
    }

    /** CR-series style consist: tapered smooth noses at both ends, pantograph glyphs, head arrow (G-07). */
    private static void drawConsist(GuiGraphics gg, SyncTrainPanelPacket panel, int x, int y, int w, int h) {
        int carriages = Math.max(1, panel.carriageCount());
        int bodyW = Math.max(6, (w - (carriages - 1)) / carriages);
        int noseW = Math.max(2, bodyW / 3);

        // head arrow above the leading end
        boolean inverted = panel.cabInverted();
        int headX = inverted ? x + w - bodyW / 2 : x + bodyW / 2;
        gg.fill(headX - 1, y - 4, headX + 1, y - 1, ACCENT);
        gg.fill(headX - 3, y - 3, headX + 3, y - 2, ACCENT);

        for (int i = 0; i < carriages; i++) {
            int cx = inverted ? x + w - (i + 1) * bodyW - i : x + i * (bodyW + 1);
            boolean first = i == 0;
            boolean last = i == carriages - 1;
            int bx = cx + (first ? noseW : 0);
            int bw = bodyW - (first ? noseW : 0) - (last ? noseW : 0);
            // body
            gg.fill(bx, y, bx + bw, y + h - 2, BG_LIGHT);
            // tapered nose (two steps, smooth-ish)
            if (first) {
                gg.fill(cx + noseW / 2, y + 2, cx + noseW, y + h - 4, BG_LIGHT);
                gg.fill(cx, y + 4, cx + noseW / 2, y + h - 6, BG_LIGHT);
            }
            if (last) {
                int rx = bx + bw;
                gg.fill(rx, y + 2, rx + noseW / 2, y + h - 4, BG_LIGHT);
                gg.fill(rx + noseW / 2, y + 4, rx + noseW, y + h - 6, BG_LIGHT);
            }
            // pantograph glyph on this carriage
            for (SyncTrainPanelPacket.PantographState ps : panel.pantographs()) {
                if (ps.carriageIndex() != i || ps.railShoe())
                    continue;
                int px = bx + bw / 2 - 2;
                int color = ps.active() ? GREEN : ps.autoRaise() ? AMBER : TEXT_DIM;
                gg.fill(px, y + h - 7, px + 1, y + h - 3, color);
                gg.fill(px + 3, y + h - 7, px + 4, y + h - 3, color);
                gg.fill(px + 1, y + h - 4, px + 3, y + h - 3, color);
                gg.fill(px - 1, y + h - 3, px + 5, y + h - 2, color);
                if (ps.autoRaise())
                    gg.drawString(Minecraft.getInstance().font, "A", px + 5, y + h - 10, AMBER, false);
            }
        }
    }

    // ------------------------------------------------------------------

    private static Component modeLabel(SyncTrainPanelPacket panel) {
        if (panel.controlsLocked() || panel.emergencyPenalty())
            return Component.literal("因紧急制动锁定 不能操控");
        if (panel.mode() == TrainControlMode.DEAD_COAST.ordinal())
            return Component.literal("断电惰行");
        if (panel.mode() == TrainControlMode.ELECTRIC.ordinal())
            return Component.literal("电动驾驶");
        return Component.literal("联控");
    }

    private static int statusColor(SyncTrainPanelPacket panel) {
        if (panel.controlsLocked() || panel.emergencyPenalty() || panel.ebActive())
            return RED;
        if (panel.mode() == TrainControlMode.DEAD_COAST.ordinal())
            return AMBER;
        return GREEN;
    }

    private static String capText(SyncTrainPanelPacket panel) {
        int capKmh = Math.round(panel.capSpeed() * 3.6f / 10f) * 10;
        if (panel.emergencyPenalty() || panel.penaltyActive())
            return String.format("限速 %d km/h", capKmh);
        if (panel.overloaded())
            return String.format("超载 限速 %d km/h", capKmh);
        return null;
    }

    private static String powerText(SyncTrainPanelPacket panel) {
        if (panel.ebActive() && panel.motorBraking())
            return String.format("反拖 %.0f kW", panel.powerKW());
        if (panel.mode() == TrainControlMode.DEAD_COAST.ordinal())
            return "断电 0 kW";
        if (panel.powerKW() <= 0.5)
            return "功率 0 kW";
        return String.format("功率 %.0f kW", panel.powerKW());
    }

    /** The panel belongs to the train the local player drives (sitting in a conductor seat). */
    public static SyncTrainPanelPacket drivingPanel() {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || !(player.getVehicle() instanceof CarriageContraptionEntity cce))
            return null;
        if (cce.getCarriage() == null || cce.getCarriage().train == null)
            return null;
        var panel = com.george_vi.electroenergetics.content.railway_electrification.ClientTrainPanel.get(cce.getCarriage().train.id);
        if (panel == null)
            return null;
        if (panel.mode() == TrainControlMode.CREATE.ordinal())
            return null; // Create HUD takes over (D-05)
        return panel;
    }
}
