package com.ditsjohann.warfront.combat;

import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 3D spotting. Looking at an enemy and pressing the spot key marks them (glowing outline +
 * minimap) for 8 seconds; a teammate killing a spotted enemy pays the spotter a spot assist.
 * Gunfire also alerts nearby AI and reveals unsuppressed shooters.
 */
public final class Spotting {
    private record Spot(UUID by, long time) {}

    private static final Map<UUID, Spot> SPOTS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    public static final int SPOT_TICKS = 160;

    /** Spots the enemy closest to the centre of the spotter's view. Returns the spotted entity. */
    @Nullable
    public static Entity spot(LivingEntity spotter, double range, double coneDeg) {
        if (!(spotter.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime();
        if (spotter instanceof ServerPlayer && COOLDOWN.getOrDefault(spotter.getUUID(), 0L) > now) return null;
        Vec3 eye = spotter.getEyePosition();
        Vec3 look = spotter.getLookAngle();
        double cos = Math.cos(Math.toRadians(coneDeg));
        Entity best = null;
        double bestDot = cos;
        AABB area = spotter.getBoundingBox().inflate(range);
        for (Entity e : level.getEntities(spotter, area, e -> e.isAlive() && Teams.enemies(spotter, e))) {
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 to = c.subtract(eye);
            double dist = to.length();
            if (dist > range) continue;
            double dot = to.normalize().dot(look);
            // bias slightly toward nearer targets
            double score = dot + Math.min(0.02, 2.0 / Math.max(dist, 1) * 0.01);
            if (score <= bestDot) continue;
            if (level.clip(new ClipContext(eye, c, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, spotter)).getType() != HitResult.Type.MISS) continue;
            bestDot = score;
            best = e;
        }
        if (spotter instanceof ServerPlayer) COOLDOWN.put(spotter.getUUID(), now + 12);
        if (best != null) mark(level, best, spotter);
        return best;
    }

    public static void mark(ServerLevel level, Entity target, @Nullable LivingEntity by) {
        if (target instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, SPOT_TICKS, 0, false, false));
        } else {
            target.setGlowingTag(true);
            if (target instanceof com.ditsjohann.warfront.entity.TankEntity t) t.spottedUntil = level.getGameTime() + SPOT_TICKS;
        }
        if (by != null) {
            SPOTS.put(target.getUUID(), new Spot(by.getUUID(), level.getGameTime()));
            if (by instanceof ServerPlayer sp) {
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), ModSounds.get("spot"), SoundSource.PLAYERS, 0.6f, 1f);
                WarfrontNet.toPlayer(sp, new SimplePackets.Score(0, "ENEMY SPOTTED"));
            }
        }
    }

    public static boolean isSpotted(Entity e) {
        return e instanceof LivingEntity le ? le.hasEffect(MobEffects.GLOWING) : e.isCurrentlyGlowing();
    }

    static void onKilled(ServerLevel level, LivingEntity victim, @Nullable Entity killer) {
        Spot s = SPOTS.remove(victim.getUUID());
        if (s == null || level.getGameTime() - s.time > SPOT_TICKS + 40) return;
        if (killer != null && s.by.equals(killer.getUUID())) return;
        if (level.getEntity(s.by) instanceof ServerPlayer p && !Teams.allies(p, victim)) Scoring.award(p, 25, "SPOT BONUS");
    }

    /** An unsuppressed weapon fired: nearby enemy AI hear it and turn toward the threat. */
    public static void revealFiring(LivingEntity shooter) {
        if (!(shooter.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 4 != 0) return;
        for (SoldierEntity s : level.getEntitiesOfClass(SoldierEntity.class, shooter.getBoundingBox().inflate(40),
                s -> Teams.enemies(s, shooter) && s.getTarget() == null)) {
            s.hearGunfire(shooter);
        }
    }

    private Spotting() {}
}
