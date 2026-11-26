package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.combat.FactionMember;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.MatchManager;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.weapon.BulletTarget;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A recon radio beacon: a forward spawn point for the whole team until enemies destroy it. */
public class SpawnBeaconEntity extends Entity implements FactionMember, BulletTarget {
    private static final EntityDataAccessor<Byte> FACTION = SynchedEntityData.defineId(SpawnBeaconEntity.class, EntityDataSerializers.BYTE);
    private float health = 8;
    @Nullable private UUID owner;
    private String ownerName = "";

    public SpawnBeaconEntity(EntityType<? extends SpawnBeaconEntity> type, Level level) {
        super(type, level);
    }

    public static SpawnBeaconEntity place(ServerLevel level, ServerPlayer owner, Vec3 at, Faction f) {
        // one beacon per player
        for (SpawnBeaconEntity old : level.getEntitiesOfClass(SpawnBeaconEntity.class, owner.getBoundingBox().inflate(512), b -> owner.getUUID().equals(b.owner))) old.destroy(null);
        SpawnBeaconEntity b = new SpawnBeaconEntity(ModEntities.SPAWN_BEACON.get(), level);
        b.setPos(at.x, at.y, at.z);
        b.entityData.set(FACTION, (byte) f.ordinal());
        b.owner = owner.getUUID();
        b.ownerName = owner.getGameProfile().getName();
        level.addFreshEntity(b);
        ConquestMatch m = MatchManager.active(level);
        if (m != null) m.beacons.add(new ConquestMatch.SpawnBeacon(f, b.blockPosition(), b.getUUID(), b.ownerName));
        return b;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FACTION, (byte) 0);
    }

    @Override
    @Nullable
    public Faction getFaction() {
        return Faction.byId(entityData.get(FACTION));
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            move(MoverType.SELF, getDeltaMovement());
        }
        if (level().isClientSide && tickCount % 20 < 2) {
            Faction f = getFaction();
            int c = f == null ? 0xFFFFFF : f.color;
            level().addParticle(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 1f),
                    getX(), getY() + 0.95, getZ(), 0, 0, 0);
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        Entity e = source.getEntity();
        if (e != null && Teams.allies(e, this)) return false;
        health -= amount;
        if (health <= 0) destroy(e);
        return true;
    }

    @Override
    public void onBulletHit(@Nullable Entity shooter, float damage) {
        if (shooter != null && Teams.allies(shooter, this)) return;
        health -= damage;
        if (health <= 0) destroy(shooter);
    }

    private void destroy(@Nullable Entity by) {
        if (level() instanceof ServerLevel sl) {
            ConquestMatch m = MatchManager.active(sl);
            if (m != null) m.beacons.removeIf(b -> b.entity().equals(getUUID()));
            if (by instanceof ServerPlayer sp && Teams.enemies(sp, this)) Scoring.award(sp, 50, "SPAWN BEACON DESTROYED");
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(FACTION, tag.getByte("Faction"));
        health = tag.getFloat("Health");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Faction", entityData.get(FACTION));
        tag.putFloat("Health", health);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }
}
