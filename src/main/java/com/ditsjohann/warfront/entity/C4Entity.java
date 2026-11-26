package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.combat.Explosions;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.BulletTarget;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A placed demolition charge. Sticks to the surface it was placed on and to vehicles. */
public class C4Entity extends Projectile implements BulletTarget {
    private static final EntityDataAccessor<Integer> FACE = SynchedEntityData.defineId(C4Entity.class, EntityDataSerializers.INT);
    @Nullable private Entity stuckTo;
    private Vec3 stuckOffset = Vec3.ZERO;

    public C4Entity(EntityType<? extends C4Entity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static C4Entity place(ServerLevel level, Entity owner, Vec3 at, Direction face, @Nullable Entity stickTo) {
        C4Entity c = new C4Entity(ModEntities.C4.get(), level);
        c.setOwner(owner);
        c.setPos(at.x, at.y, at.z);
        c.entityData.set(FACE, face.get3DDataValue());
        if (stickTo != null) {
            c.stuckTo = stickTo;
            c.stuckOffset = at.subtract(stickTo.position());
        }
        level.addFreshEntity(c);
        level.playSound(null, at.x, at.y, at.z, ModSounds.get("c4_beep"), SoundSource.PLAYERS, 0.8f, 1f);
        return c;
    }

    public Direction face() {
        return Direction.from3DDataValue(entityData.get(FACE));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FACE, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (stuckTo != null) {
            if (!stuckTo.isAlive()) stuckTo = null;
            else setPos(stuckTo.position().add(stuckOffset));
        }
        if (level().isClientSide && tickCount % 30 == 0) {
            level().addParticle(new DustParticleOptions(new Vector3f(1f, 0.1f, 0.1f), 0.6f), getX(), getY() + 0.1, getZ(), 0, 0, 0);
        }
    }

    public void detonate() {
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        discard();
        if (stuckTo instanceof TankEntity t) t.damageVehicle(55, getOwner(), "C4");
        Explosions.blast(sl, this, getOwner(), position(), 4.2f, "C4", 26);
    }

    /** Detonates every charge owned by {@code owner} within range. Returns how many went off. */
    public static int detonateAll(ServerLevel level, Entity owner) {
        List<C4Entity> charges = level.getEntitiesOfClass(C4Entity.class, owner.getBoundingBox().inflate(160), c -> c.getOwner() == owner);
        for (C4Entity c : charges) c.detonate();
        return charges.size();
    }

    @Override
    public void onBulletHit(@Nullable Entity shooter, float damage) {
        // a stray round sets the charge off
        if (random.nextFloat() < 0.25f) detonate();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Face", entityData.get(FACE));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(FACE, tag.getInt("Face"));
    }
}
