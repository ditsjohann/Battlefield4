package com.ditsjohann.warfront.registry;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.weapon.Loadout;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Item data components (1.21 replacement for stack NBT). */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTER = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Warfront.MODID);

    /** Rounds in the magazine. */
    public static final RegistryObject<DataComponentType<Integer>> AMMO = REGISTER.register("ammo",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** Spare rounds carried for this weapon. */
    public static final RegistryObject<DataComponentType<Integer>> RESERVE = REGISTER.register("reserve",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** Game time at which the current reload completes; absent when not reloading. */
    public static final RegistryObject<DataComponentType<Long>> RELOAD_END = REGISTER.register("reload_end",
            () -> DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
    /** Total length of the running reload, for animation progress. */
    public static final RegistryObject<DataComponentType<Integer>> RELOAD_LENGTH = REGISTER.register("reload_length",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** Index into the gun's fire-mode cycle. */
    public static final RegistryObject<DataComponentType<Integer>> FIRE_MODE = REGISTER.register("fire_mode",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final RegistryObject<DataComponentType<Loadout>> LOADOUT = REGISTER.register("loadout",
            () -> DataComponentType.<Loadout>builder().persistent(Loadout.CODEC).networkSynchronized(Loadout.STREAM_CODEC).build());
    /** Marks items handed out by a Conquest kit: removed on redeploy and never dropped. */
    public static final RegistryObject<DataComponentType<Unit>> KIT_ITEM = REGISTER.register("kit_item",
            () -> DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)).build());
    /** Charges/uses left on a gadget (grenades, C4, bags). */
    public static final RegistryObject<DataComponentType<Integer>> CHARGES = REGISTER.register("charges",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** Heat of the repair tool (0..100). */
    public static final RegistryObject<DataComponentType<Integer>> HEAT = REGISTER.register("heat",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    private ModComponents() {}
}
