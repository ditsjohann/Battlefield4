package com.ditsjohann.warfront.combat;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.WarfrontConfig;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.MatchManager;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.registry.ModDamageTypes;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Central combat bookkeeping: hit confirmation, the downed/revive rule, kill feed, tickets and
 * score attribution (kills, headshots, assists, spot and suppression assists).
 */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class CombatEvents {
    private record WeaponRecord(String weapon, boolean headshot, long time) {}

    private static final Map<UUID, WeaponRecord> LAST_WEAPON = new HashMap<>();
    /** victim -> (attacker -> damage) for assists. */
    private static final Map<UUID, Map<UUID, Float>> DAMAGE_LOG = new HashMap<>();
    /** Entities whose death has already been counted (they were downed first). */
    private static final Map<Entity, Boolean> COUNTED = new WeakHashMap<>();

    public static void recordWeapon(Entity target, GunType gun, boolean headshot) {
        LAST_WEAPON.put(target.getUUID(), new WeaponRecord(gun.displayName, headshot, target.level().getGameTime()));
    }

    public static void recordWeapon(Entity target, String weapon) {
        LAST_WEAPON.put(target.getUUID(), new WeaponRecord(weapon, false, target.level().getGameTime()));
    }

    public static boolean isDowned(LivingEntity e) {
        if (e instanceof SoldierEntity s) return s.isDowned();
        if (e instanceof Player p) return DownedState.isDowned(p);
        return false;
    }

    /** Called by the ballistics engine after a round damages a living target. */
    public static void onBulletHit(@Nullable Entity shooter, LivingEntity target, GunType gun, float damage, boolean headshot, boolean killed) {
        if (shooter != null) {
            DAMAGE_LOG.computeIfAbsent(target.getUUID(), k -> new HashMap<>()).merge(shooter.getUUID(), damage, Float::sum);
        }
        if (shooter instanceof ServerPlayer sp) {
            int flags = 1 | (headshot ? 2 : 0) | (killed ? 4 : 0);
            WarfrontNet.toPlayer(sp, new SimplePackets.HitMarker(flags, damage));
        }
        if (target instanceof SoldierEntity s && shooter instanceof LivingEntity le) s.onShotBy(le);
    }

    /** Any damage while downed is ignored except the bleed-out itself. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        if (isDowned(e) && !event.getSource().is(ModDamageTypes.BLEED_OUT) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker != e && !Teams.canHurt(attacker, e)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource src = event.getSource();
        boolean combatant = victim instanceof SoldierEntity || victim instanceof Player;
        if (!combatant) {
            Entity killer = src.getEntity();
            if (killer instanceof ServerPlayer sp && victim instanceof net.minecraft.world.entity.monster.Enemy) {
                Scoring.award(sp, 20, "HOSTILE NEUTRALIZED");
            }
            return;
        }
        ConquestMatch match = MatchManager.active(level);
        boolean inMatch = match != null && match.isLive() && Teams.of(victim) != null;
        if (inMatch && !src.is(ModDamageTypes.BLEED_OUT) && !isDowned(victim) && canBeDowned(victim, src)) {
            event.setCanceled(true);
            victim.setHealth(1f);
            victim.clearFire();
            if (victim instanceof SoldierEntity s) s.setDowned(true);
            else if (victim instanceof ServerPlayer p) DownedState.down(p);
            announceKill(level, victim, src);
            COUNTED.put(victim, true);
            return;
        }
        if (!COUNTED.containsKey(victim)) announceKill(level, victim, src);
        COUNTED.remove(victim);
        if (victim instanceof ServerPlayer p) DownedState.clear(p, false);
    }

    private static boolean canBeDowned(LivingEntity victim, DamageSource src) {
        if (src.is(DamageTypeTags.IS_EXPLOSION) || src.is(ModDamageTypes.HEADSHOT) || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (victim instanceof Player) return WarfrontConfig.PLAYER_DOWNED_STATE.get();
        return victim.getRandom().nextFloat() < 0.6f;
    }

    /** A combatant was taken out of the fight: tickets, kill feed and score. */
    private static void announceKill(ServerLevel level, LivingEntity victim, DamageSource src) {
        Entity killer = src.getEntity();
        WeaponRecord wr = LAST_WEAPON.remove(victim.getUUID());
        String weapon;
        boolean headshot = false;
        if (wr != null && level.getGameTime() - wr.time < 10 && (src.is(ModDamageTypes.BULLET) || src.is(ModDamageTypes.HEADSHOT))) {
            weapon = wr.weapon;
            headshot = wr.headshot;
        } else if (src.is(DamageTypeTags.IS_EXPLOSION)) {
            weapon = wr != null && level.getGameTime() - wr.time < 10 ? wr.weapon : "Explosive";
        } else if (src.is(ModDamageTypes.CRUSHED)) {
            weapon = "Roadkill";
        } else if (killer != null) {
            weapon = "Melee";
        } else {
            weapon = src.getMsgId();
        }
        Faction vf = Teams.of(victim);
        Faction kf = Teams.of(killer);
        ConquestMatch match = MatchManager.active(level);
        if (match != null && vf != null) match.onCombatantLost(level, victim, vf);
        String killerName = killer == null ? "" : killer.getDisplayName().getString();
        if (killer != null && killer.getVehicle() instanceof com.ditsjohann.warfront.entity.TankEntity && weapon.equals("Melee")) weapon = "MBT-9 Juggernaut";
        SimplePackets.KillFeed feed = new SimplePackets.KillFeed(killerName, kf == null ? -1 : kf.ordinal(),
                victim.getDisplayName().getString(), vf == null ? -1 : vf.ordinal(), weapon, headshot);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(victim) < 400 * 400) WarfrontNet.toPlayer(p, feed);
        }
        // ---- scoring
        Map<UUID, Float> log = DAMAGE_LOG.remove(victim.getUUID());
        if (killer instanceof ServerPlayer sp && killer != victim && !Teams.allies(sp, victim)) {
            Scoring.award(sp, 100, victim instanceof Player ? "ENEMY KILLED" : "ENEMY SOLDIER KILLED");
            if (headshot) Scoring.award(sp, 25, "HEADSHOT BONUS");
            Scoring.countKill(sp);
            if (match != null) match.addKill(sp);
        }
        if (log != null) {
            float total = 0;
            for (float f : log.values()) total += f;
            for (Map.Entry<UUID, Float> en : log.entrySet()) {
                if (killer != null && en.getKey().equals(killer.getUUID())) continue;
                if (level.getEntity(en.getKey()) instanceof ServerPlayer ap && !Teams.allies(ap, victim)) {
                    int pts = Math.max(10, Math.min(90, Math.round(100 * en.getValue() / Math.max(total, victim.getMaxHealth()))));
                    Scoring.award(ap, pts, "KILL ASSIST");
                }
            }
        }
        Spotting.onKilled(level, victim, killer);
        Suppression.onKilled(level, victim, killer);
        if (victim instanceof ServerPlayer vp) {
            Scoring.countDeath(vp);
            if (match != null) match.addDeath(vp);
        }
    }

    /** Revive a downed combatant. Refunds the ticket like BF4's defibrillator. */
    public static boolean revive(ServerLevel level, LivingEntity target, @Nullable LivingEntity reviver) {
        if (!isDowned(target)) return false;
        if (target instanceof SoldierEntity s) s.setDowned(false);
        else if (target instanceof ServerPlayer p) DownedState.clear(p, true);
        target.setHealth(Math.min(target.getMaxHealth(), 10f));
        COUNTED.remove(target);
        Faction f = Teams.of(target);
        ConquestMatch match = MatchManager.active(level);
        if (match != null && f != null) match.refundTicket(f);
        if (reviver instanceof ServerPlayer rp) Scoring.award(rp, 100, "REVIVE");
        level.playSound(null, target.getX(), target.getY(), target.getZ(), com.ditsjohann.warfront.registry.ModSounds.get("defib"), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        return true;
    }

    /** Kit items never drop: they vanish with the soldier, keeping the battlefield clean. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(ie -> ie.getItem().has(ModComponents.KIT_ITEM.get()));
    }

    private CombatEvents() {}
}
