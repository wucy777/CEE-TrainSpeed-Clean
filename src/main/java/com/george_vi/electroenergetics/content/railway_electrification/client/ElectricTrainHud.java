package com.george_vi.electroenergetics.content.railway_electrification.client;

import com.george_vi.electroenergetics.content.railway_electrification.TrainControlMode;
import com.george_vi.electroenergetics.content.railway_electrification.TrainGear;
import com.george_vi.electroenergetics.content.railway_electrification.packets.SyncTrainPanelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Driving readout, bottom left (G-01/G-02/G-04/G-05/G-09): speed & cap, gear,
 * grade, power, catenary, consist & motor count. Display speed is smoothed
 * (tau = 0.12 s) and quantised to 0.1 m/s so the text never flickers.
 */
public class ElectricTrainHud {

    private static final int TEXT = 0xFFE6ECF2;
    private static final int TEXT_DIM = 0xFFB8C0CA;
    private static final int ACCENT = 0xFF4FC3F7;
    private static final int GREEN = 0xFF66BB6A;
    private static final int AMBER = 0xFFFFC107;
    private static final int RED = 0xFFEF5350;
    private static final int PANEL = 0x90101215;

    private static double smoothSpeed = 0;
    private static long lastFrame = 0;
    private static String cache = "";

    public static void render(GuiGraphics gg, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui)
            return;
        SyncTrainPanelPacket panel = TrainControlHud.drivingPanel();
        if (panel == null) {
            smoothSpeed = 0;
            return;
        }

        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0.1 : (now - lastFrame) / 1e9;
        lastFrame = now;
        double tau = 0.12;
        smoothSpeed += (panel.speed() - smoothSpeed) * (1 - Math.exp(-dt / tau));
        if (Math.abs(panel.speed() - smoothSpeed) > 5)
            smoothSpeed = panel.speed();

        double shown = Math.round(smoothSpeed * 10) / 10.0;
        if (shown < 0.05 && panel.speed() < 0.05)
            shown = 0;

        Font font = mc.font;
        String gearLabel = gearLabel(panel);
        String cap = capLabel(panel);
        String power = powerLabel(panel);
        String catenary = String.format("%.1f kV · %d%%", panel.powerScale() * 25.0f, Math.round(panel.powerScale() * 100));
        String consist = String.format("车厢 %d · 电机 %d", panel.carriageCount(), panel.motorCarriages());
        String grade = String.format("坡度 %+.1f%%", panel.grade());

        String composite = shown + "|" + gearLabel + "|" + cap + "|" + power + "|" + catenary + "|" + consist + "|" + grade;
        int x = TrainControlLayout.MARGIN;
        int y = gg.guiHeight() - 6 * 11 - TrainControlLayout.MARGIN - 4;
        if (!composite.equals(cache)) {
            cache = composite;
        }

        gg.fill(x - 3, y - 3, x + 108, y + 6 * 11 + 1, PANEL);
        gg.drawString(font, String.format("%.1f m/s", shown), x, y, ACCENT, true);
        gg.drawString(font, String.format("%.0f km/h", shown * 3.6), x + 58, y, TEXT_DIM, true);
        y += 11;
        gg.drawString(font, gearLabel, x, y, gearColor(panel), true);
        y += 11;
        gg.drawString(font, cap, x, y, capColor(panel), true);
        y += 11;
        gg.drawString(font, power, x, y, panel.motorBraking() ? RED : GREEN, true);
        y += 11;
        gg.drawString(font, catenary, x, y, panel.powerScale() > 0 ? TEXT : AMBER, true);
        y += 11;
        gg.drawString(font, consist, x, y, TEXT_DIM, true);
        y += 11;
        gg.drawString(font, grade, x, y, TEXT_DIM, true);
    }

    private static String gearLabel(SyncTrainPanelPacket panel) {
        if (panel.mode() == TrainControlMode.DEAD_COAST.ordinal())
            return "挡位 断电惰行";
        TrainGear gear = TrainGear.values()[Math.floorMod(panel.gear(), TrainGear.values().length)];
        String name = switch (gear) {
            case ACCELERATE -> "加速";
            case CRUISE -> String.format("匀速 %.0f", panel.cruiseTarget() * 3.6) + " km/h";
            case COAST -> "断电";
            case BRAKE -> "制动";
            case REVERSE -> "倒车";
        };
        String label = "挡位 " + name;
        if (panel.ebActive())
            label = "挡位 紧急制动";
        if (panel.unmanned())
            label += " (无人)";
        return label;
    }

    private static String capLabel(SyncTrainPanelPacket panel) {
        int capKmh = Math.round(panel.capSpeed() * 3.6f / 10f) * 10;
        if (panel.emergencyPenalty() || panel.penaltyActive())
            return String.format("限速 %d km/h (下一站解除)", capKmh);
        if (panel.overloaded())
            return String.format("超载限速 %d km/h", capKmh);
        return String.format("最高 %d km/h", Math.round(TrainControlHudCapDefault(panel)));
    }

    private static float TrainControlHudCapDefault(SyncTrainPanelPacket panel) {
        // Unpowered stretches keep showing the design cap instead of blanking out (G-09).
        return 100.8f * (panel.capSpeed() > 0 ? 1 : 1);
    }

    private static String powerLabel(SyncTrainPanelPacket panel) {
        if (panel.ebActive() && panel.motorBraking())
            return String.format("电机反拖 %.0f kW", panel.powerKW());
        if (panel.mode() == TrainControlMode.DEAD_COAST.ordinal())
            return "功率 0 kW";
        return String.format("功率 %.0f kW", panel.powerKW());
    }

    private static int gearColor(SyncTrainPanelPacket panel) {
        if (panel.ebActive())
            return RED;
        if (panel.mode() == TrainControlMode.DEAD_COAST.ordinal())
            return AMBER;
        return TEXT;
    }

    private static int capColor(SyncTrainPanelPacket panel) {
        if (panel.emergencyPenalty() || panel.penaltyActive() || panel.overloaded())
            return RED;
        return TEXT_DIM;
    }
}
