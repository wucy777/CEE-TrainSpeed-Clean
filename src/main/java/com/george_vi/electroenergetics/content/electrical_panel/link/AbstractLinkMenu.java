package com.george_vi.electroenergetics.content.electrical_panel.link;

import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.items.ItemStackHandler;

public abstract class AbstractLinkMenu extends GhostItemMenu<ElectricalPanelLink> implements IPanelAttachmentMenu {
    protected AbstractLinkMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    protected AbstractLinkMenu(MenuType<?> type, int id, Inventory inv, ElectricalPanelLink contentHolder) {
        super(type, id, inv, contentHolder);
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler itemStackHandler = new ItemStackHandler(slots());
        for (int i = 0; i < slots(); i++)
            itemStackHandler.setStackInSlot(i, contentHolder.getLinkFrequencies()[i]);

        return itemStackHandler;
    }

    public abstract int slots();

    @Override
    protected boolean allowRepeats() {
        return true;
    }

    @Override
    protected ElectricalPanelLink createOnClient(RegistryFriendlyByteBuf extraData) {
        return ElectricalPanelLink.createOnClient(extraData);
    }

    @Override
    protected void saveData(ElectricalPanelLink contentHolder) {
        contentHolder.removeLinkState(-1);
        for (int i = 0; i < slots(); i++)
            contentHolder.getLinkFrequencies()[i] = ghostInventory.getStackInSlot(i);
        contentHolder.updateLinkState(-1);
        if (contentHolder instanceof PanelAttachment a)
            a.sendData();
    }

    @Override
    public PanelAttachment getPanelAttachment() {
        return (PanelAttachment)contentHolder;
    }
}
