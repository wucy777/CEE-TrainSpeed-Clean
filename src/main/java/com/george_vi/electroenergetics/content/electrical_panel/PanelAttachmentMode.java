package com.george_vi.electroenergetics.content.electrical_panel;

import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot.*;

public interface PanelAttachmentMode {
    PanelAttachmentMode FULL_NONE = builder().addSlot(FULL_SLOT).build();

    PanelAttachmentMode FULL_SINGLE = builder()
            .addSlot(FULL_SLOT).node(3, 16).build();

    PanelAttachmentMode FULL_DOUBLE = builder()
            .addSlot(FULL_SLOT)
                .node(1, 5)
                .node(14, 18).build();

    PanelAttachmentMode FULL_TRIPLE = builder()
            .addSlot(FULL_SLOT)
                .node(1, 3, 5)
                .node(14, 16, 18).build();

    PanelAttachmentMode FULL_QUAD = builder()
            .addSlot(FULL_SLOT)
                .node(0, 2, 4, 6)
                .node(13, 15, 17, 19).build();

    PanelAttachmentMode QUARTER_NONE = builder()
            .addSlot(QUARTER_CENTER).addSlot(QUARTER_LEFT_LOWER).addSlot(QUARTER_LEFT_UPPER)
            .addSlot(QUARTER_RIGHT_LOWER).addSlot(QUARTER_RIGHT_UPPER)
            .build();

    PanelAttachmentMode HALF_NONE = builder()
            .addSlot(HALF_LEFT).addSlot(HALF_UPPER)
            .addSlot(HALF_RIGHT).addSlot(HALF_LOWER)
            .build();

    PanelAttachmentMode HALF_VERTICAL = builder()
            .addSlot(HALF_RIGHT).node(20, 22)
            .addSlot(HALF_LEFT).node(21, 23).build();

    PanelAttachmentMode HALF_HORIZONTAL = builder()
            .addSlot(HALF_LOWER).node(22, 23)
            .addSlot(HALF_UPPER).node(20, 21).build();

    PanelAttachmentMode HALF = union(HALF_HORIZONTAL, HALF_VERTICAL);

    PanelAttachmentMode THIRD = builder()
            .addSlot(THIRD_RIGHT).node(7, 10)
            .addSlot(THIRD_CENTERED).node(8, 11)
            .addSlot(THIRD_LEFT).node(9, 12).build();

    PanelAttachmentMode THIRD_NONE = builder()
            .addSlot(THIRD_RIGHT)
            .addSlot(THIRD_CENTERED)
            .addSlot(THIRD_LEFT).build();

    PanelAttachmentMode HALF_OR_THIRD = union(HALF, THIRD);

    PanelAttachmentMode HALF_OR_THIRD_NONE = union(HALF_NONE, THIRD_NONE);

    PanelAttachmentMode SIXTH = builder()
            .addSlot(THIRD_RIGHT_BOTTOM).node(131, 31)
            .addSlot(THIRD_CENTERED_BOTTOM).node(135, 35)
            .addSlot(THIRD_LEFT_BOTTOM).node(139, 39)
            .addSlot(THIRD_RIGHT_TOP).node(291, 191)
            .addSlot(THIRD_CENTERED_TOP).node(295, 195)
            .addSlot(THIRD_LEFT_TOP).node(299, 199).build();

    PanelAttachmentMode SIXTH_NONE = builder()
            .addSlot(THIRD_RIGHT_BOTTOM)
            .addSlot(THIRD_CENTERED_BOTTOM)
            .addSlot(THIRD_LEFT_BOTTOM)
            .addSlot(THIRD_RIGHT_TOP)
            .addSlot(THIRD_CENTERED_TOP)
            .addSlot(THIRD_LEFT_TOP).build();

    PanelAttachmentMode HALF_OR_THIRD_OR_SIXTH = union(HALF_OR_THIRD, SIXTH);

    PanelAttachmentMode HALF_OR_THIRD_OR_SIXTH_NONE = union(HALF_OR_THIRD_NONE, SIXTH_NONE);

