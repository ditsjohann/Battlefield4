package com.ditsjohann.warfront.entity.ai;

import com.ditsjohann.warfront.WarfrontConfig;
import com.ditsjohann.warfront.entity.FragGrenadeEntity;
import com.ditsjohann.warfront.entity.RocketEntity;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.SoldierRole;
import com.ditsjohann.warfront.entity.TankEntity;
import com.ditsjohann.warfront.weapon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The firefight. Soldiers aim with a human-like reaction delay, fire in bursts whose length
 * depends on range, lead moving targets and compensate for bullet drop. Under suppression they
 * break for cover (positions where the threat's line of fire is blocked at crouch height) and
 * peek out to return fire. They grenade enemies hiding behind cover and engineers rocket armour.
 */
public class SoldierCombatGoal extends Goal {
    private final SoldierEntity s;
    private int aimTicks;
    private int burstLeft;
    private int pause;
    private int strafeTimer;
    private int coverTicks;
    private int coverCooldown;
    private int repath;
    private Vec3 lastSeen;
    private long lastSeenTime;
    private BlockPos coverPos;

    public SoldierCombatGoal(SoldierEntity s) {
        this.s = s;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = s.getTarget();
        return t != null && t.isAlive() && !s.isDowned() && !s.isPassenger();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        aimTicks = 0;
        burstLeft = 0;
        pause = 4 + s.getRandom().nextInt(8);
        coverTicks = 0;
    }

    @Override
    public void stop() {
        s.setAiming(false);
        s.getNavigation().stop();
        if (s.getPose() == Pose.CROUCHING) s.setPose(Pose.STANDING);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private static float preferredRange(GunType g) {
        return switch (g.category) {
            case SNIPER -> 70;
            case LMG -> 42;
            case ASSAULT_RIFLE -> 36;
            case CARBINE -> 28;
            case SHOTGUN -> 9;
            case PISTOL -> 16;
        };
    }

    @Override
    public void tick() {
        LivingEntity target = s.getTarget();
        if (target == null || !target.isAlive() || s.isDowned() || s.isPassenger()) return;
        ServerLevel level = (ServerLevel) s.level();
        Entity aimAt = target.getVehicle() instanceof TankEntity tank ? tank : target;
        boolean vsArmour = aimAt instanceof TankEntity;
        boolean canSee = s.getSensing().hasLineOfSight(aimAt);
        long now = level.getGameTime();
        if (canSee) {
            lastSeen = aimAt.position();
            lastSeenTime = now;
        }
        double dist = s.distanceTo(aimAt);
        GunType gun = s.gunType();
        float range = preferredRange(gun);
        if (coverCooldown > 0) coverCooldown--;

        // ---------------- movement
        if (coverTicks > 0) {
            coverTicks--;
            boolean peek = coverTicks % 50 < 18;
            s.setPose(peek && s.getNavigation().isDone() ? Pose.STANDING : Pose.CROUCHING);
            if (s.getSuppression() > 0.8f) s.setPose(Pose.CROUCHING);
        } else if (s.getSuppression() > 0.5f && coverCooldown <= 0) {
            coverPos = CoverFinder.find(s, aimAt.getEyePosition(), 10);
            coverCooldown = 140;
            if (coverPos != null && s.getNavigation().moveTo(coverPos.getX() + 0.5, coverPos.getY(), coverPos.getZ() + 0.5, 1.3)) {
                coverTicks = 60 + s.getRandom().nextInt(70);
                s.setPose(Pose.STANDING);
            }
        } else if (!canSee) {
            if (s.getPose() == Pose.CROUCHING) s.setPose(Pose.STANDING);
            if (lastSeen != null && now - lastSeenTime < 220) {
                if (--repath <= 0) {
                    s.getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, 1.05);
                    repath = 20;
                }
            } else {
                s.setTarget(null);
                return;
            }
        } else if (dist > range && !vsArmour) {
            if (s.getPose() == Pose.CROUCHING) s.setPose(Pose.STANDING);
            if (--repath <= 0) {
                s.getNavigation().moveTo(aimAt, dist > range * 1.8 ? 1.15 : 0.95);
                repath = 15;
            }
        } else {
            // in range with line of sight: hold, strafe, crouch
            if (--strafeTimer <= 0) {
                strafeTimer = 40 + s.getRandom().nextInt(60);
                boolean crouchy = s.getRole() == SoldierRole.SUPPORT || s.getRole() == SoldierRole.RECON;
                if (s.getRandom().nextFloat() < (crouchy ? 0.65f : 0.4f)) {
                    s.getNavigation().stop();
                    s.setPose(Pose.CROUCHING);
                } else {
                    s.setPose(Pose.STANDING);
                    Vec3 to = aimAt.position().subtract(s.position()).normalize();
                    Vec3 side = new Vec3(-to.z, 0, to.x).scale(s.getRandom().nextBoolean() ? 1 : -1);
                    Vec3 dest = s.position().add(side.scale(2.5 + s.getRandom().nextDouble() * 3));
                    if (dist < range * 0.35) dest = dest.subtract(to.scale(3)); // too close: back off
                    s.getNavigation().moveTo(dest.x, dest.y, dest.z, 0.8);
                }
            }
        }

        // ---------------- aiming
        Vec3 aimPoint = aimPoint(aimAt, gun, dist);
        s.getLookControl().setLookAt(aimPoint.x, aimPoint.y, aimPoint.z, 40f, 40f);
        if (canSee) {
            aimTicks++;
            s.setYBodyRot(s.getYHeadRot());
        } else aimTicks = Math.max(0, aimTicks - 2);
        s.setAiming(canSee);

        // ---------------- explosives
        if (vsArmour && s.rockets > 0 && s.rocketCooldown <= 0 && canSee && aimTicks > 20 && dist < 70) {
            Vec3 origin = s.getEyePosition().add(s.getLookAngle().scale(0.6));
            Vec3 dir = aimAt.getBoundingBox().getCenter().subtract(origin).normalize();
            dir = GunLogic.applySpread(dir, 1.6f / WarfrontConfig.AI_ACCURACY.get().floatValue(), s.getRandom());
            RocketEntity.launch(level, s, origin, dir, RocketEntity.Kind.ROCKET);
            s.rockets--;
            s.rocketCooldown = 90;
            return;
        }
        if (vsArmour) return; // small arms don't hurt armour; don't waste ammo
        if (s.grenadeCooldown <= 0 && lastSeen != null && dist > 7 && dist < 24
                && (!canSee && now - lastSeenTime < 80 || canSee && dist < 18 && s.getRandom().nextFloat() < 0.01f)) {
            throwGrenade(level, lastSeen);
            s.grenadeCooldown = 300 + s.getRandom().nextInt(300);
            return;
        }

        // ---------------- shooting
        ItemStack stack = s.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem)) return;
        if (GunLogic.reloading(stack)) return;
        if (GunLogic.ammo(stack) <= 0) {
            GunLogic.startReload(s, stack, gun, now);
            return;
        }
        int reaction = 8 + (int) (s.getSuppression() * 12) + (dist > 50 ? 8 : 0);
        s.shotDebt = Math.max(0, s.shotDebt - 1);
        if (pause > 0) {
            pause--;
            return;
        }
        if (!canSee || aimTicks < reaction || s.shotDebt > 0) return;
        FireMode mode = GunLogic.fireMode(stack, gun);
        if (burstLeft <= 0) {
            burstLeft = switch (mode) {
                case AUTO -> dist > 45 ? 2 + s.getRandom().nextInt(2) : 3 + s.getRandom().nextInt(dist < 20 ? 6 : 4);
                case BURST -> 3;
                default -> 1;
            };
        }
        Vec3 origin = s.getEyePosition();
        Vec3 dir = aimPoint.subtract(origin).normalize();
        GunLogic.aiShoot(level, s, gun, GunLogic.loadout(stack, gun), origin, dir, spread(gun, dist));
        stack.set(com.ditsjohann.warfront.registry.ModComponents.AMMO.get(), GunLogic.ammo(stack) - 1);
        s.shotDebt += gun.stats.shotInterval();
        if (--burstLeft <= 0) {
            pause = switch (mode) {
                case AUTO -> 8 + s.getRandom().nextInt(dist > 40 ? 18 : 12);
                case BURST -> 10 + s.getRandom().nextInt(10);
                case SEMI -> 3 + s.getRandom().nextInt(gun.category == WeaponCategory.SNIPER ? 20 : 8);
                case BOLT -> 6 + s.getRandom().nextInt(20);
            };
        }
    }

    /** Chest-height aim point with lead for target motion and compensation for bullet drop. */
    private Vec3 aimPoint(Entity t, GunType gun, double dist) {
        double h = t instanceof TankEntity ? t.getBbHeight() * 0.5 : t.getBbHeight() * 0.68;
        Vec3 p = t.position().add(0, h, 0);
        float vel = gun.stats.velocity;
        double flight = dist / vel;
        Vec3 motion = t.getDeltaMovement().multiply(1, 0, 1);
        p = p.add(motion.scale(flight * 0.85));
        double drop = 0.5 * gun.stats.gravity * flight * flight;
        return p.add(0, drop * 0.9, 0);
    }

    private float spread(GunType gun, double dist) {
        GunType.Stats st = gun.stats;
        float base = st.adsSpread + 0.7f;
        if (st.pellets > 1) base = 0.4f;
        boolean moving = !s.getNavigation().isDone();
        if (moving) base += st.hipSpread * 0.3f;
        float mult = 1f + 2.4f * s.getSuppression();
        mult *= aimTicks < 20 ? 1.8f : aimTicks < 45 ? 1.25f : 1f;
        if (s.getPose() == Pose.CROUCHING) mult *= 0.8f;
        if (gun.category == WeaponCategory.SNIPER && !moving) mult *= 0.55f;
        if (dist > 60) mult *= 0.85f; // long-range shots are taken more carefully
        return base * mult / WarfrontConfig.AI_ACCURACY.get().floatValue();
    }

    private void throwGrenade(ServerLevel level, Vec3 at) {
        Vec3 from = s.getEyePosition();
        Vec3 delta = at.subtract(from);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        // lob at ~40 degrees; solve speed for range under the grenade's gravity
        double g = FragGrenadeEntity.GRAVITY;
        double angle = Math.toRadians(38);
        double v = Math.sqrt(horiz * g / Math.sin(2 * angle)) * 1.06;
        v = Math.min(v, 1.6);
        Vec3 flat = new Vec3(delta.x, 0, delta.z).normalize();
        Vec3 vel = flat.scale(Math.cos(angle) * v).add(0, Math.sin(angle) * v, 0);
        s.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        FragGrenadeEntity.throwFrom(level, s, from, vel, 50);
    }
}
