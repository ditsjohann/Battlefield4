package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.combat.CombatEvents;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.registry.ModItems;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.GunItem;
import com.ditsjohann.warfront.weapon.GunLogic;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A thrown medic bag or ammo bag. Heals / resupplies teammates standing near it. */
public class SupplyBagEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> AMMO = SynchedEntityData.defineId(SupplyBagEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int LIFETIME = 600;
    private int life = LIFETIME;
    private int scoreThisBag;

    public SupplyBagEntity(EntityType<? extends SupplyBagEntity> type, Level level) {
        super(type, level);
    }

    public static void toss(ServerLevel level, LivingEntity owner, boolean ammo) {
        SupplyBagEntity b = new SupplyBagEntity(ModEntities.SUPPLY_BAG.get(), level);
        b.setOwner(owner);
        b.entityData.set(AMMO, ammo);
        Vec3 eye = owner.getEyePosition();
        b.setPos(eye.x, eye.y - 0.4, eye.z);
        b.setDeltaMovement(owner.getLookAngle().scale(0.45).add(0, 0.15, 0));
        level.addFreshEntity(b);
        level.playSound(null, eye.x, eye.y, eye.z, ModSounds.get("bag_drop"), SoundSource.PLAYERS, 0.8f, 1f);
    }

    public boolean isAmmo() {
        return entityData.get(AMMO);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AMMO, false);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        move(MoverType.SELF, v);
        if (onGround()) setDeltaMovement(v.x * 0.5, 0, v.z * 0.5);
        else setDeltaMovement(v.x * 0.98, v.y - 0.04, v.z * 0.98);
        if (level().isClientSide) {
            if (tickCount % 10 == 0) level().addParticle(isAmmo() ? ParticleTypes.CRIT : ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.5, getZ(), 0, 0.05, 0);
            return;
        }
        if (--life <= 0) {
            discard();
            return;
        }
        if (tickCount % 20 != 0) return;
        Entity owner = getOwner();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 2, 3.5),
                e -> e.isAlive() && !CombatEvents.isDowned(e) && (e == owner || Teams.allies(owner == null ? this : owner, e) || owner == null))) {
            boolean helped = isAmmo() ? resupply(e) : heal(e);
            if (helped && owner instanceof ServerPlayer sp && e != owner && scoreThisBag < 200) {
                Scoring.award(sp, 10, isAmmo() ? "RESUPPLY" : "HEAL");
                scoreThisBag += 10;
            }
        }
    }

    private boolean heal(LivingEntity e) {
        if (e.getHealth() >= e.getMaxHealth()) return false;
        e.heal(1.5f);
        return true;
    }

    private boolean resupply(LivingEntity e) {
        boolean any = false;
        if (e instanceof Player p) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.getItem() instanceof GunItem g) any |= GunLogic.resupply(s, g.type, 0.5f) > 0;
                if (tickCount % 100 == 0 && s.is(ModItems.FRAG_GRENADE.get())) {
                    int c = s.getOrDefault(ModComponents.CHARGES.get(), 1);
                    if (c < 2) {
                        s.set(ModComponents.CHARGES.get(), c + 1);
                        any = true;
                    }
                }
            }
            if (any) level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.get("resupply"), SoundSource.PLAYERS, 0.4f, 1.2f);
        } else if (e instanceof SoldierEntity s && s.rockets < 3 && tickCount % 100 == 0 && s.getRole() == SoldierRole.ENGINEER) {
            s.rockets++;
            any = true;
        }
        return any;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Ammo", isAmmo());
        tag.putInt("Life", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AMMO, tag.getBoolean("Ammo"));
        life = tag.getInt("Life");
    }

    @Nullable
    @Override
    public Entity getOwner() {
        return super.getOwner();
    }
}
