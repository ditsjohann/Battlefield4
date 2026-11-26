package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.combat.Spotting;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.SoldierRole;
import com.ditsjohann.warfront.entity.TankEntity;
import com.ditsjohann.warfront.entity.BuggyEntity;
import com.ditsjohann.warfront.entity.VehicleEntity;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.PlayerProgress;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * One Conquest battle: flags, tickets, armies, squads and the Levolution tower.
 *
 * <p>Rules (after BF4 Conquest): out-number the enemy inside a flag's radius to neutralise and
 * then capture it. The side holding more flags makes the other bleed tickets; every soldier
 * taken out costs a ticket, refunded on revive. First side at zero tickets loses.
 */
public final class ConquestMatch {
    public static final int WARMUP = 0, LIVE = 1, ENDED = 2;
    public static final int WARMUP_TICKS = 200, END_TICKS = 400;
    public static final int HQ_RADIUS = 12;

    public UUID id = UUID.randomUUID();
    public int state = WARMUP;
    public int timer;
    public final int[] tickets = new int[2];
    public int maxTickets;
    public int soldiersPerTeam;
    public final List<Objective> flags = new ArrayList<>();
    public final BlockPos[] hq = {BlockPos.ZERO, BlockPos.ZERO};
    public final List<VehiclePad> pads = new ArrayList<>();
    public @Nullable BlockPos towerBase;
    public int towerIntegrity = 100;
    public boolean towerCollapsed;
    public int towerFlag = -1;
    public @Nullable BlockPos depotPos;
    public int depotIntegrity = 60;
    public boolean depotDestroyed;
    public final List<BlockPos> fuelTanks = new ArrayList<>();
    public int minX, minZ, maxX, maxZ;
    public int winner = -1;
    public final Map<UUID, Participant> participants = new LinkedHashMap<>();
    public final Map<Integer, Squad> squads = new HashMap<>();
    public final List<SpawnBeacon> beacons = new ArrayList<>();
    private int nextSquad = 1;
    private final int[] bleed = new int[2];
    private final int[] reinforceCooldown = new int[2];
    private final boolean[] lowWarned = new boolean[2];
    private int lastAliveA, lastAliveB;

    // ------------------------------------------------------------------ data types
    public static final class Participant {
        public final UUID id;
        public String name;
        public Faction faction;
        public int score, kills, deaths;
        public boolean deployed;
        public int squadId = -1;
        public int lastKit;
        public String lastGun = "";

        Participant(UUID id, String name, Faction faction) {
            this.id = id;
            this.name = name;
            this.faction = faction;
        }
    }

    /** An AI fireteam. If {@code leader} is a player, the squad follows that player's orders. */
    public static final class Squad {
        public final int id;
        public final Faction faction;
        public int objective = -1;
        public @Nullable UUID leader;
        /** -1 follow leader, -2 hold {@link #holdPos}, otherwise objective index ordered by the player. */
        public int order = -1;
        public @Nullable Vec3 holdPos;
        public long orderTime;

        Squad(int id, Faction faction) {
            this.id = id;
            this.faction = faction;
        }
    }

    public static final class VehiclePad {
        public static final int TANK = 0, BUGGY = 1;
        public final BlockPos pos;
        public final Faction faction;
        public final int kind;
        public @Nullable UUID vehicle;
        public int cooldown = 200;

        VehiclePad(BlockPos pos, Faction faction, int kind) {
            this.pos = pos;
            this.faction = faction;
            this.kind = kind;
            if (kind == BUGGY) cooldown = 120; // light vehicles roll out first
        }
    }

    public record SpawnBeacon(Faction faction, BlockPos pos, UUID entity, String owner) {}

    // ------------------------------------------------------------------ queries
    public boolean isLive() { return state == LIVE; }

