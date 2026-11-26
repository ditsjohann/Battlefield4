package com.ditsjohann.warfront.combat;

import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.Scoring;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Near misses suppress: AI soldiers lose accuracy and seek cover, players get a screen effect
 * and wider spread. Whoever suppressed a target shortly before a teammate kills it earns a
 * suppression assist.
 */
public final class Suppression {
    private record Mark(UUID by, long time) {}

    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    public static void apply(LivingEntity target, @Nullable Entity shooter, float amount, Vec3 where) {
        if (target instanceof SoldierEntity s) s.suppress(amount, shooter);
        if (target instanceof ServerPlayer p) WarfrontNet.toPlayer(p, new SimplePackets.Suppressed(amount, where));
        if (shooter != null) MARKS.put(target.getUUID(), new Mark(shooter.getUUID(), target.level().getGameTime()));
    }

    static void onKilled(ServerLevel level, LivingEntity victim, @Nullable Entity killer) {
        Mark m = MARKS.remove(victim.getUUID());
        if (m == null || level.getGameTime() - m.time > 100) return;
        if (killer != null && m.by.equals(killer.getUUID())) return;
        if (level.getEntity(m.by) instanceof ServerPlayer p && !Teams.allies(p, victim)) Scoring.award(p, 25, "SUPPRESSION ASSIST");
    }

    private Suppression() {}
}
