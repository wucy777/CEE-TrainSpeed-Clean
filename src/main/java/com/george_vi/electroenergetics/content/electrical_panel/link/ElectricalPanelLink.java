package com.george_vi.electroenergetics.content.electrical_panel.link;

import com.george_vi.electroenergetics.CEEPartialModels;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public interface ElectricalPanelLink extends MenuProvider {

    ItemStack[] getLinkFrequencies();
    ElectricalPanelLinkable[] getLinkables();
    Level getLevel();

    default void writeLinkFrequencies(CompoundTag tag, HolderLookup.Provider registries) {

        for (int i = 0; i < frequencies() * 2; i++) {
            ItemStack linkFrequency = getLinkFrequencies()[i];
            if (linkFrequency.isEmpty())
                continue;
            tag.put("LinkFrequency" + i, linkFrequency.save(registries));
        }
    }

    default void readLinkFrequencies(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < frequencies() * 2; i++) {
            if (!tag.contains("LinkFrequency" + i)) {
                getLinkFrequencies()[i] = ItemStack.EMPTY;
                continue;
            }
            Tag itemTag = tag.get("LinkFrequency" + i);
            if (itemTag != null)
                getLinkFrequencies()[i] = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
            else
                getLinkFrequencies()[i] = ItemStack.EMPTY;
        }
        for (int i = 0; i < frequencies(); i++) {
            getLinkables()[i].freq1 = getLinkFrequencies()[i * 2];
            getLinkables()[i].freq2 = getLinkFrequencies()[i * 2 + 1];
        }
    }

    default void updateLinkState(int frequency) {
        if (frequency == -1) {
            for (int i = 0; i < frequencies(); i++) {
                ElectricalPanelLinkable linkable = getLinkables()[i];

                if (linkable.freq1 != getLinkFrequencies()[i * 2] ||
                        linkable.freq2 != getLinkFrequencies()[i * 2 + 1])
                    linkable.removeLinkState();
                linkable.freq1 = getLinkFrequencies()[i * 2];
                linkable.freq2 = getLinkFrequencies()[i * 2 + 1];
                linkable.updateLinkState();
            }
        } else {
            ElectricalPanelLinkable linkable = getLinkables()[frequency];
            if (linkable.freq1 != getLinkFrequencies()[frequency * 2] ||
                    linkable.freq2 != getLinkFrequencies()[frequency * 2 + 1])
                linkable.removeLinkState();
            linkable.freq1 = getLinkFrequencies()[frequency * 2];
            linkable.freq2 = getLinkFrequencies()[frequency * 2 + 1];
            linkable.updateLinkState();
        }
    }

    default void updateLinkState(int frequency, int strength) {
        if (frequency == -1) {
            for (int i = 0; i < frequencies(); i++) {
                ElectricalPanelLinkable linkable = getLinkables()[i];

                if (linkable.freq1 != getLinkFrequencies()[i * 2] ||
                        linkable.freq2 != getLinkFrequencies()[i * 2 + 1])
                    linkable.removeLinkState();
                linkable.freq1 = getLinkFrequencies()[i * 2];
                linkable.freq2 = getLinkFrequencies()[i * 2 + 1];
                linkable.updateLinkState(strength);
            }
        } else {
            ElectricalPanelLinkable linkable = getLinkables()[frequency];
            if (linkable.freq1 != getLinkFrequencies()[frequency * 2] ||
                    linkable.freq2 != getLinkFrequencies()[frequency * 2 + 1])
                linkable.removeLinkState();
            linkable.freq1 = getLinkFrequencies()[frequency * 2];
            linkable.freq2 = getLinkFrequencies()[frequency * 2 + 1];
            linkable.updateLinkState(strength);
        }
    }

    default void removeLinkState(int frequency) {
        if (frequency == -1) {
            Arrays.stream(getLinkables()).forEach(ElectricalPanelLinkable::removeLinkState);
        } else {
            getLinkables()[frequency].removeLinkState();
        }
    }

    default int frequencies() {
        return 1;
    }

    @OnlyIn(Dist.CLIENT)
    default void renderLinkAntenna(ElectricalPanelBlockEntity be, PoseStack ms,
                                   MultiBufferSource buffer, int light) {
        if (Arrays.stream(getLinkFrequencies()).allMatch(ItemStack::isEmpty))
            return;

        boolean powered = false;
        for (ElectricalPanelLinkable linkable : getLinkables()) {
            if (linkable.getTransmittedStrength() != 0) {
                powered = true;
                break;
            }
        }

        CachedBuffers.partial(powered ?
                        CEEPartialModels.PANEL_ATTACHMENT_LINK_ANTENNA_POWERED :
                        CEEPartialModels.PANEL_ATTACHMENT_LINK_ANTENNA, be.getBlockState())
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.CUTOUT));
    }

    default boolean hasReturnToZeroOption() {
        return false;
    }

    default void setReturnToZero(boolean returnToZero) {

    }

    default boolean getReturnToZero() {
        return false;
    }

    @Override
    default @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return ElectricalPanelLinkMenu.create(containerId, playerInventory, this);
    }

    @Override
    default Component getDisplayName() {
        return CEELang.translateDirect("electrical_panel_linking.title");
    }

    static ElectricalPanelLink createOnClient(RegistryFriendlyByteBuf extraData) {
        return new Simple(extraData);
    }

    BlockPos getLocation();

    default void writeForConfiguration(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(frequencies());
        buf.writeBoolean(hasReturnToZeroOption());
        buf.writeBoolean(getReturnToZero());
        for (int i = 0; i < frequencies() * 2; i++)
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, getLinkFrequencies()[i]);
    }

    class Simple implements ElectricalPanelLink {
        private final ItemStack[] linkFrequencies;
        private final ElectricalPanelLinkable[] linkables;
        private final int frequencies;
        private final boolean hasReturnToZero;
        private final boolean returnToZero;

        public Simple(RegistryFriendlyByteBuf extraData) {
            frequencies = extraData.readVarInt();
            linkFrequencies = new ItemStack[frequencies * 2];
            linkables = new ElectricalPanelLinkable[frequencies];
            hasReturnToZero = extraData.readBoolean();
            returnToZero = extraData.readBoolean();
            for (int i = 0; i < frequencies * 2; i++) {
                linkFrequencies[i] = ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData);
            }
            for (int i = 0; i < frequencies; i++) {
                linkables[i] = new ElectricalPanelLinkable(this);
            }
        }

        @Override
        public int frequencies() {
            return frequencies;
        }

        @Override
        public ItemStack[] getLinkFrequencies() {
            return linkFrequencies;
        }

        @Override
        public ElectricalPanelLinkable[] getLinkables() {
            return linkables;
        }

        @Override
        public Level getLevel() {
            return null;
        }

        @Override
        public BlockPos getLocation() {
            return null;
        }

        @Override
        public boolean hasReturnToZeroOption() {
            return hasReturnToZero;
        }

        @Override
        public boolean getReturnToZero() {
            return returnToZero;
        }
    }
}
