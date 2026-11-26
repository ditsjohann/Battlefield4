package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.registry.ModEntities;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.BulletManager;
import com.ditsjohann.warfront.weapon.EffectiveStats;
import com.ditsjohann.warfront.weapon.GunLogic;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * FAV-3 "Jackal" fast attack vehicle: a light, quick two-seater. The driver drives; the gunner
 * stands at a rear-mounted heavy machine gun that overheats under sustained fire. Thin armour —
 * rifles hurt it — but it is the fastest way to a flag and can run circles around infantry.
 */
public class BuggyEntity extends VehicleEntity {
    public static final float MAX_HEALTH = 45f;
    private static final int OVERHEAT_LOCK = 60;
    private int fireCooldown;
    private int overheatLock;

    public BuggyEntity(EntityType<? extends BuggyEntity> type, Level level) {
        super(type, level);
    }

    @Nullable
    public static BuggyEntity spawn(ServerLevel level, BlockPos pos, Faction f, @Nullable UUID matchId) {
        BuggyEntity b = ModEntities.BUGGY.get().create(level);
        if (b == null) return null;
        float yaw = f == Faction.COALITION ? 0 : 180;
        b.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        b.init(f, matchId, yaw);
        level.addFreshEntity(b);
        return b;
    }

    @Override public float maxHealth() { return MAX_HEALTH; }
    @Override public float disabledAt() { return 10f; }
    @Override protected double maxSpeed() { return 0.52; }
    @Override protected double reverseSpeed() { return 0.2; }
    @Override protected double accel() { return 0.024; }
    @Override protected float turnRate() { return 3.8f; }
    @Override protected int seats() { return 2; }
    @Override public int gunnerSeat() { return 1; }
    @Override public String vehicleName() { return "FAV-3 Jackal"; }
    @Override protected float smallArmsFactor() { return 0.22f; }
    @Override protected float roadkillDamage() { return 10f; }
    @Override protected boolean crushesHardCover() { return false; }
    @Override protected float minPitch() { return -35f; }
    @Override protected float maxPitch() { return 18f; }
    @Override protected float turretRate() { return 9f; }
    @Override protected float destroyBlast() { return 3f; }
    @Override protected float enginePitch() { return 1.45f; }
    @Override protected AiStyle aiStyle() { return AiStyle.CIRCLE; }
    @Override protected double aiRange() { return 70; }

    /** Heat 0..100 is stored in the shared reload field. */
    public int heat() { return reloadTicks(); }

    public boolean overheated() { return overheatLock > 0; }

    @Override
    protected Vec3 seatOffset(int seat) {
        return seat == 0 ? hullOffset(-0.38, 0.45, 0.25) : hullOffset(0, 0.95, -0.75);
    }

    @Override
    public String weaponStatus() {
        return disabled() ? "DISABLED" : overheatLock > 0 ? "OVERHEATED" : gunner() == null ? "NO GUNNER" : "HMG READY";
    }

    @Override
    public float weaponReadiness() {
        return 1f - heat() / 100f;
    }

    public Vec3 gunPivot() {
        return position().add(hullOffset(0, 1.55, -0.55));
    }

    public Vec3 gunDir() {
        float ty = turretYaw() * Mth.DEG_TO_RAD, bp = barrelPitch() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(ty) * Mth.cos(bp), -Mth.sin(bp), Mth.cos(ty) * Mth.cos(bp));
    }

    @Override
    protected void tickWeapons(ServerLevel level, @Nullable LivingEntity gunner) {
        if (fireCooldown > 0) fireCooldown--;
        if (overheatLock > 0) {
            overheatLock--;
            if (overheatLock == 0) setReload(30);
        } else if (heat() > 0 && tickCount % 2 == 0) setReload(heat() - 1);
        if (gunner != null && !(gunner instanceof SoldierEntity) && attackHeld) fire(level, gunner, 1.1f);
        if (gunner instanceof SoldierEntity s) aiGun(level, s, findTarget(s, aiRange()));
    }

    private void fire(ServerLevel level, LivingEntity gunner, float spread) {
        if (fireCooldown > 0 || overheatLock > 0) return;
        fireCooldown = 2;
        Vec3 origin = gunPivot().add(gunDir().scale(1.1));
        EffectiveStats stats = EffectiveStats.of(GunType.HMG240, GunType.HMG240.defaultLoadout());
        BulletManager.fire(level, gunner, stats, origin, GunLogic.applySpread(gunDir(), spread, random), null);
        GunLogic.playLayered(level, origin, "lmg_shot", "lmg_shot_far", null, 1.5f, 0.82f, 48);
        int h = heat() + 4;
        if (h >= 100) {
            overheatLock = OVERHEAT_LOCK;
            setReload(100);
            level.playSound(null, origin.x, origin.y, origin.z, ModSounds.get("dry_fire"), SoundSource.NEUTRAL, 1f, 0.6f);
        } else setReload(h);
    }

    @Override
    protected void aiGun(ServerLevel level, SoldierEntity gunner, @Nullable Entity target) {
        if (gunnerSeat() != seatOf(gunner)) return; // the driver doesn't shoot
        if (target == null) {
            aimTurret(getYRot(), 0);
            return;
        }
        float[] a = aimAngles(gunPivot(), target.getBoundingBox().getCenter());
        aimTurret(a[0], a[1]);
        // AI fires in bursts and lets the gun cool before it locks up
        if (aligned(a[0], a[1], 5f) && heat() < 80 && tickCount % 30 < 16) fire(level, gunner, 1.8f);
    }
}
