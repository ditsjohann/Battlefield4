package com.ditsjohann.warfront.weapon;

import com.ditsjohann.warfront.combat.Spotting;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative firing, reloading and ammo handling shared by players and AI. */
public final class GunLogic {
    /** Token bucket per shooter: refills at the weapon's fire rate, small burst allowance for jitter. */
    private static final Map<UUID, double[]> RATE = new ConcurrentHashMap<>();
    private static final Map<Entity, Long> LAST_SHOT = new WeakHashMap<>();

    public static Loadout loadout(ItemStack stack, GunType type) {
        Loadout l = stack.get(ModComponents.LOADOUT.get());
        return l == null ? type.defaultLoadout() : l;
    }

    public static int ammo(ItemStack stack) { return stack.getOrDefault(ModComponents.AMMO.get(), 0); }
    public static int reserve(ItemStack stack) { return stack.getOrDefault(ModComponents.RESERVE.get(), 0); }
    public static boolean reloading(ItemStack stack) { return stack.has(ModComponents.RELOAD_END.get()); }

    public static FireMode fireMode(ItemStack stack, GunType type) {
        FireMode[] order = type.stats.modeOrder;
        int i = stack.getOrDefault(ModComponents.FIRE_MODE.get(), 0);
        return order[Math.floorMod(i, order.length)];
    }