    public boolean inBounds(BlockPos p) {
        return p.getX() >= minX && p.getX() <= maxX && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    @Nullable
    public Participant participant(Player p) { return participants.get(p.getUUID()); }

    public BlockPos hq(Faction f) { return hq[f.ordinal()]; }

    public int owned(Faction f) {
        int n = 0;
        for (Objective o : flags) if (o.owner == f) n++;
        return n;
    }

    @Nullable
    public Faction uplinkHolder() {
        if (towerCollapsed || towerFlag < 0 || towerFlag >= flags.size()) return null;
        return flags.get(towerFlag).owner;
    }

    public Squad squad(int id) { return squads.get(id); }

    /** Where members of a squad should head right now. */
    @Nullable
    public Vec3 squadDestination(ServerLevel level, int squadId) {
        Squad s = squads.get(squadId);
        if (s == null) return null;
        if (s.leader != null) {
            if (s.order >= 0 && s.order < flags.size()) return Vec3.atBottomCenterOf(flags.get(s.order).pos);
            if (s.order == -2 && s.holdPos != null) return s.holdPos;
            Player p = level.getPlayerByUUID(s.leader);
            Participant pt = participants.get(s.leader);
            if (p != null && p.isAlive() && pt != null && pt.deployed) return p.position();
            if (s.objective >= 0 && s.objective < flags.size()) return Vec3.atBottomCenterOf(flags.get(s.objective).pos);
            return null;
        }
        if (s.objective >= 0 && s.objective < flags.size()) return Vec3.atBottomCenterOf(flags.get(s.objective).pos);
        return null;
    }

    public boolean squadFollowsPlayer(int squadId) {
        Squad s = squads.get(squadId);
        return s != null && s.leader != null && s.order < 0;
    }

    // ------------------------------------------------------------------ lifecycle
    private boolean chunksForced;

    /** Keeps every chunk of the battlefield loaded so the armies keep fighting out of the player's view. */
    public void forceChunks(ServerLevel level, boolean force) {
        int x0 = (minX + 24) >> 4, x1 = (maxX - 24) >> 4, z0 = (minZ + 24) >> 4, z1 = (maxZ - 24) >> 4;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++) level.setChunkForced(cx, cz, force);
        chunksForced = force;
    }

    public void tick(ServerLevel level) {
        if (!chunksForced && state != ENDED) forceChunks(level, true);
        timer++;
        switch (state) {
            case WARMUP -> {
                if (timer % 20 == 0) reinforce(level);
                if (timer >= WARMUP_TICKS) {
                    state = LIVE;
                    timer = 0;
                    broadcast(level, new SimplePackets.Announce("CONQUEST", "Capture and hold the flags. Bleed their tickets dry.", 0));
                }
            }
            case LIVE -> {
                if (timer % 5 == 0) updateFlags(level);
                if (timer % 20 == 0) {
                    bleedTickets(level);
                    reinforce(level);
                    tickPads(level);
                    protectHq(level);
                }
                if (timer % 40 == 0) uplinkScan(level);
                if (timer % 100 == 0) Commander.reassign(level, this);
                checkEnd(level);
            }
            case ENDED -> {
                if (timer >= END_TICKS) MatchManager.get(level).clear(level);
            }
        }
        if (timer % 10 == 0) MatchManager.sync(level, this);
    }

