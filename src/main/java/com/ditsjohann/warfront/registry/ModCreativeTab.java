package com.ditsjohann.warfront.registry;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Warfront.MODID);

    public static final RegistryObject<CreativeModeTab> TAB = REGISTER.register("warfront", () -> CreativeModeTab.builder()
            .title(Component.literal("WARFRONT"))
            .icon(() -> new ItemStack(ModItems.gun(GunType.AR17)))
            .displayItems((params, out) -> {
                out.accept(ModItems.WAR_TABLE.get());
                for (GunType t : GunType.values()) out.accept(ModItems.gun(t));
                out.accept(ModItems.FRAG_GRENADE.get());
                out.accept(ModItems.MEDIC_BAG.get());
                out.accept(ModItems.DEFIBRILLATOR.get());
                out.accept(ModItems.ROCKET_LAUNCHER.get());
                out.accept(ModItems.REPAIR_TOOL.get());
                out.accept(ModItems.AMMO_BAG.get());
                out.accept(ModItems.C4.get());
                out.accept(ModItems.SPAWN_BEACON.get());
                out.accept(ModItems.FIELD_BINOCULARS.get());
                out.accept(ModItems.TANK_DEPLOYER.get());
                out.accept(ModItems.SOLDIER_SPAWN_EGG.get());
                out.accept(ModItems.SANDBAG.get());
                out.accept(ModItems.RESUPPLY_CRATE.get());
                out.accept(ModItems.REINFORCED_CONCRETE.get());
                out.accept(ModItems.DOG_TAG.get());
            }).build());

    private ModCreativeTab() {}
}
