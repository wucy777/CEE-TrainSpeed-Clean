package com.george_vi.electroenergetics.content.transmission_distribution.transformer;

import com.george_vi.electroenergetics.CEEGuiTextures;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.TransformerAttachment;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.simibubi.create.AllSoundEvents;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class TransformerScreen extends AbstractSimiScreen {

    int swapSidesX;
    int swapSidesY;
    int primaryX;
    int primaryY;
    int secondaryX;
    int secondaryY;
    int sliderWidth = 37;
    int sliderHeight = 13;
    int packetCooldown;
    boolean dirty;
    boolean playSound;

    public int primaryTurns;
    public int secondaryTurns;
    public boolean inverted;

    TransformerAttachment attachment;
    public TransformerScreen(TransformerAttachment attachment) {
        super(CEELang.translateDirect("gui.transformer"));
        this.attachment = attachment;
        primaryTurns = attachment.primaryTurns;
        secondaryTurns = attachment.secondaryTurns;
        inverted = attachment.inverted;
    }

    @Override
    protected void init() {
        setWindowSize(CEEGuiTextures.TRANSFORMER.getWidth(), CEEGuiTextures.TRANSFORMER.getHeight());
        setWindowOffset(0, 0);

        super.init();

        swapSidesX = guiLeft + 107;
        swapSidesY = guiTop + 53;
        primaryX = guiLeft + 42;
        primaryY = guiTop + 18;
        secondaryX = guiLeft + 42;
        secondaryY = guiTop + 89;
    }

    @Override
    protected void renderWindow(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        CEEGuiTextures.TRANSFORMER.render(graphics, x, y);
        CEEGuiTextures.TRANSFORMER_SWAP_SIDES.render(graphics, swapSidesX, swapSidesY);

        int stringWidth = font.width(title);
        graphics.drawString(font, title, x - stringWidth / 2 + windowWidth / 2, y - 4, 0xFFFFEE);

        boolean mouseOnPrimary = mouseX > primaryX && mouseX <= primaryX + sliderWidth &&
                mouseY > primaryY && mouseY <= primaryY + sliderHeight;

        boolean mouseOnSecondary = mouseX > secondaryX && mouseX <= secondaryX + sliderWidth &&
                mouseY > secondaryY && mouseY <= secondaryY + sliderHeight;

        Component primaryTurnsText = Component.literal(String.valueOf(inverted ? secondaryTurns : primaryTurns));
        Component secondaryTurnsText = Component.literal(String.valueOf(inverted ? primaryTurns : secondaryTurns));
        graphics.drawString(font, primaryTurnsText, primaryX - font.width(primaryTurnsText) / 2 + sliderWidth / 2, primaryY + 3, mouseOnPrimary ? 0xFFFFEE : 0x8E6F49);
        graphics.drawString(font, secondaryTurnsText, secondaryX - font.width(secondaryTurnsText) / 2 + sliderWidth / 2, secondaryY + 3, mouseOnSecondary ? 0xFFFFEE : 0x8E6F49);

        // Swap Sides
        if (mouseX > swapSidesX && mouseX <= swapSidesX + 14 &&
            mouseY > swapSidesY && mouseY <= swapSidesY + 14) {
            graphics.renderComponentTooltip(font,
                    List.of(
                            CEELang.translateDirect("gui.transformer.swap_sides"),
                            CEELang.translateDirect("gui.transformer.swap_sides_tip_1")
                                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                    ), mouseX, mouseY);
        }

        if (mouseOnPrimary) {
            graphics.renderComponentTooltip(font,
                    List.of(
                            CEELang.translateDirect(inverted ?
                                    "gui.transformer.secondary_turns" :
                                    "gui.transformer.primary_turns"),
                            CEELang.translateDirect("gui.transformer.turns_tip_1")
                                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                            CEELang.translateDirect("gui.transformer.turns_tip_2")
                                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                    ), mouseX, mouseY);
        }

        if (mouseOnSecondary) {
            graphics.renderComponentTooltip(font,
                    List.of(
                            CEELang.translateDirect(inverted ?
                                    "gui.transformer.primary_turns" :
                                    "gui.transformer.secondary_turns"),
                            CEELang.translateDirect("gui.transformer.turns_tip_1")
                                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                            CEELang.translateDirect("gui.transformer.turns_tip_2")
                                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                    ), mouseX, mouseY);
        }

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean lmb = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;

        // Swap Sides
        if (lmb && mouseX > swapSidesX && mouseX <= swapSidesX + 14 &&
                mouseY > swapSidesY && mouseY <= swapSidesY + 14) {
            inverted ^= true;
            playSound = true;
            dirty = true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX > primaryX && mouseX <= primaryX + sliderWidth &&
                mouseY > primaryY && mouseY <= primaryY + sliderHeight) {
            int prev = inverted ? secondaryTurns : primaryTurns;
            int newValue = Mth.clamp(prev + (scrollY > 0 ? 1 : -1) * (hasShiftDown() ? 5 : 1), 1, 240);

            if (inverted)
                secondaryTurns = newValue;
            else
                primaryTurns = newValue;
            if (prev != newValue) {
                playSound = true;
                dirty = true;
            }
        }

        if (mouseX > secondaryX && mouseX <= secondaryX + sliderWidth &&
                mouseY > secondaryY && mouseY <= secondaryY + sliderHeight) {
            int prev = inverted ? primaryTurns : secondaryTurns;
            int newValue = Mth.clamp(prev + (scrollY > 0 ? 1 : -1) * (hasShiftDown() ? 5 : 1), 1, 240);

            if (inverted)
                primaryTurns = newValue;
            else
                secondaryTurns = newValue;
            if (prev != newValue) {
                playSound = true;
                dirty = true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (playSound) {
            playSound = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null)
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        AllSoundEvents.SCROLL_VALUE.getMainEvent(), SoundSource.PLAYERS,
                        0.25f, 1f, false);
        }

        if (packetCooldown > 0) {
            packetCooldown--;
        }

        if (packetCooldown == 0 && dirty) {
            sendUpdate();
            packetCooldown = 10;
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (dirty)
            sendUpdate();
    }

    private void sendUpdate() {
        CatnipServices.NETWORK.sendToServer(new ConfigureTransformerAttachmentPacket(primaryTurns, secondaryTurns, attachment.pos, attachment.slot.ordinal(), inverted));
        attachment.primaryTurns = primaryTurns;
        attachment.secondaryTurns = secondaryTurns;
        attachment.inverted = inverted;
    }
}
