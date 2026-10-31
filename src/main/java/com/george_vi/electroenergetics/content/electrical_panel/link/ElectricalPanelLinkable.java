package com.george_vi.electroenergetics.content.electrical_panel.link;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public class ElectricalPanelLinkable implements IRedstoneLinkable {

    private final ElectricalPanelLink link;
    public ItemStack freq1 = ItemStack.EMPTY;
    public ItemStack freq2 = ItemStack.EMPTY;

    private int strength = 0;

    public ElectricalPanelLinkable(ElectricalPanelLink link) {
        this.link = link;
    }

    public void updateLinkState() {
        if (isAlive() && link.getLevel() != null && !link.getLevel().isClientSide)
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(link.getLevel(), this);
    }

    public void updateLinkState(int strength) {
        this.strength = strength;
        if (isAlive() && link.getLevel() != null && !link.getLevel().isClientSide)
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(link.getLevel(), this);
    }

    public void removeLinkState() {
        if (link.getLevel() != null && !link.getLevel().isClientSide) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(link.getLevel(), this);
        }
    }

    @Override
    public boolean isAlive() {
        return !freq1.isEmpty() || !freq2.isEmpty();
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public int getTransmittedStrength() {
        return strength;
    }

    @Override
    public void setReceivedStrength(int power) {

    }

    @Override
    public BlockPos getLocation() {
        return link.getLocation();
    }

    @Override
    public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
        return Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(freq1),
                RedstoneLinkNetworkHandler.Frequency.of(freq2));
    }
}