    PanelAttachmentMode SIXTH_QUAD = builder()
            .addSlot(THIRD_RIGHT_BOTTOM).node(130, 30, 132, 32)
            .addSlot(THIRD_CENTERED_BOTTOM).node(134, 34, 136, 36)
            .addSlot(THIRD_LEFT_BOTTOM).node(138, 38, 140, 40)
            .addSlot(THIRD_RIGHT_TOP).node(290, 190, 292, 192)
            .addSlot(THIRD_CENTERED_TOP).node(294, 194, 296, 196)
            .addSlot(THIRD_LEFT_TOP).node(298, 198, 300, 200).build();


    static PanelAttachmentMode union(PanelAttachmentMode m1, PanelAttachmentMode m2) {
        Map<ElectricalPanelSlot, IntList> slots = new HashMap<>(m1.possibleSlots());
        slots.putAll(m2.possibleSlots());
        return new Normal(slots);
    }

    private static @NotNull Builder builder() {
        return new Builder();
    }


    Map<ElectricalPanelSlot, IntList> possibleSlots();

    default InWorldNode[] getNodesFor(BlockPos pos, ElectricalPanelSlot slot) {
        IntList nodes = possibleSlots().get(slot);
        if (nodes == null)
            return new InWorldNode[0];

        InWorldNode[] out = new InWorldNode[nodes.size()];
        for (int i = 0; i < nodes.size(); i++)
            out[i] = new InWorldNode(nodes.getInt(i), pos);

        return out;
    }

    @Nullable
    default ElectricalPanelSlot getSlot(Direction facing, Vec3 clickPosition, PanelAttachment[] existingAttachments) {
        Vec3 rotatedClickPos = VecHelper.rotateCentered(clickPosition, facing.toYRot() + 180, Direction.Axis.Y);
        double x = rotatedClickPos.x;
        double y = rotatedClickPos.y;
        if (existingAttachments[FULL_SLOT.ordinal()] != null)
            return null;

        ElectricalPanelSlot closestSlot = null;
        double closestDistanceSqr = Double.MAX_VALUE;
        SlotLoop:
        for (ElectricalPanelSlot possibleSlot : possibleSlots().keySet()) {
            for (PanelAttachment attachment : existingAttachments)
                if (attachment != null && attachment.slot.shape.intersects(possibleSlot.shape))
                    continue SlotLoop;

            if (possibleSlot.shape.minX < x && possibleSlot.shape.maxX > x &&
                    possibleSlot.shape.minY < y && possibleSlot.shape.maxY > y) {

                double distanceSqr = (x - possibleSlot.center.x) * (x - possibleSlot.center.x) + (y - possibleSlot.center.y) * (y - possibleSlot.center.y);
                if (distanceSqr < closestDistanceSqr) {
                    closestDistanceSqr = distanceSqr;
                    closestSlot = possibleSlot;
                }
            }
        }
        return closestSlot;
    }

    class Normal implements PanelAttachmentMode {
        private final Map<ElectricalPanelSlot, IntList> nodes;

        public Normal(Map<ElectricalPanelSlot, IntList> nodes) {
            this.nodes = nodes;
        }

        @Override
        public Map<ElectricalPanelSlot, IntList> possibleSlots() {
            return nodes;
        }
    }

    class Builder {
        private ElectricalPanelSlot currentSlot;
        private IntList currentNodes;
        private final Map<ElectricalPanelSlot, IntList> nodes = new HashMap<>();

        public Builder() {

        }

        public Builder addSlot(ElectricalPanelSlot slot) {
            if (currentNodes != null && currentSlot != null) {
                nodes.put(currentSlot, currentNodes);
            }

            currentSlot = slot;
            currentNodes = new IntArrayList();
            return this;
        }

        public Builder node(int id) {
            if (currentNodes == null || currentSlot == null)
                throw new IllegalStateException("Tried to add a node without specifying the slot");

            currentNodes.add(id);
            return this;
        }

        public Builder node(int i1, int i2) {
            return node(i1).node(i2);
        }

        public Builder node(int i1, int i2, int i3) {
            return node(i1).node(i2).node(i3);
        }
        public Builder node(int i1, int i2, int i3, int i4) {
            return node(i1).node(i2).node(i3).node(i4);
        }

        public PanelAttachmentMode build() {
            if (currentNodes != null && currentSlot != null) {
                nodes.put(currentSlot, currentNodes);
            }
            return new Normal(nodes);
        }
    }
}
