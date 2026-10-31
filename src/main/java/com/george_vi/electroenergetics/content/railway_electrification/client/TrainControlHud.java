package com.george_vi.electroenergetics.content.railway_electrification.client;

import com.george_vi.electroenergetics.content.railway_electrification.TrainControlMode;
import com.george_vi.electroenergetics.content.railway_electrification.TrainGear;
import com.george_vi.electroenergetics.client.TrainControlInput;
import com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The lever panel, bottom right (B-02/G-06/G-07/G-11): a five-slot lever with a handle,
 * the emergency brake and vigilance buttons, a display screen with a driving hint, and
 * the consist strip with pantograph positions and the head arrow. Drawn procedurally -
 * no texture assets, cached strings for performance (G-10).
 */
public class TrainControlHud {

    private static final int PANEL_W = 128;
    private static final int LEVER_W = 22;
    private static final int SLOT = 12;
    private static final int LEVER_H = SLOT * 5 + 8;
    private static final int ROW = 11;

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

        gg.fill(x, y, x + PANEL_W, y + panelHeight(panel), BG);
        gg.fill(x, y, x + PANEL_W, y + 1, EDGE);
        gg.fill(x, y + panelHeight(panel) - 1, x + PANEL_W, y + panelHeight(panel), EDGE);
        gg.fill(x, y, x + 1, y + panelHeight(panel), EDGE);
        gg.fill(x + PANEL_W - 1, y, x + PANEL_W, y + panelHeight(panel), EDGE);

        int cy = y + 4;

        // ---------------- mode & status line (G-11) ----------------
        gg.fill(x + 4, cy, x + PANEL_W - 4, cy + 10, BG_LIGHT);
        gg.drawString(font, modeLabel(panel), x + 6, cy + 1, statusColor(panel), false);
        cy += 12;

        // ---------------- lever + screen ----------------
        int leverTop = cy;
        drawLever(gg, font, panel, x + 4, leverTop, LEVER_W, LEVER_H);

        int screenX = x + 4 + LEVER_W + 3;
        int screenW = PANEL_W - (LEVER_W + 11);
        gg.fill(screenX, leverTop, screenX + screenW, leverTop + LEVER_H, SCREEN);

        int row = leverTop + 3;
        gg.drawString(font, String.format("%.1f m/s", panel.speed()), screenX + 4, row, ACCENT, false);
        row += ROW;
        gg.drawString(font, String.format("%.0f km/h", panel.speed() * 3.6), screenX + 4, row, TEXT_DIM, false);
        row += ROW;
        String cap = capText(panel);
        if (cap != null) {
            gg.drawString(font, cap, screenX + 4, row, RED, false);
            row += ROW;
        }
        gg.drawString(font, powerText(panel), screenX + 4, row, panel.motorBraking() ? RED : GREEN, false);
        row += ROW;
        gg.drawString(font, String.format("%.1f kV · %d%%", panel.powerScale() * 25.0f,
                Math.round(panel.powerScale() * 100)), screenX + 4, row, TEXT_DIM, false);
        row += ROW;
        gg.drawString(font, String.format("车厢 %d · 电机 %d", panel.carriageCount(), panel.motorCarriages()),
                screenX + 4, row, TEXT_DIM, false);

        cy = leverTop + LEVER_H + 3;

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
        if (panel.confirmStage() != 0) {
            int barW = (int) ((btnW - 4) * Mth.clamp(panel.confirmProgress(), 0, 1));
            gg.fill(bx2 + 2, cy + btnH - 2, bx2 + 2 + barW, cy + btnH - 1, stageColor);
        }
        cy += btnH + 2;

        // ---------------- driving hint (how to actually start) ----------------
        gg.drawString(font, hintText(panel), x + 5, cy, TEXT_DIM, false);
        cy += ROW + 1;

