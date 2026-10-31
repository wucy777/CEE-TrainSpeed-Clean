package com.george_vi.electroenergetics.content.electrical_panel.link;

import com.george_vi.electroenergetics.foundation.CEELang;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public abstract class AbstractLinkScreen<T extends AbstractLinkMenu> extends AbstractSimiContainerScreen<T> {

    public AbstractLinkScreen(T container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics graphics, int x, int y) {
        if (!menu.getCarried()
                .isEmpty() || this.hoveredSlot == null || hoveredSlot.container == menu.playerInventory) {
            super.renderTooltip(graphics, x, y);
            return;
        }

        List<Component> list = new LinkedList<>();
        if (hoveredSlot.hasItem())
            list = getTooltipFromContainerItem(hoveredSlot.getItem());

        graphics.renderComponentTooltip(font, addToTooltip(list, hoveredSlot.getSlotIndex()), x, y);
    }

    private List<Component> addToTooltip(List<Component> list, int slot) {
        if (slot >= 0 && slot < slots())
            list.add(CEELang.translateDirect("electrical_panel_linking.frequency_slot_" + slot % 2)
                    .withStyle(ChatFormatting.GOLD));
        return list;
    }

    public abstract int slots();
}
