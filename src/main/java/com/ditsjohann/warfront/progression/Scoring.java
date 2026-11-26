package com.ditsjohann.warfront.progression;

import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.MatchManager;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Score popups, match score and XP. Rank-ups announce what they unlocked. */
public final class Scoring {
    public static void award(ServerPlayer p, int points, String label) {
        if (points <= 0) return;
        WarfrontNet.toPlayer(p, new SimplePackets.Score(points, label));
        ConquestMatch m = MatchManager.active(p.serverLevel());
        if (m != null) m.addScore(p, points);
        int before = PlayerProgress.rank(p);
        PlayerProgress.add(p, "xp", points);
        int after = PlayerProgress.rank(p);
        if (after > before) {
            List<String> unlocks = Ranks.unlocksAt(after);
            String sub = unlocks.isEmpty() ? "Keep fighting, soldier." : "Unlocked: " + String.join(", ", unlocks);
            WarfrontNet.toPlayer(p, new SimplePackets.Announce("PROMOTED: " + Ranks.name(after).toUpperCase(), sub, 1));
        }
        PlayerProgress.sync(p);
    }

    public static void countKill(ServerPlayer p) { PlayerProgress.add(p, "kills", 1); }

    public static void countDeath(ServerPlayer p) {
        PlayerProgress.add(p, "deaths", 1);
        PlayerProgress.sync(p);
    }

    private Scoring() {}
}
