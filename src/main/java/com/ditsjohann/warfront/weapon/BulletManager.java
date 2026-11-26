package com.ditsjohann.warfront.weapon;

import com.ditsjohann.warfront.WarfrontConfig;
import com.ditsjohann.warfront.combat.CombatEvents;
import com.ditsjohann.warfront.combat.Suppression;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.TracerPacket;
import com.ditsjohann.warfront.registry.ModDamageTypes;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Server-side ballistic simulation. Bullets are not entities: each live round is a tiny record
 * that advances one segment per tick under gravity, sweeping a raycast against blocks and entity
 * hitboxes along that segment. This gives exact hit registration at any velocity, real bullet
 * drop and travel time, and costs a fraction of an entity per round.
 *
 * <p>Clients receive one {@link TracerPacket} per round and simulate the visual tracer locally.
 */
@Mod.EventBusSubscriber(modid = com.ditsjohann.warfront.Warfront.MODID)
public final class BulletManager {
    private static final Map<ServerLevel, List<Bullet>> LIVE = new WeakHashMap<>();
    private static final int MAX_LIFE = 80;
    private static final double SUPPRESS_RADIUS = 3.0;

    public static final class Bullet {
        final @Nullable Entity owner;
        final GunType gun;
        final float velocityScale;
        final float rangeMult;
        Vec3 pos;
        Vec3 vel;
        final Vec3 origin;
        final float gravity;
        float damageScale = 1f;
        int penetration = 2;
        int age;
        final boolean suppressed;
        final Set<Entity> alreadySuppressed = new HashSet<>();
        final Set<Entity> alreadyHit = new HashSet<>();

        Bullet(@Nullable Entity owner, GunType gun, Vec3 origin, Vec3 vel, float rangeMult, boolean suppressed) {
            this.owner = owner;
            this.gun = gun;
            this.origin = origin;
            this.pos = origin;
            this.vel = vel;
            this.gravity = gun.stats.gravity;
            this.velocityScale = 1f;
            this.rangeMult = rangeMult;
            this.suppressed = suppressed;
        }
    }

    /**
     * Fires one round. {@code direction} must already include spread.
     * @return the bullet, for tests.
     */
    public static Bullet fire(ServerLevel level, @Nullable Entity owner, EffectiveStats stats, Vec3 origin, Vec3 direction, @Nullable ServerPlayer excludeTracer) {
        Vec3 vel = direction.normalize().scale(stats.velocity());
        Bullet b = new Bullet(owner, stats.type(), origin, vel, stats.rangeMult(), stats.suppressed());
        LIVE.computeIfAbsent(level, l -> new ArrayList<>()).add(b);
        TracerPacket tp = new TracerPacket(owner == null ? -1 : owner.getId(), origin, vel, b.gravity,
                stats.type().stats.tracerColor, stats.suppressed());
        for (ServerPlayer p : level.players()) {
            if (p == excludeTracer) continue;
            if (p.distanceToSqr(origin) < 192 * 192) WarfrontNet.CHANNEL.send(tp, PacketDistributor.PLAYER.with(p));
        }
        return b;
    }

