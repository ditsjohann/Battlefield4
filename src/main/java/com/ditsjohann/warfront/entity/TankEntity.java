package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.WarfrontConfig;
import com.ditsjohann.warfront.combat.CombatEvents;
import com.ditsjohann.warfront.combat.Explosions;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.combat.FactionMember;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.entity.ai.SoldierTargetGoal;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.MatchManager;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModDamageTypes;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.BulletManager;
import com.ditsjohann.warfront.weapon.BulletTarget;
import com.ditsjohann.warfront.weapon.EffectiveStats;
import com.ditsjohann.warfront.weapon.GunLogic;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * MBT-9 "Juggernaut" main battle tank.
 *
 * <p>Movement follows the vanilla boat model: the driver's client simulates the hull from its
 * own input and the server accepts it via vanilla vehicle-move packets. Weapons, armour, damage
 * states and AI crews run on the server. Drivers aim the turret with their view; left click fires
 * the main gun, holding right click runs the coaxial machine gun.
 */
public class TankEntity extends Entity implements FactionMember, BulletTarget {
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> FACTION = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> TURRET = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BARREL = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> RELOAD = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);

    public static final float MAX_HEALTH = 100f;
    public static final float DISABLED_AT = 22f;
    public static final int CANNON_RELOAD = 80;
    private static final double MAX_SPEED = 0.30, REVERSE_SPEED = 0.14, ACCEL = 0.011;

    @Nullable private UUID matchId;
    private double speed;
    private boolean coax;
    private int coaxCooldown;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot;
    public long spottedUntil;
    private int stuckTicks, reverseTicks;
    private float lastTurret;
    public float trackPhase;
    @Nullable private Entity lastAttacker;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Nullable
    public static TankEntity spawn(ServerLevel level, BlockPos pos, Faction f, @Nullable UUID matchId) {
        TankEntity t = ModEntities.TANK.get().create(level);
        if (t == null) return null;
        t.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, f == Faction.COALITION ? 0 : 180, 0);
        t.setFaction(f);
        t.matchId = matchId;
        level.addFreshEntity(t);
        return t;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(HEALTH, MAX_HEALTH);
        b.define(FACTION, (byte) 2);
        b.define(TURRET, 0f);
        b.define(BARREL, 0f);
        b.define(RELOAD, 0);
    }

    // ------------------------------------------------------------------ accessors
    @Override
    @Nullable
    public Faction getFaction() {
        byte f = entityData.get(FACTION);
        return f > 1 ? null : Faction.byId(f);
    }

    public void setFaction(@Nullable Faction f) { entityData.set(FACTION, (byte) (f == null ? 2 : f.ordinal())); }

    public float health() { return entityData.get(HEALTH); }

    public boolean disabled() { return health() < DISABLED_AT; }

    /** Turret yaw in world degrees. */
    public float turretYaw() { return entityData.get(TURRET); }

    public float barrelPitch() { return entityData.get(BARREL); }

    public int reloadTicks() { return entityData.get(RELOAD); }

    @Nullable public UUID getMatchId() { return matchId; }

    public boolean hasCrew() { return !getPassengers().isEmpty(); }

    public double speed() { return speed; }

    // ------------------------------------------------------------------ passengers
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2;
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity le ? le : null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int seat = getPassengers().indexOf(passenger);
        Vec3 off;
        if (seat == 0) {
            // commander's hatch on the turret
            float ty = turretYaw() * Mth.DEG_TO_RAD;
            off = new Vec3(Mth.sin(-ty) * -0.35, 1.72, Mth.cos(ty) * -0.35);
        } else {
            float hy = getYRot() * Mth.DEG_TO_RAD;
            off = new Vec3(Mth.sin(-hy) * -1.7, 1.25, Mth.cos(hy) * -1.7);
        }
        move.accept(passenger, getX() + off.x, getY() + off.y, getZ() + off.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float hy = getYRot() * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(Mth.cos(hy), 0, Mth.sin(hy)).scale(2.6);
        Vec3 p = position().add(side);
        return new Vec3(p.x, getY() + 0.2, p.z);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        Faction pf = Teams.of(player);
        if (getFaction() != null && pf != null && pf != getFaction() && hasCrew()) return InteractionResult.FAIL;
        // an allied AI driver hands the tank over to the player
        if (getControllingPassenger() instanceof SoldierEntity s && (pf == null || pf == s.getFaction())) {
            s.stopRiding();
            s.setPos(getDismountLocationForPassenger(s));
        }
        if (pf != null) setFaction(pf);
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public float maxUpStep() { return 1.1f; }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        tickLerp();
        LivingEntity driver = getControllingPassenger();
        if (isControlledByLocalInstance()) {
            if (driver instanceof Player p) drive(p.zza, p.xxa);
            else if (driver instanceof SoldierEntity s && level() instanceof ServerLevel sl) aiDrive(sl, s);
            else drive(0, 0);
            move(MoverType.SELF, getDeltaMovement());
        } else if (driver == null && !level().isClientSide) {
            drive(0, 0);
            move(MoverType.SELF, getDeltaMovement());
        }
        if (!level().isClientSide) serverTick(driver);
        else clientTick();
    }

    /** Arcade tank kinematics: throttle accelerates along the hull, steering pivots it. */
    private void drive(float forward, float strafe) {
        double max = disabled() ? MAX_SPEED * 0.4 : MAX_SPEED;
        double target = forward > 0 ? max * forward : forward < 0 ? -REVERSE_SPEED * -forward : 0;
        if (speed < target) speed = Math.min(target, speed + ACCEL);
        else if (speed > target) speed = Math.max(target, speed - ACCEL * (forward == 0 ? 1.6 : 2.2));
        float turn = strafe * (disabled() ? 1.0f : 2.2f) * (speed < -0.01 ? -1 : 1);
        setYRot(getYRot() - turn);
        float yr = getYRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
        double vy = getDeltaMovement().y;
        vy = onGround() ? -0.04 : Math.max(-1.5, vy - 0.08);
        setDeltaMovement(dir.x * speed, vy, dir.z * speed);
        trackPhase += (float) speed;
    }

    private void serverTick(@Nullable LivingEntity driver) {
        ServerLevel level = (ServerLevel) level();
        // effective speed from motion (works for client-driven movement too)
        double moved = new Vec3(getX() - xo, 0, getZ() - zo).length();
        if (!isControlledByLocalInstance()) speed = moved * Math.signum(speed == 0 ? 1 : speed);
        int reload = reloadTicks();
        if (reload > 0) entityData.set(RELOAD, reload - 1);
        if (coaxCooldown > 0) coaxCooldown--;
        // turret follows the driver's view (players) or the AI's aim (soldiers)
        if (driver instanceof Player p) {
            aimTurret(p.getYHeadRot(), Mth.clamp(p.getXRot(), -25f, 10f));
            if (coax) fireCoax(level, p);
        }
        if (moved > 0.06) crush(level, moved);
        if (disabled()) {
            entityData.set(HEALTH, Math.max(0.5f, health() - 0.04f));
            if (tickCount % 5 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 2, 0.5, 0.2, 0.5, 0.02);
            if (tickCount % 8 == 0) level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.6, getZ(), 2, 0.4, 0.1, 0.4, 0.01);
        }
        if (driver != null && tickCount % 40 == 0 && moved > 0.01) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.get("tank_engine"), SoundSource.NEUTRAL, 1.2f, 0.9f + (float) moved);
        }
        if (Math.abs(Mth.wrapDegrees(turretYaw() - lastTurret)) > 1.5f && tickCount % 20 == 0) {
            level.playSound(null, getX(), getY() + 1.5, getZ(), ModSounds.get("turret_traverse"), SoundSource.NEUTRAL, 0.5f, 1f);
        }
        lastTurret = turretYaw();
        if (spottedUntil > 0 && level.getGameTime() > spottedUntil) {
            setGlowingTag(false);
            spottedUntil = 0;
        }
        if (getY() < level.getMinBuildHeight() - 10) discard();
    }

    private void clientTick() {
        if (disabled() && tickCount % 3 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 0, 0.08, 0);
        double moved = new Vec3(getX() - xo, 0, getZ() - zo).length();
        if (moved > 0.02 && tickCount % 2 == 0) {
            float yr = getYRot() * Mth.DEG_TO_RAD;
            Vec3 back = new Vec3(Mth.sin(yr), 0, -Mth.cos(yr)).scale(2.2);
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX() + back.x, getY() + 1.3, getZ() + back.z, 0, 0.05, 0);
        }
        if (!isControlledByLocalInstance()) trackPhase += (float) moved * (speedSignFromMotion() < 0 ? -1 : 1);
    }

    private double speedSignFromMotion() {
        float yr = getYRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
        return dir.dot(new Vec3(getX() - xo, 0, getZ() - zo));
    }

    private void aimTurret(float yaw, float pitch) {
        float cur = turretYaw();
        float delta = Mth.clamp(Mth.wrapDegrees(yaw - cur), -3.2f, 3.2f);
        entityData.set(TURRET, Mth.wrapDegrees(cur + delta));
        float bp = barrelPitch();
        entityData.set(BARREL, bp + Mth.clamp(pitch - bp, -2f, 2f));
    }

    /** Flattens soft blocks and runs over infantry in the tank's path. */
    private void crush(ServerLevel level, double moved) {
        float yr = getYRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr)).scale(Math.signum(speed == 0 ? 1 : speed));
        AABB front = getBoundingBox().move(dir.scale(0.6)).inflate(0.1, 0, 0.1).setMaxY(getY() + 2.2).setMinY(getY());
        if (WarfrontConfig.TANKS_CRUSH_BLOCKS.get()) {
            BlockPos.betweenClosedStream(front).limit(64).forEach(p -> {
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && crushable(s)) level.destroyBlock(p, false, this);
            });
        }
        if (moved < 0.12) return;
        Entity driver = getControllingPassenger();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, front.inflate(0.2), e -> e.isAlive() && e.getVehicle() != this)) {
            if (driver != null && Teams.allies(driver, e)) {
                e.push(dir.x * 0.5, 0.2, dir.z * 0.5);
                continue;
            }
            e.hurt(ModDamageTypes.source(level, ModDamageTypes.CRUSHED, this, driver), 14f);
            e.push(dir.x * 1.2, 0.5, dir.z * 1.2);
        }
    }

    public static boolean crushable(BlockState s) {
        Block b = s.getBlock();
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FENCES) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WALLS) && b != Blocks.STONE_BRICK_WALL
                || BulletManager.isShatterable(s) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS)
                || b instanceof BushBlock || b instanceof SnowLayerBlock || b instanceof BambooStalkBlock || s.is(BlockTags.WOOL_CARPETS)
                || b instanceof TorchBlock || b instanceof LanternBlock || b instanceof SignBlock || b instanceof CactusBlock
                || b instanceof SugarCaneBlock || b instanceof IronBarsBlock && b != Blocks.IRON_BARS || b == Blocks.HAY_BLOCK
                || b instanceof BarrelBlock || s.is(BlockTags.WOODEN_TRAPDOORS) || s.is(BlockTags.WOODEN_DOORS) || b == com.ditsjohann.warfront.registry.ModBlocks.SANDBAG.get();
    }

    // ------------------------------------------------------------------ weapons
    public Vec3 muzzle() {
        float ty = turretYaw() * Mth.DEG_TO_RAD, bp = barrelPitch() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(ty) * Mth.cos(bp), -Mth.sin(bp), Mth.cos(ty) * Mth.cos(bp));
        return position().add(0, 1.55, 0).add(dir.scale(3.6));
    }

    public Vec3 barrelDir() {
        float ty = turretYaw() * Mth.DEG_TO_RAD, bp = barrelPitch() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(ty) * Mth.cos(bp), -Mth.sin(bp), Mth.cos(ty) * Mth.cos(bp));
    }

    public void fireCannon(ServerLevel level, LivingEntity gunner) {
        if (reloadTicks() > 0) return;
        entityData.set(RELOAD, CANNON_RELOAD);
        RocketEntity.launch(level, gunner, muzzle(), barrelDir(), RocketEntity.Kind.TANK_SHELL);
        Vec3 recoil = barrelDir().scale(-0.15);
        setDeltaMovement(getDeltaMovement().add(recoil.x, 0, recoil.z));
    }

    public void setCoax(boolean on) { coax = on; }

    private void fireCoax(ServerLevel level, LivingEntity gunner) {
        if (coaxCooldown > 0) return;
        coaxCooldown = 2;
        float ty = turretYaw() * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(Mth.cos(ty), 0, Mth.sin(ty)).scale(0.35);
        Vec3 origin = position().add(0, 1.6, 0).add(barrelDir().scale(1.8)).add(side);
        EffectiveStats stats = EffectiveStats.of(GunType.HMG240, GunType.HMG240.defaultLoadout());
        BulletManager.fire(level, gunner, stats, origin, GunLogic.applySpread(barrelDir(), 0.9f, random), null);
        GunLogic.playLayered(level, origin, "lmg_shot", "lmg_shot_far", null, 1.4f, 0.9f, 48);
    }

    /** AI crew: drive toward the squad objective, stop to engage, aim and fire. */
    private void aiDrive(ServerLevel level, SoldierEntity crew) {
        ConquestMatch m = MatchManager.active(level);
        Entity target = null;
        double best = 90;
        for (Entity e : level.getEntities(this, getBoundingBox().inflate(90, 30, 90), e -> e != crew && e.getVehicle() != this && SoldierTargetGoal.validTarget(crew, e))) {
            double d = distanceTo(e);
            if (d < best && hasLineOfSight(e)) {
                best = d;
                target = e;
            }
        }
        float forward = 0, steer = 0;
        Vec3 dest = null;
        if (m != null && crew.getMatchId() != null) dest = m.squadDestination(level, crew.getSquadId());
        if (target != null) {
            Vec3 aim = target.getBoundingBox().getCenter().subtract(position().add(0, 1.55, 0));
            float yaw = (float) (Mth.atan2(aim.z, aim.x) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(aim.y, aim.horizontalDistance()) * Mth.RAD_TO_DEG);
            aimTurret(yaw, Mth.clamp(pitch, -25f, 10f));
            boolean aligned = Math.abs(Mth.wrapDegrees(yaw - turretYaw())) < 3f && Math.abs(pitch - barrelPitch()) < 3f;
            if (aligned) {
                if (target instanceof TankEntity || best > 18 || random.nextFloat() < 0.3f) fireCannon(level, crew);
                coax = target instanceof LivingEntity && best < 45;
                if (coax) fireCoax(level, crew);
            }
            if (best < 45) dest = null; // hold position while engaging
        } else {
            coax = false;
            aimTurret(getYRot(), 0);
        }
        if (dest != null && position().distanceTo(dest) > 10) {
            Vec3 to = dest.subtract(position());
            float want = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
            float diff = Mth.wrapDegrees(want - getYRot());
            steer = Mth.clamp(-diff / 20f, -1, 1);
            forward = Math.abs(diff) > 60 ? 0.2f : 1f;
        }
        // unstick: reverse and turn when blocked
        if (reverseTicks > 0) {
            reverseTicks--;
            forward = -1;
            steer = 1;
        } else if (forward > 0.5f && Math.abs(speed) < 0.03 && horizontalCollision) {
            if (++stuckTicks > 30) {
                reverseTicks = 25;
                stuckTicks = 0;
            }
        } else stuckTicks = 0;
        drive(forward, steer);
    }

    private boolean hasLineOfSight(Entity e) {
        Vec3 from = position().add(0, 1.7, 0);
        return level().clip(new net.minecraft.world.level.ClipContext(from, e.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ damage
    @Override
    public void onBulletHit(@Nullable Entity shooter, float damage) {
        // small arms ricochet off the armour (a sliver of damage so massed fire isn't pointless)
        if (Teams.canHurt(shooter, this) || shooter == null) applyDamage(damage * 0.03f, shooter, "Small Arms");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && !Teams.canHurt(attacker, this) && attacker != this) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) applyDamage(amount * 1.2f, attacker, null);
        else if (source.is(DamageTypeTags.IS_FIRE)) applyDamage(amount * 0.2f, attacker, null);
        else if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) applyDamage(amount, attacker, null);
        else if (attacker instanceof Player p && p.isCreative() && p.isShiftKeyDown()) discard();
        return true;
    }

    public void damageVehicle(float amount, @Nullable Entity attacker, String weapon) {
        if (attacker != null && !Teams.canHurt(attacker, this)) return;
        applyDamage(amount, attacker, weapon);
        if (attacker instanceof ServerPlayer sp) WarfrontNet.toPlayer(sp, new SimplePackets.HitMarker(1 | 8, amount));
    }

    private void applyDamage(float amount, @Nullable Entity attacker, @Nullable String weapon) {
        if (amount <= 0 || isRemoved()) return;
        if (attacker != null) lastAttacker = attacker;
        boolean wasDisabled = disabled();
        float h = health() - amount;
        entityData.set(HEALTH, Math.max(0, h));
        level().playSound(null, getX(), getY() + 1, getZ(), ModSounds.get("vehicle_hit"), SoundSource.NEUTRAL, 1f, 0.9f + random.nextFloat() * 0.2f);
        if (!wasDisabled && disabled()) {
            for (Entity p : getPassengers()) if (p instanceof ServerPlayer sp) {
                WarfrontNet.toPlayer(sp, new SimplePackets.Announce("VEHICLE DISABLED", "Get out or get repaired!", 2));
                sp.playNotifySound(ModSounds.get("vehicle_warning"), SoundSource.MASTER, 1f, 1f);
            }
            if (attacker instanceof ServerPlayer sp) Scoring.award(sp, 50, "VEHICLE DISABLED");
        }
        if (h <= 0) destroyVehicle(attacker, weapon);
    }

    public boolean repair(float amount) {
        float h = health();
        if (h >= MAX_HEALTH) return false;
        entityData.set(HEALTH, Math.min(MAX_HEALTH, h + amount));
        return true;
    }

    private void destroyVehicle(@Nullable Entity attacker, @Nullable String weapon) {
        if (!(level() instanceof ServerLevel level)) return;
        Entity killer = attacker != null ? attacker : lastAttacker;
        List<Entity> crew = List.copyOf(getPassengers());
        ejectPassengers();
        for (Entity c : crew) {
            if (c instanceof LivingEntity le) {
                CombatEvents.recordWeapon(le, weapon == null ? "Vehicle Explosion" : weapon);
                le.hurt(level.damageSources().explosion(this, killer), 16f);
            }
        }
        if (killer instanceof ServerPlayer sp && Teams.enemies(sp, this)) Scoring.award(sp, 200, "VEHICLE DESTROYED");
        discard();
        Explosions.blast(level, this, killer, position().add(0, 1, 0), 4.5f, "Vehicle Explosion", 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, 1, 0.5, 1, 0);
    }

    // ------------------------------------------------------------------ networking / lerp (boat-style)
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 10;
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            double d = 1.0 / lerpSteps;
            setPos(getX() + (lerpX - getX()) * d, getY() + (lerpY - getY()) * d, getZ() + (lerpZ - getZ()) * d);
            setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) * (float) d);
            lerpSteps--;
        }
    }

    @Override
    public double lerpTargetX() { return lerpSteps > 0 ? lerpX : getX(); }

    @Override
    public double lerpTargetY() { return lerpSteps > 0 ? lerpY : getY(); }

    @Override
    public double lerpTargetZ() { return lerpSteps > 0 ? lerpZ : getZ(); }

    @Override
    public float lerpTargetYRot() { return lerpSteps > 0 ? (float) lerpYRot : getYRot(); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(HEALTH, tag.contains("Health") ? tag.getFloat("Health") : MAX_HEALTH);
        entityData.set(FACTION, tag.getByte("Faction"));
        entityData.set(TURRET, tag.getFloat("Turret"));
        matchId = tag.hasUUID("Match") ? tag.getUUID("Match") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Health", health());
        tag.putByte("Faction", entityData.get(FACTION));
        tag.putFloat("Turret", turretYaw());
        if (matchId != null) tag.putUUID("Match", matchId);
    }
}
