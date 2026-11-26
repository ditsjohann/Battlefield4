package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Team-level strategy. Every few seconds each AI squad is (re)assigned the objective with the
 * best value: enemy and neutral flags are worth attacking, threatened friendly flags are worth
 * defending, distant flags cost more, and squads spread out instead of dog-piling one flag.
 */
public final class Commander {
    public static int choose(ConquestMatch m, Faction f, BlockPos from, int squadId) {
        if (m.flags.isEmpty()) return -1;
        Map<Integer, Integer> load = new HashMap<>();
        for (ConquestMatch.Squad s : m.squads.values()) {
            if (s.faction == f && s.id != squadId && s.objective >= 0) load.merge(s.objective, 1, Integer::sum);
        }
        int best = 0;
        double bestScore = -1e9;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < m.flags.size(); i++) {
            Objective o = m.flags.get(i);
            double score = 0;
            if (o.owner != f) score += 10;
            if (o.owner == null) score += 3;
            if (o.owner == f && o.enemiesOf(f) > 0) score += 12;
            if (o.owner == f && o.enemiesOf(f) == 0) score += 1;
            if (o.hasTower && !m.towerCollapsed) score += 2; // the uplink is worth having
            double dist = Math.sqrt(o.pos.distSqr(from));
            score -= dist / 25.0;
            score -= 3.5 * load.getOrDefault(i, 0);
            score += rnd.nextDouble() * 2.0;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    static void reassign(ServerLevel level, ConquestMatch m) {
        for (ConquestMatch.Squad s : m.squads.values()) {
            if (s.leader != null) {
                // player squads without an order keep a fallback objective for when the leader is dead
                s.objective = choose(m, s.faction, m.hq(s.faction), s.id);
                continue;
            }
            BlockPos from = squadPosition(level, m, s);
            // keep current objective if it is still worth holding (not yet captured, or under attack)
            if (s.objective >= 0 && s.objective < m.flags.size()) {
                Objective cur = m.flags.get(s.objective);
                boolean stillValid = cur.owner != s.faction || cur.enemiesOf(s.faction) > 0;
                if (stillValid && ThreadLocalRandom.current().nextFloat() < 0.7f) continue;
            }
            s.objective = choose(m, s.faction, from, s.id);
        }
    }

    private static BlockPos squadPosition(ServerLevel level, ConquestMatch m, ConquestMatch.Squad s) {
        AABB area = new AABB(m.minX - 48, level.getMinBuildHeight(), m.minZ - 48, m.maxX + 48, level.getMaxBuildHeight(), m.maxZ + 48);
        for (SoldierEntity e : level.getEntitiesOfClass(SoldierEntity.class, area, e -> e.getSquadId() == s.id && e.isAlive())) {
            return e.blockPosition();
        }
        return m.hq(s.faction);
    }

    private Commander() {}
}
