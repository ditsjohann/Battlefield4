package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.combat.Explosions;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.GunLogic;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shoulder-launched rockets and tank main-gun shells. Direct hits on armour deal bonus damage. */
public class RocketEntity extends Projectile {
    public enum Kind {
        ROCKET(2.4, 0.004, 3.2f, 34, 10, "RL-4 Breacher"),
        TANK_SHELL(5.5, 0.012, 3.0f, 30, 14, "MBT-9 Cannon");

        public final double speed, gravity;
        public final float power;
        public final int armourDamage, towerDamage;
        public final String weapon;

        Kind(double speed, double gravity, float power, int armourDamage, int towerDamage, String weapon) {
            this.speed = speed;
            this.gravity = gravity;
            this.power = power;
            this.armourDamage = armourDamage;
            this.towerDamage = towerDamage;
            this.weapon = weapon;
        }
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private int life;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public static RocketEntity launch(ServerLevel level, @Nullable Entity owner, Vec3 origin, Vec3 dir, Kind kind) {
        RocketEntity r = new RocketEntity(ModEntities.ROCKET.get(), level);
        r.setOwner(owner);
        r.entityData.set(KIND, kind.ordinal());
        r.setPos(origin.x, origin.y, origin.z);
        Vec3 v = dir.normalize().scale(kind.speed);
        r.setDeltaMovement(v);
        r.setYRot((float) (Math.atan2(v.x, v.z) * 180 / Math.PI));
        r.setXRot((float) (Math.atan2(v.y, v.horizontalDistance()) * 180 / Math.PI));
        level.addFreshEntity(r);
        GunLogic.playLayered(level, origin, kind == Kind.ROCKET ? "rocket_launch" : "tank_cannon", "explosion_far", null, 2f, 1f, 48);
        if (kind == Kind.TANK_SHELL) {
            level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y, origin.z, 12, 0.4, 0.3, 0.4, 0.02);
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(origin) < 24 * 24) WarfrontNet.toPlayer(p, new SimplePackets.Shake(1.2f));
        }
        return r;
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Kind k = kind();
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            for (int i = 0; i < 3; i++) {
                double f = i / 3.0;
                level().addParticle(k == Kind.ROCKET ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.SMOKE,
                        getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0.01, 0);
            }
            if (k == Kind.ROCKET) level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !level().isClientSide) {
            impact(hit);
            return;
        }
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        setDeltaMovement(v.x, v.y - k.gravity, v.z);
        if (++life > 200 && !level().isClientSide) discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity owner = getOwner();
        if (owner != null && (e == owner || e == owner.getVehicle() || owner.getVehicle() != null && e.getVehicle() == owner.getVehicle())) return false;
        return super.canHitEntity(e) || e instanceof TankEntity;
    }

    private void impact(HitResult hit) {
        ServerLevel level = (ServerLevel) level();
        Kind k = kind();
        Vec3 at = hit.getLocation();
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof TankEntity tank && Teams.canHurt(getOwner(), tank)) {
            tank.damageVehicle(k.armourDamage, getOwner(), k.weapon);
        }
        discard();
        // pull the blast slightly back so it carves the surface that was hit
        Vec3 back = getDeltaMovement().normalize().scale(-0.3);
        Explosions.blast(level, this, getOwner(), at.add(back), k.power, k.weapon, k.towerDamage);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", entityData.get(KIND));
        tag.putInt("Life", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getInt("Kind"));
        life = tag.getInt("Life");
    }
}
