package com.george_vi.electroenergetics.content.electrical_panel.link;

import com.george_vi.electroenergetics.CEEMenuTypes;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ElectricalPanelLinkMenu extends AbstractLinkMenu {

    public ElectricalPanelLinkMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public ElectricalPanelLinkMenu(MenuType<?> type, int id, Inventory inv, ElectricalPanelLink contentHolder) {
        super(type, id, inv, contentHolder);
        this.contentHolder = contentHolder;
    }

    public static ElectricalPanelLinkMenu create(int id, Inventory inv, ElectricalPanelLink contentHolder) {
        return new ElectricalPanelLinkMenu(CEEMenuTypes.ELECTRICAL_PANEL_LINK_MENU.get(), id, inv, contentHolder);
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(-45, 131);

        addSlot(new SlotItemHandler(ghostInventory, 0, 24, 34));
        addSlot(new SlotItemHandler(ghostInventory, 1, 24, 52));
    }

    @Override
    public int slots() {
        return 2;
    }
}