    private void updateFlags(ServerLevel level) {
        for (int i = 0; i < flags.size(); i++) {
            Objective o = flags.get(i);
            AABB box = new AABB(o.pos).inflate(o.radius, 7, o.radius);
            int a = 0, b = 0;
            List<ServerPlayer> inside = new ArrayList<>();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (!o.contains(e.getX(), e.getY(), e.getZ()) || com.ditsjohann.warfront.combat.CombatEvents.isDowned(e)) continue;
                Faction f = null;
                if (e instanceof SoldierEntity s && id.equals(s.getMatchId())) f = s.getFaction();
                else if (e instanceof ServerPlayer p && !p.isSpectator()) {
                    Participant pt = participants.get(p.getUUID());
                    if (pt != null) {
                        f = pt.faction;
                        inside.add(p);
                    }
                }
                if (f == Faction.COALITION) a++;
                else if (f == Faction.LEGION) b++;
            }
            o.countA = a;
            o.countB = b;
            int diff = a - b;
            float old = o.control;
            if (diff != 0) {
                float rate = 0.022f * (float) Math.pow(Math.min(Math.abs(diff), 4), 0.6);
                o.control = Mth.clamp(o.control + Math.signum(diff) * rate, -1f, 1f);
                if (timer % 20 == 0) {
                    for (ServerPlayer p : inside) level.playSound(null, p.blockPosition(), ModSounds.get("capture_tick"), SoundSource.PLAYERS, 0.35f, 1f + Math.abs(o.control) * 0.5f);
                }
            } else if (a == 0 && o.owner != null) {
                float target = o.owner == Faction.COALITION ? 1f : -1f;
                o.control += Mth.clamp(target - o.control, -0.01f, 0.01f);
            }
            // neutralised
            if (o.owner != null && ((old > 0 && o.control <= 0) || (old < 0 && o.control >= 0))) {
                Faction lost = o.owner;
                o.owner = null;
                for (ServerPlayer p : inside) if (participants.get(p.getUUID()).faction != lost) Scoring.award(p, 50, "FLAG NEUTRALIZED");
                announceFlag(level, o, lost, false);
            }
            // captured
            Faction capturer = o.control >= 1f ? Faction.COALITION : o.control <= -1f ? Faction.LEGION : null;
            if (capturer != null && o.owner != capturer) {
                o.owner = capturer;
                for (ServerPlayer p : inside) if (participants.get(p.getUUID()).faction == capturer) Scoring.award(p, 150, "FLAG CAPTURED");
                announceFlag(level, o, capturer, true);
                // order bonus for players whose squad was ordered here
                for (Squad s : squads.values()) {
                    if (s.leader != null && s.order == i && s.faction == capturer && level.getPlayerByUUID(s.leader) instanceof ServerPlayer sp) {
                        Scoring.award(sp, 50, "SQUAD OBJECTIVE");
                    }
                }
            }
        }
    }

    private void announceFlag(ServerLevel level, Objective o, Faction f, boolean captured) {
        for (ServerPlayer p : level.players()) {
            Participant pt = participants.get(p.getUUID());
            if (pt == null) continue;
            boolean ours = pt.faction == f;
            String title, sub;
            int style;
            if (captured) {
                title = ours ? "FLAG " + o.letter + " CAPTURED" : "FLAG " + o.letter + " LOST";
                sub = f.displayName + " controls " + o.letter;
                style = ours ? 1 : 2;
            } else {
                title = ours ? "FLAG " + o.letter + " UNDER ATTACK" : "FLAG " + o.letter + " NEUTRALIZED";
                sub = "";
                style = ours ? 2 : 0;
            }
            WarfrontNet.toPlayer(p, new SimplePackets.Announce(title, sub, style));
            if (captured) p.playNotifySound(ModSounds.get(ours ? "flag_captured" : "flag_lost"), SoundSource.MASTER, 0.8f, 1f);
        }
    }

    private void bleedTickets(ServerLevel level) {
        int oa = owned(Faction.COALITION), ob = owned(Faction.LEGION);
        if (oa == ob) return;
        Faction bleeder = oa > ob ? Faction.LEGION : Faction.COALITION;
        int diff = Math.abs(oa - ob);
        boolean all = Math.max(oa, ob) == flags.size();
        bleed[bleeder.ordinal()] += diff * (all ? 2 : 1);
        while (bleed[bleeder.ordinal()] >= 5) {
            bleed[bleeder.ordinal()] -= 5;
            loseTicket(level, bleeder);
        }
    }

    private void loseTicket(ServerLevel level, Faction f) {
        int i = f.ordinal();
        tickets[i] = Math.max(0, tickets[i] - 1);
        if (!lowWarned[i] && tickets[i] <= maxTickets / 5) {
            lowWarned[i] = true;
            for (ServerPlayer p : level.players()) {
                Participant pt = participants.get(p.getUUID());
                if (pt == null) continue;
                boolean ours = pt.faction == f;
                WarfrontNet.toPlayer(p, new SimplePackets.Announce(ours ? "TICKETS LOW" : "ENEMY TICKETS LOW",
                        ours ? "We are losing this battle!" : "Push them back. Victory is close.", ours ? 2 : 1));
                p.playNotifySound(ModSounds.get("ticket_warning"), SoundSource.MASTER, 0.6f, 1f);
            }
        }
    }

    /** A soldier or player of {@code f} was taken out. */
    public void onCombatantLost(ServerLevel level, LivingEntity who, Faction f) {
        if (state != LIVE) return;
        if (who instanceof SoldierEntity s && !id.equals(s.getMatchId())) return;
        loseTicket(level, f);
    }

    public void refundTicket(Faction f) {
        if (state == LIVE) tickets[f.ordinal()] = Math.min(maxTickets, tickets[f.ordinal()] + 1);
    }

    private void checkEnd(ServerLevel level) {
        Faction loser = tickets[0] <= 0 ? Faction.COALITION : tickets[1] <= 0 ? Faction.LEGION : null;
        if (loser != null) end(level, loser.enemy());
    }

    public void end(ServerLevel level, @Nullable Faction win) {
        if (state == ENDED) return;
        state = ENDED;
        timer = 0;
        winner = win == null ? -1 : win.ordinal();
        for (ServerPlayer p : level.players()) {
            Participant pt = participants.get(p.getUUID());
            if (pt == null) continue;
            boolean won = win == pt.faction;
            PlayerProgress.add(p, "matches", 1);
            if (won) PlayerProgress.add(p, "wins", 1);
            WarfrontNet.toPlayer(p, new SimplePackets.Announce(win == null ? "MATCH ENDED" : won ? "VICTORY" : "DEFEAT",
                    win == null ? "" : win.displayName + " wins the battle", win == null ? 0 : won ? 3 : 4));
            p.playNotifySound(ModSounds.get(won ? "victory" : "defeat"), SoundSource.MASTER, 1f, 1f);
            if (won) Scoring.award(p, 500, "VICTORY BONUS");
            else Scoring.award(p, 150, "MATCH COMPLETED");
            PlayerProgress.sync(p);
        }
        MatchManager.sync(level, this);
    }

    /** Removes every entity this match spawned. */
    public void cleanup(ServerLevel level) {
        forceChunks(level, false);
        AABB area = new AABB(minX - 64, level.getMinBuildHeight(), minZ - 64, maxX + 64, level.getMaxBuildHeight(), maxZ + 64);
        for (Entity e : level.getEntities((Entity) null, area, e -> e instanceof SoldierEntity s && id.equals(s.getMatchId())
                || e instanceof VehicleEntity t && id.equals(t.getMatchId()))) {
            e.discard();
        }
        for (ServerPlayer p : level.players()) {
            if (participants.containsKey(p.getUUID())) {
                Deployment.stripKit(p);
                com.ditsjohann.warfront.combat.DownedState.clear(p, false);
            }
        }
    }

    // ------------------------------------------------------------------ armies
    private void reinforce(ServerLevel level) {
        int[] alive = new int[2];
        Map<Integer, Integer> squadSizes = new HashMap<>();
        AABB area = new AABB(minX - 48, level.getMinBuildHeight(), minZ - 48, maxX + 48, level.getMaxBuildHeight(), maxZ + 48);
        for (SoldierEntity s : level.getEntitiesOfClass(SoldierEntity.class, area, s -> s.isAlive() && id.equals(s.getMatchId()))) {
            if (s.getFaction() != null) alive[s.getFaction().ordinal()]++;
            squadSizes.merge(s.getSquadId(), 1, Integer::sum);
        }
        lastAliveA = alive[0];
        lastAliveB = alive[1];
        // drop AI squads with no living members (before creating new ones this pass)
        squads.values().removeIf(s -> s.leader == null && !squadSizes.containsKey(s.id) && timer > 40);
        for (Faction f : Faction.values()) {
            int i = f.ordinal();
            if (reinforceCooldown[i] > 0) {
                reinforceCooldown[i] -= 20;
                continue;
            }
            if (tickets[i] <= 0 || alive[i] >= soldiersPerTeam) continue;
            // refill player squads first (2 AI per player squad), then spawn fresh AI squads of 4
            Squad target = null;
            int need = 0;
            for (Squad s : squads.values()) {
                if (s.faction != f || s.leader == null) continue;
                int have = squadSizes.getOrDefault(s.id, 0);
                if (have < 2 && participants.containsKey(s.leader) && participants.get(s.leader).deployed) {
                    target = s;
                    need = 2 - have;
                    break;
                }
            }
            if (target == null) {
                target = new Squad(nextSquad++, f);
                squads.put(target.id, target);
                target.objective = Commander.choose(this, f, hq(f), target.id);
                need = Math.min(4, soldiersPerTeam - alive[i]);
            }
            need = Math.min(need, soldiersPerTeam - alive[i]);
            if (need <= 0) continue;
            Vec3 spawn = spawnPointFor(level, f, target);
            for (int k = 0; k < need; k++) {
                SoldierRole role = SoldierRole.pick(level.random, k);
                BlockPos at = Deployment.safeNear(level, BlockPos.containing(spawn), 4);
                SoldierEntity.spawn(level, at, f, role, target.id, id);
            }
            reinforceCooldown[i] = 60;
        }
    }

    /** AI spawn: player squads spawn on their leader, others on the owned flag nearest their objective, else HQ. */
    private Vec3 spawnPointFor(ServerLevel level, Faction f, Squad s) {
        if (s.leader != null) {
            Player p = level.getPlayerByUUID(s.leader);
            if (p != null && p.isAlive() && !com.ditsjohann.warfront.combat.CombatEvents.isDowned(p)) return p.position();
        }
        Vec3 best = Vec3.atBottomCenterOf(hq(f));
        if (s.objective >= 0 && s.objective < flags.size()) {
            Vec3 goal = Vec3.atCenterOf(flags.get(s.objective).pos);
            double bd = best.distanceToSqr(goal);
            for (Objective o : flags) {
                if (!o.safeSpawnFor(f)) continue;
                Vec3 p = Vec3.atBottomCenterOf(o.pos);
                double d = p.distanceToSqr(goal);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
        }
        return best;
    }

    public Squad createPlayerSquad(ServerPlayer p, Faction f) {
        for (Squad s : squads.values()) if (p.getUUID().equals(s.leader)) return s;
        Squad s = new Squad(nextSquad++, f);
        s.leader = p.getUUID();
        squads.put(s.id, s);
        return s;
    }

    /** Player squad order: spotting while looking at a flag orders the squad to it; otherwise follow me. */
    public void orderSquad(ServerLevel level, ServerPlayer p) {
        Participant pt = participants.get(p.getUUID());
        if (pt == null || pt.squadId < 0) return;
        Squad s = squads.get(pt.squadId);
        if (s == null) return;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        int best = -1;
        double bestDot = Math.cos(Math.toRadians(6));
        for (int i = 0; i < flags.size(); i++) {
            Vec3 to = Vec3.atCenterOf(flags.get(i).pos).add(0, 2, 0).subtract(eye);
            double dot = to.normalize().dot(look);
            if (dot > bestDot && to.length() > 6) {
                bestDot = dot;
                best = i;
            }
        }
        if (best >= 0) {
            s.order = best;
            s.orderTime = level.getGameTime();
            Objective o = flags.get(best);
            String verb = o.owner == pt.faction ? "DEFEND" : "ATTACK";
            WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "SQUAD ORDER: " + verb + " " + o.letter));
        } else if (s.order >= 0) {
            s.order = -1;
            WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "SQUAD ORDER: FOLLOW ME"));
        }
    }

    /** Commo-rose orders from a squad leader, and support requests to nearby AI. */
    public void command(ServerLevel level, ServerPlayer p, int cmd, int arg) {
        Participant pt = participants.get(p.getUUID());
        if (pt == null) return;
        Squad s = squads.get(pt.squadId);
        switch (cmd) {
            case SimplePackets.SquadCommand.FLAG -> {
                if (s == null || arg < 0 || arg >= flags.size()) return;
                s.order = arg;
                s.orderTime = level.getGameTime();
                Objective o = flags.get(arg);
                WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "SQUAD ORDER: " + (o.owner == pt.faction ? "DEFEND " : "ATTACK ") + o.letter));
            }
            case SimplePackets.SquadCommand.FOLLOW -> {
                if (s == null) return;
                s.order = -1;
                WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "SQUAD ORDER: FOLLOW ME"));
            }
            case SimplePackets.SquadCommand.HOLD -> {
                if (s == null) return;
                s.order = -2;
                s.holdPos = p.position();
                WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "SQUAD ORDER: HOLD THIS POSITION"));
            }
            case SimplePackets.SquadCommand.MEDIC, SimplePackets.SquadCommand.AMMO -> {
                boolean ammo = cmd == SimplePackets.SquadCommand.AMMO;
                SoldierRole role = ammo ? SoldierRole.SUPPORT : SoldierRole.ASSAULT;
                SoldierEntity helper = null;
                double best = 64 * 64;
                for (SoldierEntity e : level.getEntitiesOfClass(SoldierEntity.class, p.getBoundingBox().inflate(64),
                        e -> e.getRole() == role && e.getFaction() == pt.faction && e.isCombatant() && !e.isPassenger())) {
                    double d = e.distanceToSqr(p) * (e.getSquadId() == pt.squadId ? 0.5 : 1);
                    if (d < best) {
                        best = d;
                        helper = e;
                    }
                }
                if (helper == null) {
                    WarfrontNet.toPlayer(p, new SimplePackets.Score(0, ammo ? "NO SUPPORT NEARBY" : "NO MEDIC NEARBY"));
                    return;
                }
                helper.requestAssist(p, ammo);
                WarfrontNet.toPlayer(p, new SimplePackets.Score(0, (ammo ? "AMMO" : "MEDIC") + " INBOUND: " + helper.getName().getString()));
            }
            default -> {}
        }
    }

    private void tickPads(ServerLevel level) {
        for (VehiclePad pad : pads) {
            Entity v = pad.vehicle == null ? null : level.getEntity(pad.vehicle);
            if (v != null && v.isAlive()) continue;
            pad.vehicle = null;
            pad.cooldown -= 20;
            if (pad.cooldown > 0 || tickets[pad.faction.ordinal()] <= 0) continue;
            if (!level.isLoaded(pad.pos)) continue;
            VehicleEntity vehicle = pad.kind == VehiclePad.BUGGY ? BuggyEntity.spawn(level, pad.pos.above(), pad.faction, id)
                    : TankEntity.spawn(level, pad.pos.above(), pad.faction, id);
            if (vehicle != null) {
                pad.vehicle = vehicle.getUUID();
                pad.cooldown = pad.kind == VehiclePad.BUGGY ? 700 : 1200;
                // an AI crew takes the vehicle to the front; players can claim it by climbing in
                Squad crewSquad = new Squad(nextSquad++, pad.faction);
                crewSquad.objective = Commander.choose(this, pad.faction, pad.pos, crewSquad.id);
                squads.put(crewSquad.id, crewSquad);
                SoldierRole[] crew = pad.kind == VehiclePad.BUGGY ? new SoldierRole[]{SoldierRole.ASSAULT, SoldierRole.SUPPORT} : new SoldierRole[]{SoldierRole.ENGINEER};
                for (SoldierRole role : crew) {
                    SoldierEntity c = SoldierEntity.spawn(level, pad.pos.above(), pad.faction, role, crewSquad.id, id);
                    if (c != null) c.startRiding(vehicle, true);
                }
            }
        }
    }

    /** Base protection: enemies inside an HQ take escalating damage. */
    private void protectHq(ServerLevel level) {
        for (Faction f : Faction.values()) {
            BlockPos h = hq(f);
            AABB box = new AABB(h).inflate(HQ_RADIUS, 10, HQ_RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && Teams.of(e) == f.enemy())) {
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                e.hurt(level.damageSources().magic(), 3f);
                if (e instanceof ServerPlayer sp) WarfrontNet.toPlayer(sp, new SimplePackets.Score(0, "RESTRICTED AREA - TURN BACK"));
            }
        }
    }

    /** The Comms Tower uplink: whoever holds the tower flag sees nearby enemies on radar. */
    private void uplinkScan(ServerLevel level) {
        Faction holder = uplinkHolder();
        if (holder == null || towerBase == null) return;
        AABB box = new AABB(towerBase).inflate(90, 40, 90);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && Teams.of(e) == holder.enemy())) {
            if (level.random.nextFloat() < 0.5f) Spotting.mark(level, e, null);
        }
    }

    public void damageDepot(ServerLevel level, int amount, @Nullable Entity cause) {
        if (depotPos == null || depotDestroyed || state != LIVE) return;
        int before = depotIntegrity;
        depotIntegrity = Math.max(0, depotIntegrity - amount);
        if (before > 30 && depotIntegrity <= 30) broadcast(level, new SimplePackets.Announce("FUEL DEPOT LEAKING", "One more hit and it goes up", 0));
        if (before > 0 && depotIntegrity == 0) DepotExplosion.begin(level, this, cause);
    }

    public void damageTower(ServerLevel level, int amount, @Nullable Entity cause) {
        if (towerBase == null || towerCollapsed || state != LIVE) return;
        int before = towerIntegrity;
        towerIntegrity = Math.max(0, towerIntegrity - amount);
        if (before > 50 && towerIntegrity <= 50) {
            broadcast(level, new SimplePackets.Announce("COMMS TOWER DAMAGED", "Structural integrity at 50%", 0));
        }
        if (before > 0 && towerIntegrity == 0) {
            TowerCollapse.begin(level, this, cause);
        }
    }

    // ------------------------------------------------------------------ players
    public Participant join(ServerPlayer p, Faction f) {
        Participant pt = participants.computeIfAbsent(p.getUUID(), k -> new Participant(k, p.getGameProfile().getName(), f));
        pt.faction = f;
        pt.name = p.getGameProfile().getName();
        Squad s = createPlayerSquad(p, f);
        pt.squadId = s.id;
        return pt;
    }

    public void leave(ServerPlayer p) {
        Participant pt = participants.remove(p.getUUID());
        if (pt == null) return;
        squads.values().removeIf(s -> p.getUUID().equals(s.leader));
        Deployment.stripKit(p);
        com.ditsjohann.warfront.combat.DownedState.clear(p, false);
    }

    public void addScore(ServerPlayer p, int pts) {
        Participant pt = participants.get(p.getUUID());
        if (pt != null) pt.score += pts;
    }

    public void addKill(ServerPlayer p) {
        Participant pt = participants.get(p.getUUID());
        if (pt != null) pt.kills++;
    }

    public void addDeath(ServerPlayer p) {
        Participant pt = participants.get(p.getUUID());
        if (pt != null) pt.deaths++;
    }

    public void broadcast(ServerLevel level, Object packet) {
        for (ServerPlayer p : level.players()) if (participants.containsKey(p.getUUID()) || p.distanceToSqr(Vec3.atCenterOf(hq[0])) < 400 * 400) WarfrontNet.toPlayer(p, packet);
    }

    public int surfaceY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    // ------------------------------------------------------------------ snapshot + persistence
    public MatchSnapshot snapshot() {
        List<MatchSnapshot.Flag> fl = new ArrayList<>();
        for (Objective o : flags) {
            fl.add(new MatchSnapshot.Flag(o.letter, o.pos, o.radius, o.control, o.owner == null ? -1 : o.owner.ordinal(), o.countA, o.countB));
        }
        List<MatchSnapshot.Beacon> bc = new ArrayList<>();
        for (SpawnBeacon b : beacons) bc.add(new MatchSnapshot.Beacon(b.faction().ordinal(), b.pos(), b.owner()));
        List<MatchSnapshot.Entry> ps = new ArrayList<>();
        for (Participant p : participants.values()) ps.add(new MatchSnapshot.Entry(p.id, p.name, p.faction.ordinal(), p.score, p.kills, p.deaths, false));
        int t = state == WARMUP ? WARMUP_TICKS - timer : state == ENDED ? END_TICKS - timer : timer;
        Faction up = uplinkHolder();
        return new MatchSnapshot(true, state, t, tickets[0], tickets[1], maxTickets, fl, hq[0], hq[1], bc,
                towerBase == null ? BlockPos.ZERO : towerBase, towerIntegrity, towerCollapsed, up == null ? -1 : up.ordinal(),
                ps, winner, minX, minZ, maxX, maxZ, lastAliveA, lastAliveB, depotPos == null ? BlockPos.ZERO : depotPos, depotDestroyed);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putInt("state", state);
        t.putInt("timer", timer);
        t.putIntArray("tickets", tickets);
        t.putInt("maxTickets", maxTickets);
        t.putInt("soldiers", soldiersPerTeam);
        ListTag fl = new ListTag();
        for (Objective o : flags) fl.add(o.save());
        t.put("flags", fl);
        t.put("hqA", NbtUtils.writeBlockPos(hq[0]));
        t.put("hqB", NbtUtils.writeBlockPos(hq[1]));
        ListTag pl = new ListTag();
        for (VehiclePad p : pads) {
            CompoundTag c = new CompoundTag();
            c.put("pos", NbtUtils.writeBlockPos(p.pos));
            c.putInt("faction", p.faction.ordinal());
            c.putInt("kind", p.kind);
            if (p.vehicle != null) c.putUUID("vehicle", p.vehicle);
            c.putInt("cooldown", p.cooldown);
            pl.add(c);
        }
        t.put("pads", pl);
        if (towerBase != null) t.put("tower", NbtUtils.writeBlockPos(towerBase));
        t.putInt("towerIntegrity", towerIntegrity);
        t.putBoolean("towerCollapsed", towerCollapsed);
        t.putInt("towerFlag", towerFlag);
        if (depotPos != null) t.put("depot", NbtUtils.writeBlockPos(depotPos));
        t.putInt("depotIntegrity", depotIntegrity);
        t.putBoolean("depotDestroyed", depotDestroyed);
        t.putLongArray("fuelTanks", fuelTanks.stream().mapToLong(BlockPos::asLong).toArray());
        t.putIntArray("bounds", new int[]{minX, minZ, maxX, maxZ});
        t.putInt("winner", winner);
        ListTag parts = new ListTag();
        for (Participant p : participants.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", p.id);
            c.putString("name", p.name);
            c.putInt("faction", p.faction.ordinal());
            c.putInt("score", p.score);
            c.putInt("kills", p.kills);
            c.putInt("deaths", p.deaths);
            c.putInt("squad", p.squadId);
            c.putInt("kit", p.lastKit);
            c.putString("gun", p.lastGun);
            parts.add(c);
        }
        t.put("participants", parts);
        ListTag sq = new ListTag();
        for (Squad s : squads.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", s.id);
            c.putInt("faction", s.faction.ordinal());
            c.putInt("objective", s.objective);
            c.putInt("order", s.order);
            if (s.leader != null) c.putUUID("leader", s.leader);
            sq.add(c);
        }
        t.put("squads", sq);
        t.putInt("nextSquad", nextSquad);
        ListTag bl = new ListTag();
        for (SpawnBeacon b : beacons) {
            CompoundTag c = new CompoundTag();
            c.putInt("faction", b.faction().ordinal());
            c.put("pos", NbtUtils.writeBlockPos(b.pos()));
            c.putUUID("entity", b.entity());
            c.putString("owner", b.owner());
            bl.add(c);
        }
        t.put("beacons", bl);
        return t;
    }

    public static ConquestMatch load(CompoundTag t) {
        ConquestMatch m = new ConquestMatch();
        m.id = t.getUUID("id");
        m.state = t.getInt("state");
        m.timer = t.getInt("timer");
        int[] tk = t.getIntArray("tickets");
        if (tk.length == 2) {
            m.tickets[0] = tk[0];
            m.tickets[1] = tk[1];
        }
        m.maxTickets = t.getInt("maxTickets");
        m.soldiersPerTeam = t.getInt("soldiers");
        for (Tag ft : t.getList("flags", Tag.TAG_COMPOUND)) m.flags.add(Objective.load((CompoundTag) ft));
        m.hq[0] = NbtUtils.readBlockPos(t, "hqA").orElse(BlockPos.ZERO);
        m.hq[1] = NbtUtils.readBlockPos(t, "hqB").orElse(BlockPos.ZERO);
        for (Tag pt : t.getList("pads", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) pt;
            VehiclePad p = new VehiclePad(NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO), Faction.byId(c.getInt("faction")), c.getInt("kind"));
            if (c.hasUUID("vehicle")) p.vehicle = c.getUUID("vehicle");
            p.cooldown = c.getInt("cooldown");
            m.pads.add(p);
        }
        m.towerBase = NbtUtils.readBlockPos(t, "tower").orElse(null);
        m.towerIntegrity = t.getInt("towerIntegrity");
        m.towerCollapsed = t.getBoolean("towerCollapsed");
        m.towerFlag = t.getInt("towerFlag");
        m.depotPos = NbtUtils.readBlockPos(t, "depot").orElse(null);
        m.depotIntegrity = t.contains("depotIntegrity") ? t.getInt("depotIntegrity") : 60;
        m.depotDestroyed = t.getBoolean("depotDestroyed");
        for (long l : t.getLongArray("fuelTanks")) m.fuelTanks.add(BlockPos.of(l));
        int[] b = t.getIntArray("bounds");
        if (b.length == 4) {
            m.minX = b[0];
            m.minZ = b[1];
            m.maxX = b[2];
            m.maxZ = b[3];
        }
        m.winner = t.getInt("winner");
        for (Tag pt : t.getList("participants", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) pt;
            Participant p = new Participant(c.getUUID("id"), c.getString("name"), Faction.byId(c.getInt("faction")));
            p.score = c.getInt("score");
            p.kills = c.getInt("kills");
            p.deaths = c.getInt("deaths");
            p.squadId = c.getInt("squad");
            p.lastKit = c.getInt("kit");
            p.lastGun = c.getString("gun");
            m.participants.put(p.id, p);
        }
        for (Tag st : t.getList("squads", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) st;
            Squad s = new Squad(c.getInt("id"), Faction.byId(c.getInt("faction")));
            s.objective = c.getInt("objective");
            s.order = c.getInt("order");
            if (c.hasUUID("leader")) s.leader = c.getUUID("leader");
            m.squads.put(s.id, s);
        }
        m.nextSquad = Math.max(1, t.getInt("nextSquad"));
        for (Tag bt : t.getList("beacons", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) bt;
            m.beacons.add(new SpawnBeacon(Faction.byId(c.getInt("faction")), NbtUtils.readBlockPos(c, "pos").orElse(BlockPos.ZERO), c.getUUID("entity"), c.getString("owner")));
        }
        return m;
    }
}
