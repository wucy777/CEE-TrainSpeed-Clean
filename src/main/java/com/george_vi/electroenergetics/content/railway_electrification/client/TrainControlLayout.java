package com.george_vi.electroenergetics.content.railway_electrification.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Anchor helpers for the panel HUD: the lever panel sits bottom-right,
 * the readout bottom-left; the middle and top stay clear for the track view (G-05).
 */
public class TrainControlLayout {

    public static final int MARGIN = 8;

    public static int rightPanelX(GuiGraphics gg, int width) {
        return gg.guiWidth() - width - MARGIN;
    }

    public static int bottomPanelY(GuiGraphics gg, int height) {
        return gg.guiHeight() - height - MARGIN;
    }

    /** Small scale so the panel stays compact on any resolution. */
    public static float panelScale() {
        var window = Minecraft.getInstance().getWindow();
        double gui = window.getGuiScale();
        return (float) (gui >= 3 ? 1.0 : gui >= 2 ? 1.0 : 1.0);
    }
}
