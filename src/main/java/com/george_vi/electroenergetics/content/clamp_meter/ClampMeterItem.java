package com.george_vi.electroenergetics.content.clamp_meter;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.client.NodeVoltageHolder;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.InteractWirePacket;
import com.george_vi.electroenergetics.content.wire.interaction.OutlinesOnWireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class ClampMeterItem extends Item {

    public ClampMeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {

        if (level.isClientSide()) {
            NodeConnectionPoint point = WireInteractionHandler.targetedPoint;
            if (point == null || usedHand != InteractionHand.MAIN_HAND)
                return InteractionResultHolder.pass(player.getItemInHand(usedHand));
            CatnipServices.NETWORK.sendToServer(new InteractWirePacket(point));
            player.startUsingItem(usedHand);
        }
        return super.use(level, player, usedHand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 9999;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (!(livingEntity instanceof Player player))
            return;
        if (level.isClientSide) {
            NodeConnectionPoint point = WireInteractionHandler.targetedPoint;
            if (point == null) {
                player.stopUsingItem();
                return;
            }

            if (!point.connection().equals(stack.getOrDefault(CEEDataComponents.NODE_CONNECTION, point.connection()))) {
                CatnipServices.NETWORK.sendToServer(new InteractWirePacket(point));
                return;
            }

            double voltage = NodeVoltageHolder.getVoltageBetween(point.node1(), point.node2());
            Pair<InWorldNodeConnection, WireData> wire = null;
            for (Pair<InWorldNodeConnection, WireData> connection : WireRenderer.getAllConnections()) {
                if (connection.getFirst().equals(new InWorldNodeConnection(point.node1(), point.node2()))) {
                    wire = connection;
                    break;
                }
            }

            if (wire == null)
                return;

            double resistance = wire.getSecond().getResistance();
            CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> setMetering((float) (voltage / resistance)));
        }
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        stack.remove(CEEDataComponents.NODE_CONNECTION);
        CatnipServices.PLATFORM.executeOnClientOnly(() -> this::stopMetering);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        onStopUsing(stack, livingEntity, timeCharged);
    }

    @OnlyIn(Dist.CLIENT)
    protected void stopMetering() {
        ElectricPropertiesOverlay.INSTANCE.removeMeter();
    }

    @OnlyIn(Dist.CLIENT)
    protected void setMetering(float amperage) {
        OutlinesOnWireRenderer.renderCurrent(amperage);
    }
}
