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
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.BulletManager;
import com.ditsjohann.warfront.weapon.BulletTarget;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Shared ground-vehicle behaviour: health and armour model, disabled/burning state, faction,
 * seats, boat-style client-driven movement for players, AI driving with simple tactics, soft-block
 * crushing, roadkills, HQ field repair, crew bail-out, and destruction.
 *
 * <p>Subclasses describe their handling, seats and weapons. Seat 0 is always the driver; the
 * {@link #gunnerSeat()} seat aims and fires the vehicle's weapons with their view.
 */
public abstract class VehicleEntity extends Entity implements FactionMember, BulletTarget {
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> FACTION = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> TURRET = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BARREL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> RELOAD = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);

    /** Client input actions (see {@link #handleInput}). */
    public static final int IN_ATTACK_PRESS = 0, IN_USE_ON = 1, IN_USE_OFF = 2, IN_ATTACK_ON = 3, IN_ATTACK_OFF = 4;

    @Nullable protected UUID matchId;
    protected double speed;
    protected boolean attackHeld, useHeld;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot;
    public long spottedUntil;
    private int stuckTicks, reverseTicks;
    private float lastTurret;
    public float trackPhase;
    @Nullable protected Entity lastAttacker;
    protected long lastHitTime = -1000;
    private int circleDir = 1;

    protected VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ------------------------------------------------------------------ specification
    public abstract float maxHealth();

    public abstract float disabledAt();

    protected abstract double maxSpeed();

    protected abstract double reverseSpeed();

    protected abstract double accel();

    protected abstract float turnRate();

    protected abstract int seats();

    public abstract String vehicleName();

    /** Rider position for a seat, relative to the vehicle origin in world axes. */
    protected abstract Vec3 seatOffset(int seat);

    /** Seat whose occupant aims and fires the vehicle's weapons. */
    public abstract int gunnerSeat();

    /** Weapon logic each server tick (aim from the gunner's view or AI target). */
    protected abstract void tickWeapons(ServerLevel level, @Nullable LivingEntity gunner);

    /** Short weapon status for the HUD ("CANNON READY", "HMG"). */
    public abstract String weaponStatus();

    /** 0..1 readiness of the primary weapon for the HUD bar. */
    public abstract float weaponReadiness();

    /** Fraction of small-arms damage that gets through the armour. */
    protected float smallArmsFactor() { return 0.03f; }

    protected float roadkillDamage() { return 14f; }

    protected boolean crushesHardCover() { return true; }

    protected float minPitch() { return -25f; }

    protected float maxPitch() { return 10f; }

    protected float turretRate() { return 3.2f; }

    /** Muzzle-velocity style recoil etc. Subclasses fire weapons through these helpers. */
    protected float destroyBlast() { return 4.5f; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(HEALTH, 100f);
        b.define(FACTION, (byte) 2);
        b.define(TURRET, 0f);
        b.define(BARREL, 0f);
        b.define(RELOAD, 0);
    }

    protected void init(Faction f, @Nullable UUID match, float yaw) {
        entityData.set(HEALTH, maxHealth());
        setFaction(f);
        matchId = match;
        setYRot(yaw);
        entityData.set(TURRET, yaw);
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

    protected void setHealth(float h) { entityData.set(HEALTH, h); }

    public boolean disabled() { return health() < disabledAt(); }

    public float turretYaw() { return entityData.get(TURRET); }

    public float barrelPitch() { return entityData.get(BARREL); }

    public int reloadTicks() { return entityData.get(RELOAD); }

    protected void setReload(int t) { entityData.set(RELOAD, t); }

    @Nullable public UUID getMatchId() { return matchId; }

    public boolean hasCrew() { return !getPassengers().isEmpty(); }

    public double speed() { return speed; }

    public int seatOf(Entity e) { return getPassengers().indexOf(e); }

    @Nullable
    public LivingEntity gunner() {
        List<Entity> ps = getPassengers();
        return gunnerSeat() < ps.size() && ps.get(gunnerSeat()) instanceof LivingEntity le ? le : null;
    }

    /** Whether this passenger's mouse buttons control the vehicle (driver or gunner) rather than their own weapon. */
    public boolean usesVehicleControls(Entity passenger) {
        int s = seatOf(passenger);
        return s == 0 || s == gunnerSeat();
    }

    // ------------------------------------------------------------------ passengers
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seats();
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity le ? le : null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        Vec3 off = seatOffset(getPassengers().indexOf(passenger));
        move.accept(passenger, getX() + off.x, getY() + off.y, getZ() + off.z);
    }

    /** Rotates a hull-local offset (x right, z forward) into world axes. */
    protected Vec3 hullOffset(double x, double y, double z) {
        float hy = getYRot() * Mth.DEG_TO_RAD;
        double c = Mth.cos(hy), s = Mth.sin(hy);
        // forward = (-sin, cos), right = (-cos, -sin)
        return new Vec3(-s * z - c * x, y, c * z - s * x);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float hy = getYRot() * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(Mth.cos(hy), 0, Mth.sin(hy)).scale(getBbWidth() * 0.5 + 1.0);
        Vec3 p = position().add(side);
        return new Vec3(p.x, getY() + 0.2, p.z);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        Faction pf = Teams.of(player);
        if (getFaction() != null && pf != null && pf != getFaction() && hasCrew()) return InteractionResult.FAIL;
        // make room: an allied AI crew member hops out. Vanilla always seats a boarding player first,
        // so the player takes the wheel and any remaining AI crew moves to (or stays on) the gun.
        if (getPassengers().size() >= seats() || gunnerSeat() == 0 && getControllingPassenger() instanceof SoldierEntity) {
            List<Entity> ps = getPassengers();
            Entity out = gunnerSeat() == 0 ? getControllingPassenger() : ps.get(ps.size() - 1);
            if (!(out instanceof SoldierEntity s) || pf != null && pf != s.getFaction()) return InteractionResult.FAIL;
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

    /** Arcade kinematics: throttle accelerates along the hull, steering pivots it. */
    protected void drive(float forward, float strafe) {
        double max = disabled() ? maxSpeed() * 0.4 : maxSpeed();
        double target = forward > 0 ? max * forward : forward < 0 ? -reverseSpeed() * -forward : 0;
        if (speed < target) speed = Math.min(target, speed + accel());
        else if (speed > target) speed = Math.max(target, speed - accel() * (forward == 0 ? 1.6 : 2.2));
        float turn = strafe * (disabled() ? turnRate() * 0.45f : turnRate()) * (speed < -0.01 ? -1 : 1);
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
        double moved = new Vec3(getX() - xo, 0, getZ() - zo).length();
        if (!isControlledByLocalInstance()) speed = moved * Math.signum(speed == 0 ? 1 : speed);
        int reload = reloadTicks();
        if (reload > 0) setReload(reload - 1);
        LivingEntity gunner = gunner();
        if (gunner instanceof Player p) aimTurret(p.getYHeadRot(), Mth.clamp(p.getXRot(), minPitch(), maxPitch()));
        tickWeapons(level, gunner);
        if (moved > 0.06) crush(level, moved);
        if (disabled()) {
            setHealth(Math.max(0.5f, health() - 0.04f));
            if (tickCount % 5 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight(), getZ(), 2, 0.5, 0.2, 0.5, 0.02);
            if (tickCount % 8 == 0) level.sendParticles(ParticleTypes.FLAME, getX(), getY() + getBbHeight() * 0.7, getZ(), 2, 0.4, 0.1, 0.4, 0.01);
            // AI crews bail out of a burning wreck and fight on foot
            for (Entity e : List.copyOf(getPassengers())) {
                if (e instanceof SoldierEntity s) {
                    s.stopRiding();
                    s.setPos(getDismountLocationForPassenger(s));
                }
            }
        }
        fieldRepair(level);
        if (driver != null && tickCount % 40 == 0 && moved > 0.01) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.get("tank_engine"), SoundSource.NEUTRAL, 1.2f, enginePitch() + (float) moved);
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

    protected float enginePitch() { return 0.9f; }

    /** Vehicles parked at their own HQ are patched up by the ground crew. */
    private void fieldRepair(ServerLevel level) {
        if (tickCount % 10 != 0 || health() >= maxHealth()) return;
        ConquestMatch m = MatchManager.active(level);
        Faction f = getFaction();
        if (m == null || f == null) return;
        if (blockPosition().distSqr(m.hq(f)) < 20 * 20) {
            setHealth(Math.min(maxHealth(), health() + maxHealth() * 0.012f));
            if (tickCount % 40 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 4, 0.8, 0.4, 0.8, 0.1);
        }
    }

    private void clientTick() {
        if (disabled() && tickCount % 3 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight(), getZ(), 0, 0.08, 0);
        double moved = new Vec3(getX() - xo, 0, getZ() - zo).length();
        if (moved > 0.02 && tickCount % 2 == 0) {
            Vec3 back = hullOffset(0, 0, -getBbWidth() * 0.6);
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX() + back.x, getY() + getBbHeight() * 0.55, getZ() + back.z, 0, 0.05, 0);
        }
        if (!isControlledByLocalInstance()) trackPhase += (float) moved * (speedSignFromMotion() < 0 ? -1 : 1);
    }

    private double speedSignFromMotion() {
        float yr = getYRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
        return dir.dot(new Vec3(getX() - xo, 0, getZ() - zo));
    }

    protected void aimTurret(float yaw, float pitch) {
        float cur = turretYaw();
        float delta = Mth.clamp(Mth.wrapDegrees(yaw - cur), -turretRate(), turretRate());
        entityData.set(TURRET, Mth.wrapDegrees(cur + delta));
        float bp = barrelPitch();
        entityData.set(BARREL, bp + Mth.clamp(Mth.clamp(pitch, minPitch(), maxPitch()) - bp, -2f, 2f));
    }

    protected boolean aligned(float yaw, float pitch, float tolerance) {
        return Math.abs(Mth.wrapDegrees(yaw - turretYaw())) < tolerance && Math.abs(pitch - barrelPitch()) < tolerance;
    }

    /** Flattens soft blocks and runs over infantry in the vehicle's path. */
    private void crush(ServerLevel level, double moved) {
        float yr = getYRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr)).scale(Math.signum(speed == 0 ? 1 : speed));
        AABB front = getBoundingBox().move(dir.scale(0.6)).inflate(0.1, 0, 0.1).setMaxY(getY() + getBbHeight()).setMinY(getY());
        if (WarfrontConfig.TANKS_CRUSH_BLOCKS.get()) {
            BlockPos.betweenClosedStream(front).limit(64).forEach(p -> {
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && (crushesHardCover() ? crushable(s) : lightCrushable(s))) level.destroyBlock(p, false, this);
            });
        }
        if (moved < 0.12) return;
        Entity driver = getControllingPassenger();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, front.inflate(0.2), e -> e.isAlive() && e.getVehicle() != this)) {
            if (driver != null && Teams.allies(driver, e)) {
                e.push(dir.x * 0.5, 0.2, dir.z * 0.5);
                continue;
            }
            e.hurt(ModDamageTypes.source(level, ModDamageTypes.CRUSHED, this, driver), roadkillDamage() * (float) Math.min(1.5, moved / 0.25));
            e.push(dir.x * 1.2, 0.5, dir.z * 1.2);
        }
    }

    public static boolean crushable(BlockState s) {
        Block b = s.getBlock();
        return lightCrushable(s) || s.is(BlockTags.FENCES) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WALLS) && b != Blocks.STONE_BRICK_WALL
                || BulletManager.isShatterable(s) || b instanceof IronBarsBlock && b != Blocks.IRON_BARS || b == Blocks.HAY_BLOCK
                || b instanceof BarrelBlock || s.is(BlockTags.WOODEN_TRAPDOORS) || s.is(BlockTags.WOODEN_DOORS) || b == com.ditsjohann.warfront.registry.ModBlocks.SANDBAG.get();
    }

    /** What even a light vehicle flattens: plants, snow, small decorations. */
    public static boolean lightCrushable(BlockState s) {
        Block b = s.getBlock();
        return s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS) || b instanceof BushBlock || b instanceof SnowLayerBlock
                || s.is(BlockTags.WOOL_CARPETS) || b instanceof TorchBlock || b instanceof LanternBlock || b instanceof SignBlock
                || b instanceof SugarCaneBlock || b instanceof BambooStalkBlock || b instanceof CactusBlock;
    }

    // ------------------------------------------------------------------ input
    /** Mouse input from a player passenger. */
    public void handleInput(ServerPlayer p, int action) {
        if (seatOf(p) != gunnerSeat()) return;
        switch (action) {
            case IN_ATTACK_ON -> attackHeld = true;
            case IN_ATTACK_OFF -> attackHeld = false;
            case IN_USE_ON -> useHeld = true;
            case IN_USE_OFF -> useHeld = false;
            default -> {}
        }
    }

    // ------------------------------------------------------------------ AI
    /** Range at which the AI crew engages targets. */
    protected double aiRange() { return 90; }

    @Nullable private Entity cachedTarget;
    private int targetRefresh;

    /** Nearest visible enemy for an AI crew member; re-scanned every 10 ticks. */
    @Nullable
    protected Entity findTarget(LivingEntity perspective, double range) {
        if (!(perspective instanceof SoldierEntity crew)) return null;
        if (--targetRefresh > 0 && (cachedTarget == null || cachedTarget.isAlive() && distanceTo(cachedTarget) < range)) return cachedTarget;
        targetRefresh = 10;
        Entity target = null;
        double best = range;
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(range, 30, range),
                e -> e != crew && e.getVehicle() != this && SoldierTargetGoal.validTarget(crew, e))) {
            double d = distanceTo(e);
            if (d < best && hasLineOfSight(e)) {
                best = d;
                target = e;
            }
        }
        cachedTarget = target;
        return target;
    }

    /**
     * AI driving with simple combined-arms tactics: head for the squad's objective; fall back to HQ
     * for repairs when badly damaged; keep a standoff distance from infantry (rockets) and hold
     * position to shoot when a target is in range; light vehicles circle their target instead.
     */
    private void aiDrive(ServerLevel level, SoldierEntity crew) {
        ConquestMatch m = MatchManager.active(level);
        Entity target = findTarget(crew, aiRange());
        float forward = 0, steer = 0;
        Vec3 dest = null;
        if (m != null && crew.getMatchId() != null) dest = m.squadDestination(level, crew.getSquadId());
        boolean retreat = health() < maxHealth() * 0.4f && m != null && getFaction() != null;
        if (retreat) dest = Vec3.atBottomCenterOf(m.hq(getFaction()));
        if (gunnerSeat() == 0) aiGun(level, crew, target);
        if (target != null && !retreat) {
            double d = distanceTo(target);
            Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize();
            switch (aiStyle()) {
                case HOLD -> {
                    if (d < 14 && !(target instanceof VehicleEntity)) {
                        // too close to infantry with rockets: back off while firing
                        dest = position().add(away.scale(12));
                    } else if (d < 60) dest = null;
                }
                case CIRCLE -> {
                    if (d < 45) {
                        Vec3 tangent = new Vec3(-away.z, 0, away.x).scale(circleDir);
                        Vec3 radial = away.scale(d < 18 ? 1 : d > 28 ? -1 : 0);
                        dest = position().add(tangent.scale(10)).add(radial.scale(6));
                        if (random.nextInt(200) == 0) circleDir = -circleDir;
                    }
                }
            }
        }
        if (dest != null && position().distanceTo(dest) > (retreat ? 6 : 10)) {
            Vec3 to = dest.subtract(position());
            float want = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
            float diff = Mth.wrapDegrees(want - getYRot());
            steer = Mth.clamp(-diff / 20f, -1, 1);
            forward = Math.abs(diff) > 60 ? 0.25f : 1f;
        }
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

    protected enum AiStyle { HOLD, CIRCLE }

    protected AiStyle aiStyle() { return AiStyle.HOLD; }

    /** AI gunner: aim at the target and fire the vehicle's weapons. */
    protected abstract void aiGun(ServerLevel level, SoldierEntity gunner, @Nullable Entity target);

    protected float[] aimAngles(Vec3 from, Vec3 at) {
        Vec3 aim = at.subtract(from);
        float yaw = (float) (Mth.atan2(aim.z, aim.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(aim.y, aim.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new float[]{yaw, pitch};
    }

    protected boolean hasLineOfSight(Entity e) {
        Vec3 from = position().add(0, getBbHeight() * 0.75, 0);
        if (com.ditsjohann.warfront.combat.SmokeScreens.blocks(level(), from, e.getEyePosition())) return false;
        return level().clip(new ClipContext(from, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ damage
    @Override
    public void onBulletHit(@Nullable Entity shooter, float damage) {
        if (Teams.canHurt(shooter, this) || shooter == null) applyDamage(damage * smallArmsFactor(), shooter, "Small Arms");
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

    protected void applyDamage(float amount, @Nullable Entity attacker, @Nullable String weapon) {
        if (amount <= 0 || isRemoved()) return;
        if (attacker != null) lastAttacker = attacker;
        lastHitTime = level().getGameTime();
        boolean wasDisabled = disabled();
        float h = health() - amount;
        setHealth(Math.max(0, h));
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
        if (h >= maxHealth()) return false;
        setHealth(Math.min(maxHealth(), h + amount * maxHealth() / 100f));
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
        if (killer instanceof ServerPlayer sp && Teams.enemies(sp, this)) Scoring.award(sp, maxHealth() >= 80 ? 200 : 120, "VEHICLE DESTROYED");
        discard();
        Explosions.blast(level, this, killer, position().add(0, 1, 0), destroyBlast(), "Vehicle Explosion", 0);
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
        entityData.set(HEALTH, tag.contains("Health") ? tag.getFloat("Health") : maxHealth());
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
