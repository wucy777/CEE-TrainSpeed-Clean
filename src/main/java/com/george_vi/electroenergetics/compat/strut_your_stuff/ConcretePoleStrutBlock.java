package com.george_vi.electroenergetics.compat.strut_your_stuff;

import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.block.StrutBlock;
import com.cake.struts.content.block.StrutBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ConcretePoleStrutBlock extends StrutBlock {
    public ConcretePoleStrutBlock(Properties properties, StrutModelType modelType) {
        super(properties, modelType);
    }

    @Override
    protected BlockEntityType<? extends StrutBlockEntity> getStrutBlockEntityType() {
        return StrutYourStuffRegistryEntries.CONCRETE_POLE_STRUT_BLOCK_ENTITY.get();
    }
}
