package com.ditsjohann.warfront.registry;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Warfront.MODID);

    public static final RegistryObject<EntityType<SoldierEntity>> SOLDIER = REGISTER.register("soldier",
            () -> EntityType.Builder.<SoldierEntity>of(SoldierEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f)
                    .clientTrackingRange(12).updateInterval(2).build("soldier"));
    public static final RegistryObject<EntityType<TankEntity>> TANK = REGISTER.register("tank",
            () -> EntityType.Builder.<TankEntity>of(TankEntity::new, MobCategory.MISC).sized(3.4f, 2.3f)
                    .clientTrackingRange(16).updateInterval(1).build("tank"));
    public static final RegistryObject<EntityType<FragGrenadeEntity>> FRAG_GRENADE = REGISTER.register("frag_grenade",
            () -> EntityType.Builder.<FragGrenadeEntity>of(FragGrenadeEntity::new, MobCategory.MISC).sized(0.25f, 0.25f)
                    .clientTrackingRange(8).updateInterval(1).build("frag_grenade"));
    public static final RegistryObject<EntityType<RocketEntity>> ROCKET = REGISTER.register("rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(0.3f, 0.3f)
                    .clientTrackingRange(12).updateInterval(1).build("rocket"));
    public static final RegistryObject<EntityType<C4Entity>> C4 = REGISTER.register("c4",
            () -> EntityType.Builder.<C4Entity>of(C4Entity::new, MobCategory.MISC).sized(0.4f, 0.2f)
                    .clientTrackingRange(8).updateInterval(5).build("c4"));
    public static final RegistryObject<EntityType<SupplyBagEntity>> SUPPLY_BAG = REGISTER.register("supply_bag",
            () -> EntityType.Builder.<SupplyBagEntity>of(SupplyBagEntity::new, MobCategory.MISC).sized(0.5f, 0.4f)
                    .clientTrackingRange(8).updateInterval(4).build("supply_bag"));
    public static final RegistryObject<EntityType<SpawnBeaconEntity>> SPAWN_BEACON = REGISTER.register("spawn_beacon",
            () -> EntityType.Builder.<SpawnBeaconEntity>of(SpawnBeaconEntity::new, MobCategory.MISC).sized(0.4f, 0.9f)
                    .clientTrackingRange(8).updateInterval(10).build("spawn_beacon"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SOLDIER.get(), SoldierEntity.createAttributes().build());
    }

    private ModEntities() {}
}
