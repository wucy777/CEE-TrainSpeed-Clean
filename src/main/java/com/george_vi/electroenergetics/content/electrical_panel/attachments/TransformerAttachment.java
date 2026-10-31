package com.george_vi.electroenergetics.content.electrical_panel.attachments;

import com.george_vi.electroenergetics.CEEPartialModels;
import com.george_vi.electroenergetics.CreateElectroEnergetics;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerElectricalProperties;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerScreen;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.george_vi.electroenergetics.foundation.RMSHolder;
import com.george_vi.electroenergetics.foundation.SendSparkPacket;
import com.george_vi.electroenergetics.foundation.device.ElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangNumberFormat;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

public class TransformerAttachment extends PanelAttachment {
    public int primaryTurns;
    public int secondaryTurns;
    public boolean inverted;
    public boolean blown;
    public RMSHolder rmsPrimaryVoltages;
    public RMSHolder rmsSecondaryVoltages;
    public double primaryVoltage;
    public double secondaryVoltage;
    public float temp;

    public TransformerAttachment(PanelAttachmentType type) {
        super(type);
    }

    @Override
    public void tickClient(ElectricalPanelBlockEntity be) {

    }

    @Override
    public void render(ElectricalPanelBlockEntity be, float partialTicks, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        transformPose(ms, be);
        CachedBuffers.partial(blown ?
                                CEEPartialModels.PANEL_ATTACHMENT_SMOL_TRANSFORMER_BLOWN :
                                CEEPartialModels.PANEL_ATTACHMENT_SMOL_TRANSFORMER,
                        be.getBlockState())
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        // 0 -R- PD --- 2
        // 1 -R- SD --- 3

        if (blown)
            return;

        InWorldNode primaryDivNode = new InWorldNode(nodes[0].id() + 1_000_000, nodes[0].sourcePos());
        InWorldNode secondaryDivNode = new InWorldNode(nodes[0].id() + 1_000_001, nodes[0].sourcePos());

        double ratio = (double) primaryTurns / -secondaryTurns;
        if (inverted)
            ratio = 1 / ratio;
        TransformerElectricalProperties ep = new TransformerElectricalProperties(ratio,
                new DirectionalNodeConnection(primaryDivNode, nodes[2]),
                new DirectionalNodeConnection(secondaryDivNode, nodes[3]));
        bridges.addInternalNode(primaryDivNode);
        bridges.addInternalNode(secondaryDivNode);
        bridges.bridge(nodes[0], primaryDivNode, ElectricalProperties.resistor(0.1));
        bridges.bridge(primaryDivNode, nodes[2], ep);

        bridges.bridge(nodes[1], secondaryDivNode, ElectricalProperties.resistor(0.1));
        bridges.bridge(secondaryDivNode, nodes[3], ep.getOtherProperties());
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 1/16f;
    }

    double prevPrimaryVoltage = 0;
    double prevSecondaryVoltage = 0;
    double prevPower = 0;
    double power = 0;

