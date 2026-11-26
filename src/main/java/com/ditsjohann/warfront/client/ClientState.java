package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.match.MatchSnapshot;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Everything the client knows about the battle and the local soldier, for HUD and screens. */
public final class ClientState {
    public static MatchSnapshot match = MatchSnapshot.NONE;
    public static SimplePackets.Progress progress = new SimplePackets.Progress(0, 0, 0, 0, 0, 0);

    // ---- feedback
    public static int hitMarkerTicks;
    public static boolean hitHead, hitKill, hitVehicle;
    public static float suppression;
    public static Vec3 lastSuppressFrom;
    public static float shake;
    public static boolean downed;
    public static int downedTicks;
    public static int deployCountdown = -1;

    public record ScorePopup(int points, String label, long time) {}

    public record FeedLine(SimplePackets.KillFeed feed, long time) {}

    public record Announcement(String title, String subtitle, int style, long time) {}

    public static final Deque<ScorePopup> SCORES = new ArrayDeque<>();
    public static final Deque<FeedLine> FEED = new ArrayDeque<>();
    public static Announcement announcement;
    public static int roundScore;
    public static long roundScoreTime;
    public static final List<int[]> DAMAGE_INDICATORS = new ArrayList<>();
    /** Entities seen firing unsuppressed weapons recently (minimap reveal), id -> client tick. */
    public static final Map<Integer, Long> REVEALED = new ConcurrentHashMap<>();

    public static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static Faction myFaction() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : factionOf(mc.player.getUUID());
    }

    public static Faction factionOf(UUID id) {
        if (!match.active()) return null;
        for (MatchSnapshot.Entry e : match.players()) if (e.id().equals(id)) return Faction.byId(e.faction());
        return null;
    }

    public static Faction factionOf(Player p) {
        return factionOf(p.getUUID());
    }

    public static boolean inMatch() {
        return myFaction() != null;
    }

    public static void tick() {
        if (hitMarkerTicks > 0) hitMarkerTicks--;
        suppression = Math.max(0, suppression - 0.015f);
        shake *= 0.86f;
        if (shake < 0.01f) shake = 0;
        long t = now();
        while (!SCORES.isEmpty() && t - SCORES.peekFirst().time() > 60) SCORES.pollFirst();
        while (!FEED.isEmpty() && t - FEED.peekFirst().time() > 160) FEED.pollFirst();
        if (announcement != null && t - announcement.time() > 90) announcement = null;
        if (roundScore > 0 && t - roundScoreTime > 70) roundScore = 0;
        REVEALED.values().removeIf(v -> t - v > 50);
        if (downed && downedTicks > 0) downedTicks--;
        if (deployCountdown > 0) deployCountdown--;
    }

    public static void reset() {
        match = MatchSnapshot.NONE;
        SCORES.clear();
        FEED.clear();
        announcement = null;
        downed = false;
        suppression = 0;
    }

    private ClientState() {}
}
