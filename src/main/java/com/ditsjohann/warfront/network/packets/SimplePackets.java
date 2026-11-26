package com.ditsjohann.warfront.network.packets;

import com.ditsjohann.warfront.weapon.Loadout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** Small fixed-shape packets. Each record carries its own StreamCodec. */
public final class SimplePackets {
    // ---------------------------------------------------------------- client -> server
    /** Fire one shot along the given aim (spread already applied by the client). */
    public record Shoot(float yaw, float pitch) {
        public static final StreamCodec<FriendlyByteBuf, Shoot> CODEC = StreamCodec.of(
                (b, p) -> { b.writeFloat(p.yaw); b.writeFloat(p.pitch); }, b -> new Shoot(b.readFloat(), b.readFloat()));
    }

    /** 0 = reload, 1 = cycle fire mode, 2 = spot, 3 = bleed out, 4 = open deploy, 5 = aim on, 6 = aim off. */
    public record Action(int action) {
        public static final int RELOAD = 0, FIRE_MODE = 1, SPOT = 2, BLEED_OUT = 3, OPEN_DEPLOY = 4, AIM_ON = 5, AIM_OFF = 6, PRONE_ON = 7, PRONE_OFF = 8;
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (b, p) -> b.writeVarInt(p.action), b -> new Action(b.readVarInt()));
    }

    /** Deploy request: spawn type (0 HQ, 1 flag, 2 beacon, 3 squad leader) + index, kit and primary. */
    public record Deploy(int spawnType, int spawnIndex, int kit, String gun, Loadout loadout) {
        public static final StreamCodec<FriendlyByteBuf, Deploy> CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeVarInt(p.spawnType);
                    b.writeVarInt(p.spawnIndex);
                    b.writeVarInt(p.kit);
                    b.writeUtf(p.gun);
                    Loadout.STREAM_CODEC.encode(b, p.loadout);
                },
                b -> new Deploy(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(), Loadout.STREAM_CODEC.decode(b)));
    }

    /** War table command: 0 start match, 1 join, 2 end match, 3 leave. */
    public record WarTable(BlockPos pos, int command, int size, int faction, int soldiers, int tickets) {
        public static final int START = 0, JOIN = 1, END = 2, LEAVE = 3;
        public static final StreamCodec<FriendlyByteBuf, WarTable> CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeBlockPos(p.pos);
                    b.writeVarInt(p.command);
                    b.writeVarInt(p.size);
                    b.writeVarInt(p.faction);
                    b.writeVarInt(p.soldiers);
                    b.writeVarInt(p.tickets);
                },
                b -> new WarTable(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
    }

    /** Vehicle weapon input: 0 fire cannon, 1 coax on, 2 coax off, 3 smoke. */
    public record VehicleInput(int action) {
        public static final StreamCodec<FriendlyByteBuf, VehicleInput> CODEC = StreamCodec.of(
                (b, p) -> b.writeVarInt(p.action), b -> new VehicleInput(b.readVarInt()));
    }

    /** Commo-rose order: 0 attack/defend flag (arg = index), 1 follow me, 2 hold here, 3 need medic, 4 need ammo. */
    public record SquadCommand(int command, int arg) {
        public static final int FLAG = 0, FOLLOW = 1, HOLD = 2, MEDIC = 3, AMMO = 4;
        public static final StreamCodec<FriendlyByteBuf, SquadCommand> CODEC = StreamCodec.of(
                (b, p) -> { b.writeVarInt(p.command); b.writeVarInt(p.arg); }, b -> new SquadCommand(b.readVarInt(), b.readVarInt()));
    }

    // ---------------------------------------------------------------- server -> client
    /** Hit confirmation for the shooter. flags: 1 hit, 2 headshot, 4 kill, 8 vehicle. */
    public record HitMarker(int flags, float damage) {
        public static final StreamCodec<FriendlyByteBuf, HitMarker> CODEC = StreamCodec.of(
                (b, p) -> { b.writeVarInt(p.flags); b.writeFloat(p.damage); }, b -> new HitMarker(b.readVarInt(), b.readFloat()));
    }

    /** You are under fire: amount of suppression and where the round passed. */
    public record Suppressed(float amount, Vec3 from) {
        public static final StreamCodec<FriendlyByteBuf, Suppressed> CODEC = StreamCodec.of(
                (b, p) -> { b.writeFloat(p.amount); b.writeVec3(p.from); }, b -> new Suppressed(b.readFloat(), b.readVec3()));
    }

    /** A score event for the receiving player ("+100 ENEMY KILLED"). */
    public record Score(int points, String label) {
        public static final StreamCodec<FriendlyByteBuf, Score> CODEC = StreamCodec.of(
                (b, p) -> { b.writeVarInt(p.points); b.writeUtf(p.label); }, b -> new Score(b.readVarInt(), b.readUtf()));
    }

    /** Kill feed line. Factions: -1 none, 0 coalition, 1 legion. */
    public record KillFeed(String killer, int killerFaction, String victim, int victimFaction, String weapon, boolean headshot) {
        public static final StreamCodec<FriendlyByteBuf, KillFeed> CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeUtf(p.killer);
                    b.writeVarInt(p.killerFaction + 1);
                    b.writeUtf(p.victim);
                    b.writeVarInt(p.victimFaction + 1);
                    b.writeUtf(p.weapon);
                    b.writeBoolean(p.headshot);
                },
                b -> new KillFeed(b.readUtf(), b.readVarInt() - 1, b.readUtf(), b.readVarInt() - 1, b.readUtf(), b.readBoolean()));
    }

    /** Opens the deploy screen after a delay (respawn timer). */
    public record OpenDeploy(int delayTicks) {
        public static final StreamCodec<FriendlyByteBuf, OpenDeploy> CODEC = StreamCodec.of(
                (b, p) -> b.writeVarInt(p.delayTicks), b -> new OpenDeploy(b.readVarInt()));
    }

    public record OpenWarTable(BlockPos pos) {
        public static final StreamCodec<FriendlyByteBuf, OpenWarTable> CODEC = StreamCodec.of(
                (b, p) -> b.writeBlockPos(p.pos), b -> new OpenWarTable(b.readBlockPos()));
    }

    /** Player progression snapshot, including kills per weapon (attachment unlocks). */
    public record Progress(int xp, int rank, int kills, int deaths, int wins, int matches, java.util.Map<String, Integer> weaponKills) {
        public static final StreamCodec<FriendlyByteBuf, Progress> CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeVarInt(p.xp);
                    b.writeVarInt(p.rank);
                    b.writeVarInt(p.kills);
                    b.writeVarInt(p.deaths);
                    b.writeVarInt(p.wins);
                    b.writeVarInt(p.matches);
                    b.writeVarInt(p.weaponKills.size());
                    p.weaponKills.forEach((k, v) -> {
                        b.writeUtf(k);
                        b.writeVarInt(v);
                    });
                },
                b -> {
                    int xp = b.readVarInt(), rank = b.readVarInt(), kills = b.readVarInt(), deaths = b.readVarInt(), wins = b.readVarInt(), matches = b.readVarInt();
                    int n = b.readVarInt();
                    java.util.Map<String, Integer> wk = new java.util.HashMap<>();
                    for (int i = 0; i < n; i++) wk.put(b.readUtf(), b.readVarInt());
                    return new Progress(xp, rank, kills, deaths, wins, matches, wk);
                });

        public int weaponKills(com.ditsjohann.warfront.weapon.GunType g) {
            return weaponKills.getOrDefault(g.id, 0);
        }
    }

    /** Camera shake (explosions, cannon fire, collapses). */
    public record Shake(float intensity) {
        public static final StreamCodec<FriendlyByteBuf, Shake> CODEC = StreamCodec.of(
                (b, p) -> b.writeFloat(p.intensity), b -> new Shake(b.readFloat()));
    }

    /** Downed state for the local player. ticksLeft = 0 means revived/cleared. */
    public record Downed(boolean downed, int ticksLeft) {
        public static final StreamCodec<FriendlyByteBuf, Downed> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBoolean(p.downed); b.writeVarInt(p.ticksLeft); }, b -> new Downed(b.readBoolean(), b.readVarInt()));
    }

    /** Big centred announcement (flag captured, tower collapsing, victory). style: 0 info, 1 good, 2 bad, 3 victory, 4 defeat. */
    public record Announce(String title, String subtitle, int style) {
        public static final StreamCodec<FriendlyByteBuf, Announce> CODEC = StreamCodec.of(
                (b, p) -> { b.writeUtf(p.title); b.writeUtf(p.subtitle); b.writeVarInt(p.style); },
                b -> new Announce(b.readUtf(), b.readUtf(), b.readVarInt()));
    }

    /** A player's scope glint turned on/off. */
    public record Glint(int entityId, boolean on) {
        public static final StreamCodec<FriendlyByteBuf, Glint> CODEC = StreamCodec.of(
                (b, p) -> { b.writeVarInt(p.entityId); b.writeBoolean(p.on); }, b -> new Glint(b.readVarInt(), b.readBoolean()));
    }

    private SimplePackets() {}
}
