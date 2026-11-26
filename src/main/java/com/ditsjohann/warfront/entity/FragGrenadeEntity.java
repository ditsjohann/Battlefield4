package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.combat.Explosions;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.registry.ModItems;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A bouncing, rolling fragmentation grenade with a timed fuse. */
public class FragGrenadeEntity extends Projectile implements ItemSupplier {
    public static final double GRAVITY = 0.045;
    private int fuse = 60;

    public FragGrenadeEntity(EntityType<? extends FragGrenadeEntity> type, Level level) {
        super(type, level);
    }

    public static void throwFrom(ServerLevel level, @Nullable Entity owner, Vec3 from, Vec3 velocity, int fuse) {
        FragGrenadeEntity g = new FragGrenadeEntity(ModEntities.FRAG_GRENADE.get(), level);
        g.setOwner(owner);
        g.setPos(from.x, from.y - 0.1, from.z);
        g.setDeltaMovement(velocity);
        g.fuse = fuse;
        level.addFreshEntity(g);
        level.playSound(null, from.x, from.y, from.z, ModSounds.get("grenade_pin"), SoundSource.PLAYERS, 0.8f, 1f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        Vec3 before = v;
        move(MoverType.SELF, v);
        Vec3 actual = getDeltaMovement();
        // bounce: reflect the components that collided
        double vx = before.x, vy = before.y, vz = before.z;
        if (horizontalCollision) {
            if (Math.abs(actual.x) < Math.abs(before.x) * 0.5) vx = -before.x * 0.45;
            if (Math.abs(actual.z) < Math.abs(before.z) * 0.5) vz = -before.z * 0.45;
        }
        if (verticalCollision) {
            vy = before.y < -0.12 ? -before.y * 0.35 : 0;
            vx *= 0.65;
            vz *= 0.65;
            if (before.y < -0.15 && !level().isClientSide) level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
        vy -= GRAVITY;
        setDeltaMovement(vx * 0.985, vy * 0.985, vz * 0.985);
        if (level().isClientSide) {
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.01, 0);
            return;
        }
        if (--fuse <= 0) {
            discard();
            Explosions.blast((ServerLevel) level(), this, getOwner(), position().add(0, 0.2, 0), 3.0f, "Frag Grenade", 4);
        }
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.FRAG_GRENADE.get());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Fuse", fuse);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fuse = tag.getInt("Fuse");
    }
}