        // ---------------- consist strip with pantographs (G-07) ----------------
        drawConsist(gg, panel, x + 5, cy, PANEL_W - 10, 16);
    }

    private static int panelHeight(SyncTrainPanelPacket panel) {
        int rows = 2; // speed + km/h
        if (capText(panel) != null)
            rows++;
        rows += 3; // power, volt, consist
        return 4 + 12 + LEVER_H + 3 + 12 + 2 + ROW + 1 + 4 + 16 + 4;
    }

    private static void drawLever(GuiGraphics gg, Font font, SyncTrainPanelPacket panel, int x, int y, int w, int h) {
        TrainGear[] order = {TrainGear.ACCELERATE, TrainGear.CRUISE, TrainGear.COAST, TrainGear.BRAKE, TrainGear.REVERSE};
        String[] labels = {"加", "匀", "断", "制", "倒"};
        int slot = h / 5;
        int current = Math.floorMod(panel.gear(), TrainGear.values().length);

        for (int i = 0; i < order.length; i++) {
            int sy = y + i * slot;
            boolean active = order[i].ordinal() == current;
            boolean pending = TrainControlInput.pendingSelection == order[i];
            boolean available = !order[i].isTraction() || panel.gearAvailable();
            gg.fill(x, sy, x + w, sy + slot - 1, active ? BG_LIGHT : 0x00000000);
            gg.fill(x, sy, x + 2, sy + slot - 1, active ? ACCENT : pending ? AMBER : 0x203C4654);
            int labelColor = active ? ACCENT : pending ? AMBER : available ? TEXT_DIM : 0x50333B44;
            String label = labels[i];
            if (pending && !active)
                label += " *";
            gg.drawString(font, label, x + 5, sy + slot / 2 - 4, labelColor, false);
        }
        int handleY = y + current * slot + slot / 2 - 2;
        gg.fill(x + w - 6, handleY - 3, x + w - 1, handleY + 3, ACCENT);
    }

    /** CR-series style consist: tapered smooth noses at both ends, pantograph glyphs, head arrow (G-07). */
    private static void drawConsist(GuiGraphics gg, SyncTrainPanelPacket panel, int x, int y, int w, int h) {
        int carriages = Math.max(1, panel.carriageCount());
        int bodyW = Math.max(6, (w - (carriages - 1)) / carriages);
        int noseW = Math.max(2, bodyW / 3);

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
            gg.fill(bx, y, bx + bw, y + h - 2, BG_LIGHT);
            if (first) {
                gg.fill(cx + noseW / 2, y + 2, cx + noseW, y + h - 4, BG_LIGHT);
                gg.fill(cx, y + 4, cx + noseW / 2, y + h - 6, BG_LIGHT);
            }
            if (last) {
                int rx = bx + bw;
                gg.fill(rx, y + 2, rx + noseW / 2, y + h - 4, BG_LIGHT);
                gg.fill(rx + noseW / 2, y + 4, rx + noseW, y + h - 6, BG_LIGHT);
            }
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
        int capKmh = Math.round(panel.capSpeed() * 3.6f);
        if (panel.emergencyPenalty() || panel.penaltyActive())
            return String.format("限速 %d km/h", capKmh);
        if (panel.overloaded())
            return String.format("超载 限速 100 km/h");
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

    /** What the driver should do next, in one line. */
    private static String hintText(SyncTrainPanelPacket panel) {
        if (panel.ebActive())
            return "紧急制动运行中，停止前不可干预";
        if (panel.controlsLocked())
            return "锁定中，稍后解锁（限速持续到下一站）";
        if (panel.mode() == TrainControlMode.DEAD_COAST.ordinal())
            return "无电：仅 制动/断电 可用，升弓恢复供电";
        TrainGear gear = TrainGear.values()[Math.floorMod(panel.gear(), TrainGear.values().length)];
        if (panel.speed() < 0.5f && (gear == TrainGear.COAST || gear == TrainGear.BRAKE))
            return "↑↓ 选挡 · Enter 发车 · P 升弓";
        return "↑↓ 选挡 · Enter 挂入 · P 升降弓 · O 自动";
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
