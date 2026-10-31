package com.george_vi.electroenergetics.content.electrical_panel.link;

import com.george_vi.electroenergetics.CEEGuiTextures;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.SetMenuPanelAttachmentOptionsPacket;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import static com.simibubi.create.foundation.gui.AllGuiTextures.PLAYER_INVENTORY;

public class BiDirectionalLinkScreen extends AbstractLinkScreen<BiDirectionalLinkMenu> {
    private final CEEGuiTextures background = CEEGuiTextures.BIDIRECTIONAL_LINK;
    private IconButton shouldReturn;

    public BiDirectionalLinkScreen(BiDirectionalLinkMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void init() {
        assert minecraft != null;
        assert minecraft.player != null;

        setWindowSize(background.getWidth(), background.getHeight() + 4 + PLAYER_INVENTORY.getHeight());
        setWindowOffset(1, 0);
        super.init();

        int x = leftPos;
        int y = topPos;

        IconButton confirmButton = new IconButton(x + background.getWidth() - 33, y + background.getHeight() - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> minecraft.player.closeContainer());

        addRenderableWidget(confirmButton);

        shouldReturn = new IconButton(x + 7, y + background.getHeight() - 24, CEEGuiTextures.LINK_RETURN_TO_ORIGINAL);
        shouldReturn.withCallback(() -> {
            shouldReturn.green ^= true;
            CatnipServices.NETWORK.sendToServer(
                    new SetMenuPanelAttachmentOptionsPacket(SetMenuPanelAttachmentOptionsPacket.RETURN_TO_ORIGINAL, shouldReturn.green));

        });
        shouldReturn.green = menu.contentHolder.getReturnToZero();
        shouldReturn.setToolTip(CEELang.translateDirect("electrical_panel_linking.return_to_zero"));
        if (menu.contentHolder.hasReturnToZeroOption())
            addRenderableWidget(shouldReturn);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int invX = getLeftOfCentered(PLAYER_INVENTORY.getWidth());
        int invY = topPos + background.getHeight() + 4;
        renderPlayerInventory(graphics, invX, invY);

        int x = leftPos;
        int y = topPos;

        background.render(graphics, x, y);
        graphics.drawString(font, title, x + 15, y + 4, 0x592424, false);
    }

    @Override
    public int slots() {
        return 4;
    }
}
