package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.combat.CombatEvents;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.combat.FactionMember;
import com.ditsjohann.warfront.entity.ai.*;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.registry.ModDamageTypes;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.registry.ModItems;
import com.ditsjohann.warfront.weapon.GunItem;
import com.ditsjohann.warfront.weapon.GunLogic;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * An AI infantryman. Soldiers carry real WARFRONT weapons and fire them through the same
 * ballistics as players. Behaviour is layered goals: target selection, firefights with cover and
 * suppression, grenades/rockets, reviving downed allies, and following squad orders from the
 * team Commander (or from their player squad leader).
 */
public class SoldierEntity extends PathfinderMob implements FactionMember {
    private static final EntityDataAccessor<Byte> FACTION = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> ROLE = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DOWNED = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SQUAD = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);

    public static final int BLEED_TICKS = 360;
    private static final String[] RANKS = {"Pvt.", "Pvt.", "Pfc.", "Cpl.", "Sgt."};
    private static final String[] NAMES = {"Novak", "Kerr", "Adeyemi", "Halvorsen", "Ibarra", "Kowal", "Tanaka", "Moreau", "Brandt",
            "Okafor", "Lindqvist", "Reyes", "Castellano", "Vance", "Petrov", "Duarte", "Nakamura", "Sato", "Mbeki", "Larsen", "Quinn",
            "Rourke", "Fischer", "Abara", "Holt", "Marsh", "Kade", "Oduya", "Strand", "Varga", "Wolfe", "Yilmaz", "Zielinski", "Barros"};

    @Nullable private UUID matchId;
    private float suppression;
    private int downedTicks;
    @Nullable private Vec3 heardPos;
    private long heardTime;
    @Nullable private LivingEntity lastAttacker;
    private long lastAttackedTime;
    public int grenadeCooldown = 200;
    public int rocketCooldown = 80;
    public int rockets = 3;
    public float shotDebt;

    public SoldierEntity(EntityType<? extends SoldierEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
        this.setDropChance(EquipmentSlot.MAINHAND, 0f);
        this.setDropChance(EquipmentSlot.OFFHAND, 0f);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 80)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Nullable
    public static SoldierEntity spawn(ServerLevel level, BlockPos pos, Faction faction, SoldierRole role, int squad, @Nullable UUID matchId) {
        SoldierEntity s = ModEntities.SOLDIER.get().create(level);
        if (s == null) return null;
        s.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        s.setup(faction, role, squad, matchId);
        level.addFreshEntity(s);
        return s;
    }

    public void setup(Faction faction, SoldierRole role, int squad, @Nullable UUID matchId) {
        this.entityData.set(FACTION, (byte) faction.ordinal());
        this.entityData.set(ROLE, (byte) role.ordinal());
        this.entityData.set(VARIANT, (byte) random.nextInt(2));
        this.entityData.set(SQUAD, squad);
        this.matchId = matchId;
        GunType gun = role.pickGun(random);
        ItemStack stack = new ItemStack(ModItems.gun(gun));
        stack.set(ModComponents.KIT_ITEM.get(), Unit.INSTANCE);
        stack.set(ModComponents.RESERVE.get(), 9999);
        this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        this.rockets = role == SoldierRole.ENGINEER ? 3 : 0;
        String rank = RANKS[random.nextInt(RANKS.length)];
        this.setCustomName(Component.literal(rank + " " + NAMES[random.nextInt(NAMES.length)]).withStyle(faction.chat));
        this.setCustomNameVisible(false);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
        SpawnGroupData d = super.finalizeSpawn(level, difficulty, type, data);
        if (getMainHandItem().isEmpty()) {
            RandomSource r = level.getRandom();
            setup(r.nextBoolean() ? Faction.COALITION : Faction.LEGION, SoldierRole.byId(r.nextInt(4)), -1, null);
        }
        return d;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FACTION, (byte) 0);
        builder.define(ROLE, (byte) 0);
        builder.define(VARIANT, (byte) 0);
        builder.define(DOWNED, false);
        builder.define(AIMING, false);
        builder.define(SQUAD, -1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ReviveAllyGoal(this));
        this.goalSelector.addGoal(2, new SoldierCombatGoal(this));
        this.goalSelector.addGoal(3, new ObjectiveGoal(this));
        this.goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override
            public boolean canUse() {
                return matchId == null && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                LivingEntity attacker = mob.getLastHurtByMob();
                return attacker != null && !com.ditsjohann.warfront.combat.Teams.allies(mob, attacker) && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new SoldierTargetGoal(this));
    }

    // ------------------------------------------------------------------ state
    @Override
    @Nullable
    public Faction getFaction() { return Faction.byId(entityData.get(FACTION)); }

    public SoldierRole getRole() { return SoldierRole.byId(entityData.get(ROLE)); }

    public int getVariant() { return entityData.get(VARIANT); }

    public int getSquadId() { return entityData.get(SQUAD); }

    @Nullable public UUID getMatchId() { return matchId; }

    public boolean isDowned() { return entityData.get(DOWNED); }

    public boolean isAiming() { return entityData.get(AIMING); }

    public void setAiming(boolean a) { if (a != isAiming()) entityData.set(AIMING, a); }

    public int getDownedTicks() { return downedTicks; }

    public float getSuppression() { return suppression; }

    public void suppress(float amount, @Nullable Entity by) {
        suppression = Math.min(1f, suppression + amount);
        if (by instanceof LivingEntity le && getTarget() == null && !isDowned()) {
            lastAttacker = le;
            lastAttackedTime = level().getGameTime();
            heardPos = le.getEyePosition();
            heardTime = level().getGameTime();
        }
    }

    public void hearGunfire(LivingEntity shooter) {
        heardPos = shooter.getEyePosition();
        heardTime = level().getGameTime();
    }

    @Nullable
    public Vec3 recentNoise() {
        return heardPos != null && level().getGameTime() - heardTime < 60 ? heardPos : null;
    }

    public void onShotBy(LivingEntity shooter) {
        lastAttacker = shooter;
        lastAttackedTime = level().getGameTime();
    }

    @Nullable
    public LivingEntity recentAttacker() {
        return lastAttacker != null && lastAttacker.isAlive() && level().getGameTime() - lastAttackedTime < 100 ? lastAttacker : null;
    }

    public void setDowned(boolean downed) {
        entityData.set(DOWNED, downed);
        if (downed) {
            downedTicks = BLEED_TICKS;
            getNavigation().stop();
            setTarget(null);
            setAiming(false);
            setPose(Pose.STANDING);
        }
    }

    public GunType gunType() {
        return getMainHandItem().getItem() instanceof GunItem g ? g.type : GunType.AR17;
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        suppression = Math.max(0, suppression - 0.012f);
        if (grenadeCooldown > 0) grenadeCooldown--;
        if (rocketCooldown > 0) rocketCooldown--;
        if (isDowned()) {
            if (--downedTicks <= 0) {
                setDowned(false);
                hurt(ModDamageTypes.source(level(), ModDamageTypes.BLEED_OUT, null, null), Float.MAX_VALUE);
            }
            return;
        }
        ItemStack gun = getMainHandItem();
        if (gun.getItem() instanceof GunItem g) GunLogic.tickReload(this, gun, g.type, level().getGameTime(), true);
        if (getTarget() == null && isAiming()) setAiming(false);
        if (getTarget() == null && getPose() == Pose.CROUCHING && random.nextInt(60) == 0) setPose(Pose.STANDING);
    }

    @Override
    protected void customServerAiStep() {
        if (isDowned() || getVehicle() instanceof TankEntity) return; // tank crews are driven by the tank
        super.customServerAiStep();
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || isDowned();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        if (pose == Pose.CROUCHING) return EntityDimensions.scalable(0.6f, 1.5f).withEyeHeight(1.27f);
        return super.getDefaultDimensions(pose);
    }

    @Override
    public boolean isCrouching() {
        return getPose() == Pose.CROUCHING;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recent) {
        if (source.getEntity() instanceof Player && random.nextFloat() < 0.25f) {
            spawnAtLocation(new ItemStack(ModItems.DOG_TAG.get()));
        }
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return com.ditsjohann.warfront.combat.Teams.allies(this, other) || super.isAlliedTo(other);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Faction", entityData.get(FACTION));
        tag.putByte("Role", entityData.get(ROLE));
        tag.putByte("Variant", entityData.get(VARIANT));
        tag.putInt("Squad", getSquadId());
        tag.putBoolean("Downed", isDowned());
        tag.putInt("DownedTicks", downedTicks);
        tag.putInt("Rockets", rockets);
        if (matchId != null) tag.putUUID("Match", matchId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(FACTION, tag.getByte("Faction"));
        entityData.set(ROLE, tag.getByte("Role"));
        entityData.set(VARIANT, tag.getByte("Variant"));
        entityData.set(SQUAD, tag.getInt("Squad"));
        entityData.set(DOWNED, tag.getBoolean("Downed"));
        downedTicks = tag.getInt("DownedTicks");
        rockets = tag.getInt("Rockets");
        matchId = tag.hasUUID("Match") ? tag.getUUID("Match") : null;
    }

    /** Exposed for gametests. */
    public void forceSuppression(float s) { this.suppression = s; }

    public boolean isCombatant() { return isAlive() && !isDowned(); }

    static boolean visible(Mob from, Entity to) {
        return from.getSensing().hasLineOfSight(to);
    }
}
