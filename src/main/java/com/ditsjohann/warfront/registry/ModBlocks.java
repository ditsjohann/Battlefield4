package com.ditsjohann.warfront.registry;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, Warfront.MODID);

    public static final RegistryObject<Block> WAR_TABLE = REGISTER.register("war_table",
            () -> new WarTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion()));
    /** Quick fortification. Stops bullets, but explosions tear it apart. */
    public static final RegistryObject<Block> SANDBAG = REGISTER.register("sandbag",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.8f, 2.0f).sound(SoundType.WOOL)));
    public static final RegistryObject<Block> RESUPPLY_CRATE = REGISTER.register("resupply_crate",
            () -> new ResupplyCrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5f, 1200f).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> CAPTURE_POINT = REGISTER.register("capture_point",
            () -> new CapturePointBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1f, 3600000f).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> HQ_BEACON = REGISTER.register("hq_beacon",
            () -> new HqBeaconBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1f, 3600000f).sound(SoundType.METAL).lightLevel(s -> 12)));
    /** Tower material: explosions can't chew it, only the scripted Levolution collapse can. */
    public static final RegistryObject<Block> REINFORCED_CONCRETE = REGISTER.register("reinforced_concrete",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(30f, 1200f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> TOWER_CORE = REGISTER.register("tower_core",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(-1f, 3600000f).sound(SoundType.METAL).lightLevel(s -> 8)));
    public static final RegistryObject<Block> VEHICLE_PAD = REGISTER.register("vehicle_pad",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(-1f, 3600000f).sound(SoundType.METAL)));

    private ModBlocks() {}
}
