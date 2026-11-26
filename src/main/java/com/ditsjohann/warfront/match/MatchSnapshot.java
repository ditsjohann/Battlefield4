package com.ditsjohann.warfront.match;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything a client needs to draw the Conquest HUD, minimap, scoreboard and deploy map.
 * Sent to every player in the level twice a second while a match exists.
 */
public record MatchSnapshot(
        boolean active, int state, int timer, int ticketsA, int ticketsB, int maxTickets,
        List<Flag> flags, BlockPos hqA, BlockPos hqB, List<Beacon> beacons,
        BlockPos towerPos, int towerIntegrity, boolean towerCollapsed, int uplinkFaction,
        List<Entry> players, int winner, int minX, int minZ, int maxX, int maxZ, int aliveA, int aliveB) {

    public static final MatchSnapshot NONE = new MatchSnapshot(false, 0, 0, 0, 0, 0, List.of(), BlockPos.ZERO, BlockPos.ZERO,
            List.of(), BlockPos.ZERO, 0, false, -1, List.of(), -1, 0, 0, 0, 0, 0, 0);

    public record Flag(char letter, BlockPos pos, int radius, float control, int owner, int countA, int countB) {
        public boolean contested() { return countA > 0 && countB > 0; }
    }

    public record Beacon(int faction, BlockPos pos, String owner) {}

    public record Entry(UUID id, String name, int faction, int score, int kills, int deaths, boolean bot) {}

    public static final StreamCodec<FriendlyByteBuf, MatchSnapshot> CODEC = StreamCodec.of(MatchSnapshot::write, MatchSnapshot::read);

    private static void write(FriendlyByteBuf b, MatchSnapshot s) {
        b.writeBoolean(s.active);
        if (!s.active) return;
        b.writeVarInt(s.state);
        b.writeVarInt(s.timer);
        b.writeVarInt(s.ticketsA);
        b.writeVarInt(s.ticketsB);
        b.writeVarInt(s.maxTickets);
        b.writeVarInt(s.flags.size());
        for (Flag f : s.flags) {
            b.writeChar(f.letter);
            b.writeBlockPos(f.pos);
            b.writeVarInt(f.radius);
            b.writeFloat(f.control);
            b.writeVarInt(f.owner + 1);
            b.writeVarInt(f.countA);
            b.writeVarInt(f.countB);
        }
        b.writeBlockPos(s.hqA);
        b.writeBlockPos(s.hqB);
        b.writeVarInt(s.beacons.size());
        for (Beacon be : s.beacons) {
            b.writeVarInt(be.faction);
            b.writeBlockPos(be.pos);
            b.writeUtf(be.owner);
        }
        b.writeBlockPos(s.towerPos);
        b.writeVarInt(s.towerIntegrity);
        b.writeBoolean(s.towerCollapsed);
        b.writeVarInt(s.uplinkFaction + 1);
        b.writeVarInt(s.players.size());
        for (Entry e : s.players) {
            b.writeUUID(e.id);
            b.writeUtf(e.name);
            b.writeVarInt(e.faction);
            b.writeVarInt(e.score);
            b.writeVarInt(e.kills);
            b.writeVarInt(e.deaths);
            b.writeBoolean(e.bot);
        }
        b.writeVarInt(s.winner + 1);
        b.writeInt(s.minX);
        b.writeInt(s.minZ);
        b.writeInt(s.maxX);
        b.writeInt(s.maxZ);
        b.writeVarInt(s.aliveA);
        b.writeVarInt(s.aliveB);
    }

    private static MatchSnapshot read(FriendlyByteBuf b) {
        if (!b.readBoolean()) return NONE;
        int state = b.readVarInt(), timer = b.readVarInt(), ta = b.readVarInt(), tb = b.readVarInt(), max = b.readVarInt();
        int nf = b.readVarInt();
        List<Flag> flags = new ArrayList<>(nf);
        for (int i = 0; i < nf; i++) {
            flags.add(new Flag(b.readChar(), b.readBlockPos(), b.readVarInt(), b.readFloat(), b.readVarInt() - 1, b.readVarInt(), b.readVarInt()));
        }
        BlockPos hqA = b.readBlockPos(), hqB = b.readBlockPos();
        int nb = b.readVarInt();
        List<Beacon> beacons = new ArrayList<>(nb);
        for (int i = 0; i < nb; i++) beacons.add(new Beacon(b.readVarInt(), b.readBlockPos(), b.readUtf()));
        BlockPos tower = b.readBlockPos();
        int integrity = b.readVarInt();
        boolean collapsed = b.readBoolean();
        int uplink = b.readVarInt() - 1;
        int np = b.readVarInt();
        List<Entry> players = new ArrayList<>(np);
        for (int i = 0; i < np; i++) {
            players.add(new Entry(b.readUUID(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
        }
        int winner = b.readVarInt() - 1;
        return new MatchSnapshot(true, state, timer, ta, tb, max, flags, hqA, hqB, beacons, tower, integrity, collapsed, uplink,
                players, winner, b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt());
    }
}
