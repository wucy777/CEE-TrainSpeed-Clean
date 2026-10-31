package com.george_vi.electroenergetics.compat.strut_your_stuff;

import com.cake.struts.compat.flywheel.StrutFlywheelVisual;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.block.StrutBlockEntity;
import com.cake.struts.content.block.StrutBlockEntityRenderer;
import com.cake.struts.content.block.StrutBlockItem;
import com.george_vi.electroenergetics.CreateElectroEnergetics;
import com.simibubi.create.foundation.item.ItemDescription;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.state.BlockBehaviour;

import static com.george_vi.electroenergetics.CreateElectroEnergetics.REGISTRATE;

public class StrutYourStuffRegistryEntries {
    public static final StrutModelType CONCRETE_POLE_MODEL =
            new StrutModelType(CreateElectroEnergetics.rl("block/strut/concrete_pole"),
                    CreateElectroEnergetics.rl("block/concrete_pole_strut_end"), 8, 8,
                    4, () -> RenderType::cutout);


    public static final BlockEntry<ConcretePoleStrutBlock> CONCRETE_POLE_STRUT = REGISTRATE.block("concrete_pole_strut",
                    props -> new ConcretePoleStrutBlock(props, CONCRETE_POLE_MODEL))
            .properties(p -> p.strength(3f, 6f))
            .properties(BlockBehaviour.Properties::noOcclusion)
            .blockstate((c, p) -> p.directionalBlock(
                    c.get(),
                    (state) -> p.models().getExistingFile(
                            CreateElectroEnergetics.rl("block/strut/concrete_pole_strut_attachment"))
            ))
            .onRegisterAfter(
                    Registries.ITEM,
                    v -> ItemDescription.useKey(v, "block.bits_n_bobs.girder_strut")
            )
            .item(StrutBlockItem::new)
            .model((c, p) ->
                    p.withExistingParent(c.getName(), CreateElectroEnergetics.rl("block/strut/concrete_pole_item")))
            .build()
            .register();

    public static final BlockEntityEntry<StrutBlockEntity> CONCRETE_POLE_STRUT_BLOCK_ENTITY = REGISTRATE
            .blockEntity("concrete_pole_strut", StrutBlockEntity::new)
            .visual(() -> StrutFlywheelVisual::new, false)
            .validBlocks(CONCRETE_POLE_STRUT)
            .renderer(() -> StrutBlockEntityRenderer::new)
            .register();

    public static void register() {

    }

    public static void fillCreativeTab(CreativeModeTab.Output output) {
        output.accept(CONCRETE_POLE_STRUT.get());
    }
}