    @Override
    public void postTick(SimulationResults results) {
        if (!level.isLoaded(pos))
            return;

        if (blown) {
            power = 0;
            rmsPrimaryVoltages.add(0);
            rmsSecondaryVoltages.add(0);
        } else {
            InWorldNode primaryDivNode = new InWorldNode(nodes[0].id() + 1_000_000, nodes[0].sourcePos());
            InWorldNode secondaryDivNode = new InWorldNode(nodes[0].id() + 1_000_001, nodes[0].sourcePos());

            double vdp = inverted ? results.getVoltageAt(nodes[1], nodes[3]) : results.getVoltageAt(nodes[0], nodes[2]);
            double sdp = inverted ? results.getVoltageAt(nodes[0], nodes[2]) : results.getVoltageAt(nodes[1], nodes[3]);
            double ip = (inverted ? results.getVoltageAt(nodes[1], secondaryDivNode) : results.getVoltageAt(nodes[0], primaryDivNode)) / 0.1;
            power = ip * vdp;

            rmsPrimaryVoltages.add(vdp);
            rmsSecondaryVoltages.add(sdp);
        }
        primaryVoltage = rmsPrimaryVoltages.get();
        secondaryVoltage = rmsSecondaryVoltages.get();

        if (Math.abs(prevPrimaryVoltage - primaryVoltage) > 1 ||
                Math.abs(prevSecondaryVoltage - secondaryVoltage) > 1 ||
                Math.abs(prevPower - power) > 1) {
            if (level.getBlockEntity(pos) instanceof ElectricalPanelBlockEntity be)
                be.sendData();
            prevPrimaryVoltage = primaryVoltage;
            prevSecondaryVoltage = secondaryVoltage;
            prevPower = power;
        }

        double load = Math.abs(power) / CEEConfigs.server().powerValues.miniatureTransformerMaxPower.get();

        temp = ElectricalDevice.updateTemp(temp, (float) load * 1000);

        if (this.temp > 30_000) {
            if (level.isLoaded(pos)) {
                Vec3 center = getCenter();
                CatnipServices.NETWORK.sendToClientsAround((ServerLevel) level, center, 40, new SendSparkPacket(center, SendSparkPacket.SparkSize.SMALL));
                ((ServerLevel) level).sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 0, 0, 0,0, 0);
            }
            temp = 0;
            blown = true;
        } else if (this.temp > 29_000) {
            Vec3 center = getCenter();
            if (level.random.nextFloat() > 0.8f)
                ((ServerLevel)level).sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 5, 0.1, 0.1, 0.1, 0);

        }
    }

    @Override
    public ItemInteractionResult onInteract(ItemStack stack, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!AllItems.WRENCH.isIn(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> this.displayScreen(player));
        return ItemInteractionResult.SUCCESS;
    }

    @OnlyIn(Dist.CLIENT)
    protected void displayScreen(Player player) {
        if (player instanceof LocalPlayer)
            ScreenOpener.open(new TransformerScreen(this));
    }

    @Override
    public boolean addToGoggleTooltip(ElectricalPanelBlockEntity be, List<Component> tooltip, boolean isPlayerSneaking) {
        if (label != null)
            CEELang.builder()
                    .text(label)
                    .forGoggles(tooltip);
        else
            CreateLang.translate("gui.gauge.info_header")
                    .forGoggles(tooltip);

        CEELang.builder()
                .translate("gui.goggles.electric_stats")
                .forGoggles(tooltip);
        CEELang.builder()
                .translate("gui.goggles.primary_voltage")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        CEELang.builder()
                .text(LangNumberFormat.format(Math.round(Math.abs(primaryVoltage))))
                .translate("generic.volts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        CEELang.builder()
                .translate("gui.goggles.secondary_voltage")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        CEELang.builder()
                .text(LangNumberFormat.format(Math.round(Math.abs(secondaryVoltage))))
                .translate("generic.volts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        Lang.builder(CreateElectroEnergetics.ID)
                .translate("gui.goggles.power")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        Lang.builder(CreateElectroEnergetics.ID)
                .text(LangNumberFormat.format(Math.round(power)))
                .translate("generic.watts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        return true;
    }


    @Override
    public void read(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        primaryTurns = Mth.clamp(tag.getInt("PrimaryTurns"), 1, 240);
        secondaryTurns = Mth.clamp(tag.getInt("SecondaryTurns"), 1, 240);
        primaryVoltage = tag.getDouble("PrimaryVoltage");
        secondaryVoltage = tag.getDouble("SecondaryVoltage");
        power = tag.getDouble("Power");
        inverted = tag.getBoolean("Inverted");
        blown = tag.getBoolean("Blown");
        temp = tag.getFloat("Temp1");
        if (clientPacket)
            return;

        this.rmsPrimaryVoltages = new RMSHolder(2);
        this.rmsSecondaryVoltages = new RMSHolder(2);
        this.rmsPrimaryVoltages.read(tag, "PrimaryVoltages");
        this.rmsSecondaryVoltages.read(tag, "SecondaryVoltages");
    }

    @Override
    public List<ItemStack> getDrops() {
        if (blown)
            return new ArrayList<>();
        return super.getDrops();
    }

    @Override
    public void write(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        tag.putDouble("PrimaryTurns", primaryTurns);
        tag.putDouble("SecondaryTurns", secondaryTurns);
        tag.putDouble("PrimaryVoltage", primaryVoltage);
        tag.putDouble("SecondaryVoltage", secondaryVoltage);
        tag.putDouble("Power", power);
        tag.putFloat("Temp1", temp);
        if (inverted)
            tag.putBoolean("Inverted", true);
        if (blown)
            tag.putBoolean("Blown", true);
        if (clientPacket)
            return;

        this.rmsPrimaryVoltages.write(tag, "PrimaryVoltages");
        this.rmsSecondaryVoltages.write(tag, "SecondaryVoltages");
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return (id == 0 || id == 2) ^ inverted ?
                CEELang.nodeLabel("primary"):  CEELang.nodeLabel("secondary");
    }
}
