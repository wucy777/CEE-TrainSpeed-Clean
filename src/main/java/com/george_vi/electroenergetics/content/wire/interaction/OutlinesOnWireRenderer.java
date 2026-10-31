package com.george_vi.electroenergetics.content.wire.interaction;


import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.QuadraticWireHelper;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class OutlinesOnWireRenderer {
    public static void renderCurrent(float amperage) {
        ElectricPropertiesOverlay.INSTANCE.setAmmeter(Math.abs(amperage));
        NodeConnectionPoint point = WireInteractionHandler.targetedPoint;
        if (point == null || Math.abs(amperage) < 1e-2d)
            return;
        Level level = Minecraft.getInstance().level;
        InWorldNode node1 = point.node1();
        InWorldNode node2 = point.node2();
        WireData wireData = WireRenderer.getConnectionData(new InWorldNodeConnection(node1, node2));
        Vec3 pos1 = node1.getPositionNoSable(level);
        Vec3 pos2 = node2.getPositionNoSable(level);
        if (pos1 == null || pos2 == null) {
            return;
        }
        Positions pos = new Positions(pos1, pos2);
        double distance = pos.getDistance();
        if (distance == 0)
            return;
        for (int i = 0; i < 4; i++) {
            int ticks = AnimationTickHolder.getTicks();
            int page = (int) Math.floor((ticks + 6 * i) / 24d);
            int dotID = page << 2 + i;
            float progress = ((ticks + 6 * i) % 24) / 24f;

            float pointOnWire = (float) (((point.point() * distance) + ((amperage > 0 ? progress : 1.0 - progress) * 1.2f) - 0.6f) / distance);
            OutlinerExt.chaseAABBOnWire("cee_clamp_meter_current_visualization_" + dotID, AABB.ofSize(Vec3.ZERO, 0.01, 0.01, 0.01),
                            pos, pointOnWire > 1 ? 1 : pointOnWire < 0 ? 0 : pointOnWire, wireData == null ? 0 : wireData.getSag(distance))
                    .lineWidth(0.15f * Math.min(1, progress * 4))
                    .colored(Color.SPRING_GREEN)
                    .disableLineNormals();
        }
    }

    public static class OutlinerExt {
        static Map<String, WireOutline> wireOutlines = Collections.synchronizedMap(new HashMap<>());

        public static Outline.OutlineParams chaseAABBOnWire(Object slot, AABB bb, Positions pos, float point, float sag) {
            Outliner instance = Outliner.getInstance();
            Map<Object, Outliner.OutlineEntry> outlines = instance.getOutlines();
            if (!outlines.containsKey(slot) || !(outlines.get(slot).getOutline() instanceof AABBOutline)) {
                instance.showOutline(slot, new ChasingAABBOnWireOutline(bb, pos.createPosOnWire(point, sag)));
            }

            ChasingAABBOnWireOutline outline = (ChasingAABBOnWireOutline) outlines.get(slot).getOutline();
            instance.keep(slot);

            outline.pos.setPos(pos);
            outline.pos.setArguments(point, sag);
            outline.setSizeFromAABB(bb);
            return outline.getParams();
        }

        public static void showWireOutline(String slot, Positions pos, float sag, float detail,
                                           Function<Outline.OutlineParams, Outline.OutlineParams> processor) {
            if (!wireOutlines.containsKey(slot)) {
                wireOutlines.put(slot, new WireOutline(slot));
            }
            WireOutline wire = wireOutlines.get(slot);
            wire.ttl = 5; // keep it alive during using it

            wire.show(pos, sag, detail, processor);
        }

        @OnlyIn(Dist.CLIENT)
        public static void tick() { // clear
            ArrayList<String> toBeRemoved = new ArrayList<>();
            for (Map.Entry<String, WireOutline> entry : wireOutlines.entrySet()) {
                int ttl = entry.getValue().ttl--;
                if (ttl <= 0) toBeRemoved.add(entry.getKey());
            }
            toBeRemoved.forEach((slot) -> {
                wireOutlines.get(slot).clear();
                wireOutlines.remove(slot);
            });
        }
    }

    public static class Positions { // Position Tools
        Vec3 pos1, pos2;
        SubLevelAccess sl1, sl2; // SLA cache

        public Positions(@NotNull Vec3 pos1, @NotNull Vec3 pos2) {
            setPos(pos1, pos2);
        }

        public static Vec3 toPositionWithSable(Vec3 pos, SubLevelAccess subLevelAccess, float pt) {
            if (subLevelAccess == null || pos == null) {
                return pos;
            }
            Pose3dc logicalPose = subLevelAccess.logicalPose();
            Pose3dc lastPose = subLevelAccess.lastPose();

            Vec3 lastPos = lastPose.transformPosition(pos);
            Vec3 logicalPos = logicalPose.transformPosition(pos);

            return VecHelper.lerp(pt, lastPos, logicalPos);
        }

        public static Vec3 toPositionWithSable(Vec3 pos, SubLevelAccess subLevelAccess) {
            if (subLevelAccess == null || pos == null) {
                return pos;
            }
            return subLevelAccess.logicalPose().transformPosition(pos);
        }

        public void setPos(@NotNull Vec3 pos1, @NotNull Vec3 pos2) {
            setPos1(pos1);
            setPos2(pos2);
        }

        protected void setPos1(@NotNull Vec3 pos) {
            Level level = Minecraft.getInstance().level;
            sl1 = SableCompanion.INSTANCE.getContaining(level, pos);
            pos1 = pos;
        }

        protected void setPos2(@NotNull Vec3 pos) {
            Level level = Minecraft.getInstance().level;
            sl2 = SableCompanion.INSTANCE.getContaining(level, pos);
            pos2 = pos;
        }

        public Vec3 getPos1Sable() {
            return toPositionWithSable(pos1, sl1);
        }

        public Vec3 getPos2Sable() {
            return toPositionWithSable(pos2, sl2);
        }

        public double getDistance() {
            return getPos1Sable().distanceTo(getPos2Sable());
        }

        public Vec3 getPos1Sable(float pt) {
            return toPositionWithSable(pos1, sl1, pt);
        }

        public Vec3 getPos2Sable(float pt) {
            return toPositionWithSable(pos2, sl2, pt);
        }

        public double getDistance(float pt) {
            return getPos1Sable(pt).distanceTo(getPos2Sable(pt)); // may need it in the future
        }

        public PosOnWire createPosOnWire(float targetPoint, float sag) {
            return new PosOnWire(pos1, pos2, targetPoint, sag);
        }
    }

    public static class PosOnWire extends Positions {
        float targetPoint, prevPoint, sag;
        private boolean single = false;

        public PosOnWire() {
            super(Vec3.ZERO, Vec3.ZERO);
        }

        public PosOnWire(@NotNull Vec3 pos1) { // may need it in the future
            super(pos1, pos1);
            setArguments(0, 0);
            tick();
            single = true;
        }

        public PosOnWire(@NotNull Vec3 pos1, @NotNull Vec3 pos2, float targetPoint, float sag) {
            super(pos1, pos2);
            setArguments(targetPoint, sag);
            tick();
        }

        public void setArguments(float targetPoint, float sag) {
            if (single) return;
            this.targetPoint = Mth.clamp(targetPoint, 0, 1);
            this.sag = sag;
        }

        public void setPos(Positions pos) {
            super.setPos(pos.pos1, pos.pos2);
            if (pos instanceof PosOnWire) {
                setArguments(((PosOnWire) pos).targetPoint, ((PosOnWire) pos).sag);
                single = ((PosOnWire) pos).single;
            }
        }

        public void setPos(Vec3 pos) {
            setPos1(pos);
            if (!single) {
                setArguments(0, 0);
                single = true;
                tick();
            }
        }

        @Override
        public void setPos(@NotNull Vec3 pos1, @NotNull Vec3 pos2) {
            super.setPos(pos1, pos2);
            single = false;
        }

        public void tick() {
            prevPoint = targetPoint;
            if (single) {
                pos2 = pos1;
                sl2 = sl1;
            }
        }

        public Vec3 getHitPos() { // may need it in the future
            return single ? getPos1Sable() : QuadraticWireHelper.posAt(getPos1Sable(), getPos2Sable(), targetPoint, sag);
        }

        public Vec3 getHitPos(float pt) {
            return single ? getPos2Sable(pt).lerp(getPos1Sable(pt), pt) :
                    QuadraticWireHelper.posAt(getPos1Sable(pt), getPos2Sable(pt), Mth.lerp(pt, prevPoint, targetPoint), sag);
        }
    }

    public static class ChasingAABBOnWireOutline extends AABBOutline {
        protected Vec3 bbSize;
        protected PosOnWire pos;

        public ChasingAABBOnWireOutline(AABB bb, PosOnWire pos) {
            super(AABB.ofSize(Vec3.ZERO, 0.01, 0.01, 0.01)); // avoid to be detected by sable mixin
            setSizeFromAABB(bb);
            this.pos = pos;
        }

        public void setSizeFromAABB(AABB bb) {
            bbSize = bb.getMaxPosition().subtract(bb.getMinPosition());
        }

        @Override
        public void render(@NotNull PoseStack ms, @NotNull SuperRenderTypeBuffer buffer, @NotNull Vec3 camera, float pt) {
            // avoid offsets caused by sublevel
            setBounds(AABB.ofSize(pos.getHitPos(pt), bbSize.x, bbSize.y, bbSize.z));
            super.render(ms, buffer, camera, pt);
        }

        @Override
        public void tick() {
            pos.tick();
        }
    }

    public static class WireOutline {
        private static final int SECTION_STRETCHING_BUFFER = 8;// I think it's enough to deal with stretching
        public final String slot;
        protected int ttl = 5;// Life Count
        private float sag, detail, pt = -1; // keep it updated
        private Positions pos;
        private List<Vec3> cachedPoints; // cache for same frame
        private int sectionCount = 0; // count for resource clear

        public WireOutline(String slot) {
            this.slot = slot;
        }

        protected void clear() {
            Outliner instance = Outliner.getInstance();
            for (int i = 0; i < sectionCount; i++) {
                instance.remove(slot + i);
            }
        }

        public void show(Positions pos, float sag, float detail, Function<Outline.OutlineParams, Outline.OutlineParams> processor) {
            this.sag = sag;
            this.detail = detail;
            this.pos = pos;
            List<Vec3> points = QuadraticWireHelper.cablePoints(pos.getPos1Sable(), pos.getPos2Sable(), sag, detail);
            for (int i = 0; i < points.size() + SECTION_STRETCHING_BUFFER; i++) {
                Outline.OutlineParams params = addSection(slot + i, i);
                if (processor != null) {
                    processor.apply(params); // apply styles
                }
            }
            sectionCount = Math.max(sectionCount, points.size() + SECTION_STRETCHING_BUFFER);
        }

        private Outline.OutlineParams addSection(Object slot, int index) {
            Outliner instance = Outliner.getInstance();
            Map<Object, Outliner.OutlineEntry> outlines = instance.getOutlines();
            if (!outlines.containsKey(slot)) {
                WireOutlineSection outline = new WireOutlineSection(index);
                instance.showOutline(slot, outline);
            }
            Outliner.OutlineEntry entry = outlines.get(slot);
            instance.keep(slot);
            return entry.getOutline().getParams();
        }

        private Vec3 getPoint(int index, float pt) {
            if (Float.compare(pt, this.pt) != 0) {
                cachedPoints = QuadraticWireHelper.cablePoints(pos.getPos1Sable(pt), pos.getPos2Sable(pt), sag, detail);
                cachedPoints.add(pos.pos2);
                this.pt = pt;
            }
            return index < cachedPoints.size() ? cachedPoints.get(index) : null;
        }

        public class WireOutlineSection extends LineOutline {
            public final int index;

            public WireOutlineSection(int index) {
                this.index = index;
            }

            @Override
            public void render(@NotNull PoseStack ms, @NotNull SuperRenderTypeBuffer buffer, @NotNull Vec3 camera, float pt) {
                Vec3 pos1 = getPoint(index, pt);
                Vec3 pos2 = getPoint(index + 1, pt);
                if (pos1 == null || pos2 == null) {
                    return;
                }
                set(pos1, pos2);
                super.render(ms, buffer, camera, pt);
            }
        }
    }
}
