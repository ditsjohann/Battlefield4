package com.ditsjohann.warfront.registry;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.block.CapturePointBlockEntity;
import com.ditsjohann.warfront.block.HqBeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Warfront.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CapturePointBlockEntity>> CAPTURE_POINT = REGISTER.register("capture_point",
            () -> BlockEntityType.Builder.of(CapturePointBlockEntity::new, ModBlocks.CAPTURE_POINT.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<HqBeaconBlockEntity>> HQ_BEACON = REGISTER.register("hq_beacon",
            () -> BlockEntityType.Builder.of(HqBeaconBlockEntity::new, ModBlocks.HQ_BEACON.get()).build(null));

    private ModBlockEntities() {}
}
