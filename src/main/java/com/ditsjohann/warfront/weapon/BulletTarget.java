package com.ditsjohann.warfront.weapon;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Non-living things that react to small-arms fire (armoured vehicles, placed gadgets). */
public interface BulletTarget {
    void onBulletHit(@Nullable Entity shooter, float damage);
}
