package com.george_vi.electroenergetics.content.linemans_stick;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.content.clamp_meter.ClampMeterItem;
import com.george_vi.electroenergetics.content.wire.interaction.OutlinesOnWireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class LinemansStickClientHandler {

    @OnlyIn(Dist.CLIENT)
    public static OutlinesOnWireRenderer.PosOnWire linemansStickTarget = new OutlinesOnWireRenderer.PosOnWire();

    @OnlyIn(Dist.CLIENT)
    public static LinemansStickMode currentMode = LinemansStickMode.NONE;

    public static int ticksSinceChange = 0;

    @OnlyIn(Dist.CLIENT)
    public static void tick() {
        linemansStickTarget.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.hitResult == null)
            return;

        ticksSinceChange++;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        ItemStack heldItemStack = player.getMainHandItem();
        boolean isItselfClampMeter = heldItemStack.getItem() instanceof ClampMeterItem &&
                mc.player.getUseItem() == heldItemStack;
        if (heldItemStack.getItem() != CEEItems.LINEMANS_STICK.get() &&
                !isItselfClampMeter) {
            ticksSinceChange = 0;
            currentMode = LinemansStickMode.NONE;
            return;
        }

        ItemStack offHandItemStack = player.getOffhandItem();

        boolean isShiftKeyDown = player.isShiftKeyDown();

        if (currentMode == LinemansStickMode.NONE) {
            if (offHandItemStack.getItem() instanceof ClampMeterItem || isItselfClampMeter) {
                currentMode = LinemansStickMode.CLAMP_METER;
                ticksSinceChange = 0;
            } else if (isShiftKeyDown && offHandItemStack.isEmpty()) {
                currentMode = LinemansStickMode.ATTACHMENT_REMOVAL;
                ticksSinceChange = 0;
            } else if (!isShiftKeyDown && offHandItemStack.is(CEETags.WIRE_ATTACHMENT)) {
                currentMode = LinemansStickMode.ATTACHMENT_INSTALLATION;
                ticksSinceChange = 0;
            }
        }

        else if (currentMode == LinemansStickMode.CLAMP_METER &&
                !(offHandItemStack.getItem() instanceof ClampMeterItem) &&
                !isItselfClampMeter) {
            currentMode = LinemansStickMode.NONE;
            ticksSinceChange = 0;
        } else if (currentMode == LinemansStickMode.ATTACHMENT_REMOVAL &&
                !(isShiftKeyDown && offHandItemStack.isEmpty())) {
            currentMode = LinemansStickMode.NONE;
            ticksSinceChange = 0;
        } else if (currentMode == LinemansStickMode.ATTACHMENT_INSTALLATION &&
                (isShiftKeyDown || !offHandItemStack.is(CEETags.WIRE_ATTACHMENT))) {
            currentMode = LinemansStickMode.NONE;
            ticksSinceChange = 0;
        }

        if (mc.hitResult instanceof BlockHitResult result) {

            BlockPos pos = result.getBlockPos();
            BlockState state = level.getBlockState(pos);

            InWorldNode node = InWorldNode.closestNode(level, pos, state, 0.4f, result.getLocation());

            if (currentMode == LinemansStickMode.NONE && node != null && !isItselfClampMeter) {
                Vec3 nodeLocation = node.getPositionNoSable(level);
                if (nodeLocation != null) {
                    linemansStickTarget.setPos(nodeLocation);
                    return;
                }
            }

            if (WireInteractionHandler.targetedPoint != null) {
                linemansStickTarget.setPos(WireInteractionHandler.targetedPos);
                return;
            }
        } else if (mc.hitResult instanceof EntityHitResult) {

            if (WireInteractionHandler.targetedPoint != null) {
                linemansStickTarget.setPos(WireInteractionHandler.targetedPos);
                return;
            }
        }

        linemansStickTarget.setPos(mc.hitResult.getLocation());
    }
}