    public static int liveCount(ServerLevel level) {
        List<Bullet> l = LIVE.get(level);
        return l == null ? 0 : l.size();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level instanceof ServerLevel level)) return;
        List<Bullet> bullets = LIVE.get(level);
        if (bullets == null || bullets.isEmpty()) return;
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            if (!step(level, b)) it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LIVE.clear();
    }

    /** Advances a bullet one tick. Returns false when the bullet is spent. */
    static boolean step(ServerLevel level, Bullet b) {
        if (++b.age > MAX_LIFE || b.pos.y < level.getMinBuildHeight() - 16) return false;
        Vec3 from = b.pos;
        Vec3 to = from.add(b.vel);
        if (!level.isLoaded(BlockPos.containing(to))) return false;

        // --- block collision along the segment
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 segEnd = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();

        // --- entity collision: nearest hitbox crossing the segment before the block hit
        AABB sweep = new AABB(from, segEnd).inflate(1.0);
        Entity bestEntity = null;
        Vec3 bestPoint = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : level.getEntities(b.owner, sweep, BulletManager::hittable)) {
            if (b.alreadyHit.contains(e)) continue;
            if (b.owner != null && (e == b.owner.getVehicle() || e.getVehicle() == b.owner)) continue;
            if (!Teams.canHurt(b.owner, e) && Teams.allies(b.owner, e)) continue; // rounds pass through teammates
            AABB box = e.getBoundingBox().inflate(e instanceof LivingEntity ? 0.08 : 0.0);
            Optional<Vec3> hit = box.clip(from, segEnd);
            if (hit.isEmpty() && box.contains(from)) hit = Optional.of(from);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(from);
                if (d < bestDist) {
                    bestDist = d;
                    bestEntity = e;
                    bestPoint = hit.get();
                }
            }
        }

        // --- suppression: enemies the round passes close to
        suppressNear(level, b, from, bestPoint != null ? bestPoint : segEnd);

        if (bestEntity != null) {
            b.alreadyHit.add(bestEntity);
            hitEntity(level, b, bestEntity, bestPoint);
            return false; // rounds stop in the first body they hit
        }

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            if (!hitBlock(level, b, blockHit)) return false;
            // penetrated: continue from where the round exits the block
            Vec3 dir = b.vel.normalize();
            Vec3 p = blockHit.getLocation();
            BlockPos hitPos = blockHit.getBlockPos();
            for (int i = 0; i < 20 && BlockPos.containing(p).equals(hitPos); i++) p = p.add(dir.scale(0.1));
            b.pos = p.add(dir.scale(0.02));
        } else {
            b.pos = to;
        }
        b.vel = b.vel.add(0, -b.gravity, 0).scale(0.995);
        return true;
    }

    private static boolean hittable(Entity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        if (e instanceof ItemEntity || e instanceof Projectile) return false;
        if (e instanceof LivingEntity le) return !(le instanceof ArmorStand a && a.isMarker());
        return e.isPickable() || e instanceof BulletTarget;
    }

    private static void hitEntity(ServerLevel level, Bullet b, Entity target, Vec3 point) {
        double dist = point.distanceTo(b.origin);
        float damage = b.gun.stats.damageAt(dist / b.rangeMult) * b.damageScale * WarfrontConfig.DAMAGE_MULTIPLIER.get().floatValue();
        boolean headshot = false;
        if (target instanceof LivingEntity le && le.getBbHeight() > 1.2f && le.getBbHeight() < 3.0f) {
            double headBottom = le.getEyeY() - 0.13; // the head proper, not the shoulders
            if (point.y >= headBottom) {
                headshot = true;
                damage *= b.gun.stats.headMult;
            }
        }
        if (target instanceof BulletTarget bt) {
            bt.onBulletHit(b.owner, damage);
            level.playSound(null, point.x, point.y, point.z, ModSounds.get("ricochet"), SoundSource.PLAYERS, 0.8f, 0.9f + level.random.nextFloat() * 0.3f);
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 4, 0.05, 0.05, 0.05, 0.2);
            return;
        }
        DamageSource src = ModDamageTypes.source(level, headshot ? ModDamageTypes.HEADSHOT : ModDamageTypes.BULLET, b.owner, b.owner);
        float before = target instanceof LivingEntity le ? le.getHealth() : 0;
        CombatEvents.recordWeapon(target, b.gun, headshot);
        boolean hurt = target.hurt(src, damage);
        if (target instanceof LivingEntity le) {
            boolean killed = hurt && (le.isDeadOrDying() || CombatEvents.isDowned(le) && before > 0 && le.getHealth() <= 1.01f && before > 1.01f);
            if (hurt) {
                // small directional punch instead of vanilla knockback
                Vec3 push = b.vel.normalize().scale(0.05 + damage * 0.006);
                le.push(push.x, 0.02, push.z);
                le.hurtMarked = true;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                        point.x, point.y, point.z, headshot ? 10 : 5, 0.08, 0.08, 0.08, 0.15);
                CombatEvents.onBulletHit(b.owner, le, b.gun, damage, headshot, killed);
            }
        }
    }

    /** Returns true when the bullet penetrates and keeps flying. */
    private static boolean hitBlock(ServerLevel level, Bullet b, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Vec3 p = hit.getLocation();
        boolean breakable = WarfrontConfig.BULLETS_BREAK_GLASS.get() && isShatterable(state);
        if (breakable) {
            level.destroyBlock(pos, false, b.owner);
            level.playSound(null, pos, ModSounds.get("glass_shatter"), SoundSource.BLOCKS, 0.8f, 0.9f + level.random.nextFloat() * 0.3f);
            b.damageScale *= 0.9f;
            return true;
        }
        // breaching: shotgun pellets blow doors and trapdoors off their hinges
        if (b.gun == GunType.SG12 && (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock)
                && state.getDestroySpeed(level, pos) >= 0 && state.getDestroySpeed(level, pos) < 4 && level.random.nextFloat() < 0.2f) {
            level.destroyBlock(pos, true, b.owner);
            return false;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 6, 0.05, 0.05, 0.05, 0.1);
        level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
        if (level.random.nextFloat() < 0.35f) {
            level.playSound(null, p.x, p.y, p.z, ModSounds.get(level.random.nextFloat() < 0.25f ? "ricochet" : "bullet_impact"), SoundSource.PLAYERS, 0.6f, 0.9f + level.random.nextFloat() * 0.25f);
        }
        if (b.penetration > 0 && isThin(state)) {
            b.penetration--;
            b.damageScale *= 0.55f;
            return true;
        }
        return false;
    }

    public static boolean isShatterable(BlockState s) {
        Block block = s.getBlock();
        return block instanceof TransparentBlock && !(block instanceof SlimeBlock) && !(block instanceof HoneyBlock)
                || block instanceof StainedGlassPaneBlock || block == Blocks.GLASS_PANE
                || block instanceof IceBlock || s.is(BlockTags.LEAVES) || block instanceof FlowerPotBlock
                || block instanceof TintedGlassBlock;
    }

    /** Thin or soft materials a rifle round can punch through. */
    public static boolean isThin(BlockState s) {
        Block block = s.getBlock();
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS)
                || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WOODEN_SLABS)
                || block instanceof IronBarsBlock || block instanceof CarpetBlock || s.is(BlockTags.SIGNS)
                || block instanceof BarrelBlock || block instanceof ChestBlock || block == Blocks.HAY_BLOCK;
    }

    private static void suppressNear(ServerLevel level, Bullet b, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(SUPPRESS_RADIUS);
        Vec3 seg = to.subtract(from);
        double len2 = seg.lengthSqr();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != b.owner && e.isAlive())) {
            if (b.alreadySuppressed.contains(e) || !Teams.enemies(b.owner, e)) continue;
            Vec3 c = e.getEyePosition();
            double t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, c.subtract(from).dot(seg) / len2));
            Vec3 closest = from.add(seg.scale(t));
            double d = closest.distanceTo(c);
            if (d < SUPPRESS_RADIUS && d > 0.25) {
                b.alreadySuppressed.add(e);
                float amount = b.gun.stats.suppression * (float) (1.0 - d / SUPPRESS_RADIUS) * 1.6f;
                Suppression.apply(e, b.owner, amount, closest);
            }
        }
    }

    private BulletManager() {}
}
