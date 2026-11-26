package com.ditsjohann.warfront.registry;

import com.ditsjohann.warfront.Warfront;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Data-driven damage types (see data/warfront/damage_type). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> BULLET = key("bullet");
    public static final ResourceKey<DamageType> HEADSHOT = key("headshot");
    public static final ResourceKey<DamageType> BLEED_OUT = key("bleed_out");
    public static final ResourceKey<DamageType> CRUSHED = key("crushed");

    private static ResourceKey<DamageType> key(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Warfront.id(n));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity cause) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, cause);
    }

    private ModDamageTypes() {}
}
