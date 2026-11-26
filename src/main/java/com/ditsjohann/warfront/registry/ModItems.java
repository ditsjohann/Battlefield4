package com.ditsjohann.warfront.registry;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.item.*;
import com.ditsjohann.warfront.weapon.GunItem;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, Warfront.MODID);

    private static final Map<GunType, RegistryObject<GunItem>> GUNS = new EnumMap<>(GunType.class);

    static {
        for (GunType t : GunType.values()) GUNS.put(t, REGISTER.register(t.id, () -> new GunItem(t)));
    }

    public static final RegistryObject<Item> FRAG_GRENADE = REGISTER.register("frag_grenade", FragGrenadeItem::new);
    public static final RegistryObject<Item> MEDIC_BAG = REGISTER.register("medic_bag", () -> new SupplyBagItem(false));
    public static final RegistryObject<Item> AMMO_BAG = REGISTER.register("ammo_bag", () -> new SupplyBagItem(true));
    public static final RegistryObject<Item> DEFIBRILLATOR = REGISTER.register("defibrillator", DefibrillatorItem::new);
    public static final RegistryObject<Item> ROCKET_LAUNCHER = REGISTER.register("rocket_launcher", RocketLauncherItem::new);
    public static final RegistryObject<Item> REPAIR_TOOL = REGISTER.register("repair_tool", RepairToolItem::new);
    public static final RegistryObject<Item> C4 = REGISTER.register("c4", C4Item::new);
    public static final RegistryObject<Item> SPAWN_BEACON = REGISTER.register("spawn_beacon", SpawnBeaconItem::new);
    public static final RegistryObject<Item> FIELD_BINOCULARS = REGISTER.register("field_binoculars", BinocularsItem::new);
    public static final RegistryObject<Item> TANK_DEPLOYER = REGISTER.register("tank_deployer", TankDeployerItem::new);
    public static final RegistryObject<Item> DOG_TAG = REGISTER.register("dog_tag", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> SOLDIER_SPAWN_EGG = REGISTER.register("soldier_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SOLDIER, 0x4B5A32, 0xA48E64, new Item.Properties()));

    public static final RegistryObject<Item> WAR_TABLE = block("war_table", ModBlocks.WAR_TABLE);
    public static final RegistryObject<Item> SANDBAG = block("sandbag", ModBlocks.SANDBAG);
    public static final RegistryObject<Item> RESUPPLY_CRATE = block("resupply_crate", ModBlocks.RESUPPLY_CRATE);
    public static final RegistryObject<Item> REINFORCED_CONCRETE = block("reinforced_concrete", ModBlocks.REINFORCED_CONCRETE);
    public static final RegistryObject<Item> CAPTURE_POINT = block("capture_point", ModBlocks.CAPTURE_POINT);

    private static RegistryObject<Item> block(String name, RegistryObject<? extends net.minecraft.world.level.block.Block> b) {
        return REGISTER.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
    }

    public static GunItem gun(GunType t) {
        return GUNS.get(t).get();
    }

    private ModItems() {}
}
