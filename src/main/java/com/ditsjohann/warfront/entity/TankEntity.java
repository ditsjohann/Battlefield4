package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.weapon.BulletManager;
import com.ditsjohann.warfront.weapon.EffectiveStats;
import com.ditsjohann.warfront.weapon.GunLogic;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * MBT-9 "Juggernaut" main battle tank. The driver is also the gunner: the turret follows their
 * view, left click fires the main gun, holding right click runs the coaxial machine gun. A second
 * soldier can ride on the rear deck and fight with their own weapon.
 */
public class TankEntity extends VehicleEntity {
    public static final float MAX_HEALTH = 100f;
    public static final int CANNON_RELOAD = 80;
    private int coaxCooldown;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    @Nullable
    public static TankEntity spawn(ServerLevel level, BlockPos pos, Faction f, @Nullable UUID matchId) {
        TankEntity t = ModEntities.TANK.get().create(level);
        if (t == null) return null;
        float yaw = f == Faction.COALITION ? 0 : 180;
        t.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        t.init(f, matchId, yaw);
        level.addFreshEntity(t);
        return t;
    }

    @Override public float maxHealth() { return MAX_HEALTH; }
    @Override public float disabledAt() { return 22f; }
    @Override protected double maxSpeed() { return 0.30; }
    @Override protected double reverseSpeed() { return 0.14; }
    @Override protected double accel() { return 0.011; }
    @Override protected float turnRate() { return 2.2f; }
    @Override protected int seats() { return 2; }
    @Override public int gunnerSeat() { return 0; }
    @Override public String vehicleName() { return "MBT-9 Juggernaut"; }

    @Override
    protected Vec3 seatOffset(int seat) {
        if (seat == 0) {
            // commander's hatch on the turret
            float ty = turretYaw() * Mth.DEG_TO_RAD;
            return new Vec3(Mth.sin(-ty) * -0.35, 1.72, Mth.cos(ty) * -0.35);
        }
        return hullOffset(0, 1.25, -1.7);
    }

    @Override
    public String weaponStatus() {
        return disabled() ? "DISABLED" : reloadTicks() > 0 ? "LOADING" : "CANNON READY";
    }

    @Override
    public float weaponReadiness() {
        return 1f - reloadTicks() / (float) CANNON_RELOAD;
    }

    @Override
    public void handleInput(ServerPlayer p, int action) {
        if (seatOf(p) != gunnerSeat()) return;
        if (action == IN_ATTACK_PRESS) fireCannon(p.serverLevel(), p);
        else super.handleInput(p, action);
    }

    @Override
    protected void tickWeapons(ServerLevel level, @Nullable LivingEntity gunner) {
        if (coaxCooldown > 0) coaxCooldown--;
        if (gunner != null && !(gunner instanceof SoldierEntity) && useHeld) fireCoax(level, gunner);
    }

    // ------------------------------------------------------------------ weapons
    public Vec3 barrelDir() {
        float ty = turretYaw() * Mth.DEG_TO_RAD, bp = barrelPitch() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(ty) * Mth.cos(bp), -Mth.sin(bp), Mth.cos(ty) * Mth.cos(bp));
    }

    public Vec3 muzzle() {
        return position().add(0, 1.55, 0).add(barrelDir().scale(3.6));
    }

    public void fireCannon(ServerLevel level, LivingEntity gunner) {
        if (reloadTicks() > 0 || disabled() && random.nextFloat() < 0.5f) return;
        setReload(CANNON_RELOAD);
        RocketEntity.launch(level, gunner, muzzle(), barrelDir(), RocketEntity.Kind.TANK_SHELL);
        Vec3 recoil = barrelDir().scale(-0.15);
        setDeltaMovement(getDeltaMovement().add(recoil.x, 0, recoil.z));
    }

    /** Kept for compatibility with scripted callers (AI, tests). */
    public void setCoax(boolean on) { useHeld = on; }

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

    @Override
    protected void aiGun(ServerLevel level, SoldierEntity crew, @Nullable Entity target) {
        if (target == null) {
            aimTurret(getYRot(), 0);
            return;
        }
        float[] a = aimAngles(position().add(0, 1.55, 0), target.getBoundingBox().getCenter());
        aimTurret(a[0], a[1]);
        if (!aligned(a[0], a[1], 3f)) return;
        double d = distanceTo(target);
        if (target instanceof VehicleEntity || d > 18 || random.nextFloat() < 0.3f) fireCannon(level, crew);
        if (target instanceof LivingEntity && d < 45) fireCoax(level, crew);
    }
}