    /** Converts yaw/pitch (degrees) to a unit look vector. */
    public static Vec3 direction(float yaw, float pitch) {
        float f = pitch * Mth.DEG_TO_RAD, g = -yaw * Mth.DEG_TO_RAD;
        float h = Mth.cos(g), i = Mth.sin(g), j = Mth.cos(f), k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    /** Uniformly perturbs a direction inside a cone of the given half-angle. */
    public static Vec3 applySpread(Vec3 dir, float spreadDeg, RandomSource rnd) {
        if (spreadDeg <= 0) return dir;
        double r = Math.sqrt(rnd.nextDouble()) * Math.toRadians(spreadDeg);
        double a = rnd.nextDouble() * Math.PI * 2;
        Vec3 up = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 realUp = right.cross(dir).normalize();
        return dir.add(right.scale(Math.cos(a) * Math.tan(r))).add(realUp.scale(Math.sin(a) * Math.tan(r))).normalize();
    }

    /**
     * Player fire request. The client sends its final aim (spread already applied) and the server
     * verifies ammo, reload state, fire rate and that the aim is close to the player's view.
     */
    public static boolean playerShoot(ServerPlayer player, float yaw, float pitch) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun) || player.isSpectator()) return false;
        if (com.ditsjohann.warfront.combat.CombatEvents.isDowned(player)) return false;
        GunType type = gun.type;
        if (reloading(stack) || ammo(stack) <= 0) return false;
        if (!consumeToken(player.getUUID(), type)) return false;
        Vec3 aim = direction(yaw, pitch);
        Vec3 look = player.getLookAngle();
        if (aim.dot(look) < Math.cos(Math.toRadians(14))) aim = look; // reject implausible aim
        stack.set(ModComponents.AMMO.get(), ammo(stack) - 1);
        EffectiveStats stats = EffectiveStats.of(type, loadout(stack, type));
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        for (int i = 0; i < type.stats.pellets; i++) {
            Vec3 d = type.stats.pellets > 1 ? applySpread(aim, type.stats.adsSpread, player.getRandom()) : aim;
            BulletManager.fire(level, player, stats, origin, d, player);
        }
        afterShot(level, player, stats, player);
        return true;
    }

    /** AI fire: spread is applied here from the soldier's accuracy. */
    public static void aiShoot(ServerLevel level, LivingEntity shooter, GunType type, Loadout loadout, Vec3 origin, Vec3 aim, float spreadDeg) {
        EffectiveStats stats = EffectiveStats.of(type, loadout);
        for (int i = 0; i < type.stats.pellets; i++) {
            float s = spreadDeg + (type.stats.pellets > 1 ? type.stats.adsSpread : 0);
            BulletManager.fire(level, shooter, stats, origin, applySpread(aim, s, shooter.getRandom()), null);
        }
        afterShot(level, shooter, stats, null);
    }

    private static void afterShot(ServerLevel level, LivingEntity shooter, EffectiveStats stats, @Nullable ServerPlayer except) {
        LAST_SHOT.put(shooter, level.getGameTime());
        String sound = stats.suppressed() ? "suppressed_shot" : stats.type().stats.sound;
        float pitch = stats.type().stats.pitch * (0.96f + shooter.getRandom().nextFloat() * 0.08f);
        playLayered(level, shooter.getEyePosition(), sound, stats.suppressed() ? null : sound + "_far", except, 1.6f, pitch, stats.suppressed() ? 24 : 48);
        if (!stats.suppressed()) Spotting.revealFiring(shooter);
    }

    public static long lastShotTime(Entity e) {
        return LAST_SHOT.getOrDefault(e, -1000L);
    }

    /**
     * Distance-layered gunfire: listeners within {@code nearRange} hear the full report, listeners
     * further away hear a low, reverberant distant report (the "battle in the distance" effect).
     */
    public static void playLayered(ServerLevel level, Vec3 pos, String near, @Nullable String far, @Nullable ServerPlayer except, float volume, float pitch, double nearRange) {
        SoundEvent nearEv = ModSounds.get(near);
        SoundEvent farEv = far != null && ModSounds.exists(far) ? ModSounds.get(far) : null;
        long seed = level.random.nextLong();
        for (ServerPlayer p : level.players()) {
            if (p == except) continue;
            double d = p.position().distanceTo(pos);
            if (d <= nearRange) {
                send(p, nearEv, pos, volume * (float) (1 - d / (nearRange * 1.6)) + 0.4f, pitch, seed);
            } else if (farEv != null && d < 260) {
                // place the distant sound near the listener, in the shooter's direction, so attenuation stays audible
                Vec3 dir = pos.subtract(p.position()).normalize();
                Vec3 at = p.position().add(dir.scale(10));
                float v = (float) Math.max(0.12, 1.0 - d / 280.0) * 0.9f;
                send(p, farEv, at, v, pitch * 0.95f, seed);
            }
        }
    }

    private static void send(ServerPlayer p, SoundEvent ev, Vec3 pos, float vol, float pitch, long seed) {
        Holder<SoundEvent> h = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ev);
        p.connection.send(new ClientboundSoundPacket(h, SoundSource.PLAYERS, pos.x, pos.y, pos.z, vol, pitch, seed));
    }

    private static boolean consumeToken(UUID id, GunType type) {
        float interval = type.stats.shotInterval();
        double now = System.nanoTime();
        double[] bucket = RATE.computeIfAbsent(id, k -> new double[]{2, now});
        double elapsedTicks = (now - bucket[1]) / 50_000_000.0;
        bucket[1] = now;
        bucket[0] = Math.min(2.5, bucket[0] + elapsedTicks / interval);
        if (bucket[0] < 0.85) return false;
        bucket[0] -= 1;
        return true;
    }

    /** Starts a reload if possible. */
    public static boolean startReload(LivingEntity holder, ItemStack stack, GunType type, long gameTime) {
        if (reloading(stack)) return false;
        int ammo = ammo(stack);
        boolean infinite = holder instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().instabuild;
        if (ammo >= type.stats.magSize || (reserve(stack) <= 0 && !infinite)) return false;
        int len = ammo == 0 ? type.stats.reloadEmptyTicks : type.stats.reloadTicks;
        stack.set(ModComponents.RELOAD_END.get(), gameTime + len);
        stack.set(ModComponents.RELOAD_LENGTH.get(), len);
        holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(), ModSounds.get("reload_mag_out"), SoundSource.PLAYERS, 0.7f, 1f);
        return true;
    }

    /** Completes a reload whose timer has elapsed. */
    public static void tickReload(LivingEntity holder, ItemStack stack, GunType type, long gameTime, boolean selected) {
        Long end = stack.get(ModComponents.RELOAD_END.get());
        if (end == null) return;
        if (!selected) {
            stack.remove(ModComponents.RELOAD_END.get());
            return;
        }
        int len = stack.getOrDefault(ModComponents.RELOAD_LENGTH.get(), 40);
        long start = end - len;
        if (gameTime == start + len / 2) {
            holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(), ModSounds.get("reload_mag_in"), SoundSource.PLAYERS, 0.7f, 1f);
        }
        if (gameTime >= end) {
            boolean infinite = holder instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().instabuild;
            int need = type.stats.magSize - ammo(stack);
            int take = infinite ? need : Math.min(need, reserve(stack));
            stack.set(ModComponents.AMMO.get(), ammo(stack) + take);
            if (!infinite) stack.set(ModComponents.RESERVE.get(), reserve(stack) - take);
            stack.remove(ModComponents.RELOAD_END.get());
            if (type.category == WeaponCategory.SNIPER || type.category == WeaponCategory.SHOTGUN) {
                holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(), ModSounds.get("reload_bolt"), SoundSource.PLAYERS, 0.7f, 1f);
            }
        }
    }

    /** Refills spare ammo (ammo bags, resupply crates). Returns rounds added. */
    public static int resupply(ItemStack stack, GunType type, float magsFraction) {
        int max = type.stats.reserve;
        int cur = reserve(stack);
        if (cur >= max) return 0;
        int add = Math.max(1, Math.round(type.stats.magSize * magsFraction));
        int now = Math.min(max, cur + add);
        stack.set(ModComponents.RESERVE.get(), now);
        return now - cur;
    }

    private GunLogic() {}
}
